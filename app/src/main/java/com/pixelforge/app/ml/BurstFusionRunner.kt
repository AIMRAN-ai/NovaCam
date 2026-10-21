package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BurstFusion-Net multi-frame fusion runner (5-layer U-Net).
 *
 * Merges the reference frame with its aligned neighbours into a
 * single noise-reduced frame. When the quantized asset is absent,
 * falls back to an aligned multi-frame average (the classical
 * burst-denoising baseline) so the pipeline always produces output.
 */
class BurstFusionRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "burstfusion_net.tflite",
    private val inputSize: Int = 256,
    private val maxFrames: Int = 5,
) {
    val modelName: String = "BurstFusion-Net"

    var delegatePath: String = "not-initialized"
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var nnApiDelegate: org.tensorflow.lite.nnapi.NnApiDelegate? = null
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null

    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        if (!assets.list("")!!.contains(modelAsset)) {
            delegatePath = "CPU-AVERAGE-FUSION"
            diagnostics.recordDelegatePath(delegatePath)
            return
        }
        val modelBuffer = loadModelFile()
        val options = org.tensorflow.lite.Interpreter.Options()
        val nnApi = tryCreateNnApi()
        if (nnApi != null) {
            nnApiDelegate = nnApi
            options.addDelegate(nnApi)
            delegatePath = "NNAPI"
        } else {
            val gpu = tryCreateGpu()
            if (gpu != null) {
                gpuDelegate = gpu
                options.addDelegate(gpu)
                delegatePath = "GPU"
            } else {
                options.setNumThreads(4)
                options.setUseXNNPACK(true)
                delegatePath = "CPU-XNNPACK"
            }
        }
        interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)
        initialized = true
        diagnostics.recordDelegatePath(delegatePath)
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Fuses [alignedFrames] (reference first) into a single frame at
     * the reference frame's resolution.
     */
    suspend fun fuse(alignedFrames: List<Bitmap>): Bitmap =
        withContext(Dispatchers.Default) {
            if (interpreter != null && alignedFrames.size >= 2) {
                runModel(alignedFrames)
            } else {
                averageFusion(alignedFrames)
            }
        }

    private fun runModel(alignedFrames: List<Bitmap>): Bitmap {
        val interpreter = interpreter ?: return averageFusion(alignedFrames)
        val reference = alignedFrames[0]
        val frames = alignedFrames.take(maxFrames)
        val inputBuffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * frames.size * 4
        ).order(ByteOrder.nativeOrder())
        for (frame in frames) {
            writeBitmap(inputBuffer, frame)
        }
        val outputBuffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * 4
        ).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        return bufferToBitmap(outputBuffer, reference.width, reference.height)
    }

    /**
     * Aligned multi-frame average with per-pixel outlier rejection
     * (median of the temporal stack). Deterministic, no GPU needed.
     */
    private fun averageFusion(alignedFrames: List<Bitmap>): Bitmap {
        val reference = alignedFrames[0]
        val width = reference.width
        val height = reference.height
        val frames = alignedFrames.take(maxFrames)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stacks = Array(frames.size) { IntArray(width * height) }
        for ((index, frame) in frames.withIndex()) {
            val scaled = if (frame.width == width && frame.height == height) {
                frame
            } else {
                Bitmap.createScaledBitmap(frame, width, height, true)
            }
            scaled.getPixels(stacks[index], 0, width, 0, 0, width, height)
        }
        val outPixels = IntArray(width * height)
        val channelValues = IntArray(frames.size)
        for (i in outPixels.indices) {
            var r = 0
            var g = 0
            var b = 0
            for (c in 0 until 3) {
                for (f in frames.indices) {
                    val p = stacks[f][i]
                    channelValues[f] = when (c) {
                        0 -> p shr 16 and 0xFF
                        1 -> p shr 8 and 0xFF
                        else -> p and 0xFF
                    }
                }
                channelValues.sort(0, frames.size)
                val median = channelValues[frames.size / 2]
                when (c) {
                    0 -> r = median
                    1 -> g = median
                    else -> b = median
                }
            }
            outPixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun writeBitmap(buffer: ByteBuffer, bitmap: Bitmap) {
        val scaled = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        }
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel and 0xFF) / 255.0f))
        }
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val w = width.coerceAtMost(inputSize)
        val h = height.coerceAtMost(inputSize)
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val r = (buffer.float * 255).toInt().coerceIn(0, 255)
            val g = (buffer.float * 255).toInt().coerceIn(0, 255)
            val b = (buffer.float * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val base = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return if (base.width == width && base.height == height) base
        else Bitmap.createScaledBitmap(base, width, height, true)
    }

    private fun tryCreateNnApi(): org.tensorflow.lite.nnapi.NnApiDelegate? =
        try {
            org.tensorflow.lite.nnapi.NnApiDelegate()
        } catch (e: Exception) {
            null
        }

    private fun tryCreateGpu(): org.tensorflow.lite.gpu.GpuDelegate? =
        try {
            org.tensorflow.lite.gpu.GpuDelegate(
                org.tensorflow.lite.gpu.GpuDelegate.Options()
                    .setInferencePreference(org.tensorflow.lite.gpu.GpuDelegate.Options.INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER)
                    .setPrecisionLossAllowed(true)
            )
        } catch (e: Exception) {
            null
        }

    private fun loadModelFile(): java.nio.MappedByteBuffer {
        val descriptor = assets.openFd(modelAsset)
        val stream = java.io.FileInputStream(descriptor.fileDescriptor)
        return stream.channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength
        )
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        nnApiDelegate?.close()
        nnApiDelegate = null
        gpuDelegate?.close()
        gpuDelegate = null
        initialized = false
    }
}
