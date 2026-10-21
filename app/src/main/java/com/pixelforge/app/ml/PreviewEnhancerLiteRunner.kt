package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight preview enhancer for real-time 60fps enhancement.
 *
 * Uses a tiny MobileNetV3-style model (128x128 input, int8 quantized)
 * with pixel shuffle upsampling for 2x enhancement.
 * Designed for <16ms/frame on Mali-G52.
 *
 * Falls back to identity when model asset is absent.
 */
class PreviewEnhancerLiteRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "preview_enhancer_lite.tflite",
    private val inputSize: Int = 128,
) : AutoCloseable {

    var delegatePath: String = "not-initialized"
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null

    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        val modelExists = assets.list("")?.contains(modelAsset) == true
        if (!modelExists) {
            delegatePath = "IDENTITY-FALLBACK"
            diagnostics.recordDelegatePath(delegatePath)
            initialized = true
            return
        }
        val modelBuffer = loadModelFile()
        val options = org.tensorflow.lite.Interpreter.Options()
        val gpu = tryCreateGpu()
        if (gpu != null) {
            gpuDelegate = gpu
            options.addDelegate(gpu)
            delegatePath = "GPU"
        } else {
            options.setNumThreads(2)
            options.setUseXNNPACK(true)
            delegatePath = "CPU-XNNPACK"
        }
        interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)
        initialized = true
        diagnostics.recordDelegatePath("PreviewEnhancerLite-$delegatePath")
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Enhances a bitmap (2x upscale with detail enhancement).
     * Runs on dispatcher; caller should throttle to target FPS.
     */
    suspend fun enhance(input: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val interpreter = interpreter
        if (interpreter == null || !initialized) {
            return@withContext input // Identity fallback
        }
        val width = input.width
        val height = input.height
        val outWidth = width * 2
        val outHeight = height * 2
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        // Process in 128x128 tiles for speed (no overlap for real-time)
        val tileSize = inputSize
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val tileW = minOf(tileSize, width - x)
                val tileH = minOf(tileSize, height - y)
                val tile = Bitmap.createBitmap(input, x, y, tileW, tileH)
                val enhancedTile = runInference(interpreter, tile)
                val dstX = x * 2
                val dstY = y * 2
                val dstW = tileW * 2
                val dstH = tileH * 2
                blendTile(output, enhancedTile, dstX, dstY, dstW, dstH)
                tile.recycle()
                enhancedTile.recycle()
                x += tileSize
            }
            y += tileSize
        }
        output
    }

    private fun runInference(
        interpreter: org.tensorflow.lite.Interpreter,
        tile: Bitmap,
    ): Bitmap {
        val inputBuffer = bitmapToBuffer(tile)
        val outputBuffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * 4
        ).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        return bufferToBitmap(outputBuffer, tile.width * 2, tile.height * 2)
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val scaled = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        }
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            // Normalize to [0, 1] for model
            buffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel and 0xFF) / 255.0f))
        }
        return buffer
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val count = width.coerceAtMost(inputSize * 2) * height.coerceAtMost(inputSize * 2)
        val pixels = IntArray(count)
        for (i in pixels.indices) {
            val r = (buffer.float * 255).toInt().coerceIn(0, 255)
            val g = (buffer.float * 255).toInt().coerceIn(0, 255)
            val b = (buffer.float * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val base = Bitmap.createBitmap(pixels, width.coerceAtMost(inputSize * 2), height.coerceAtMost(inputSize * 2), Bitmap.Config.ARGB_8888)
        return if (base.width == width && base.height == height) base
        else Bitmap.createScaledBitmap(base, width, height, true)
    }

    private fun blendTile(
        output: Bitmap,
        tile: Bitmap,
        dstX: Int,
        dstY: Int,
        dstW: Int,
        dstH: Int,
    ) {
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(tile, dstX.toFloat(), dstY.toFloat(), paint)
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

    override fun close() {
        release()
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        initialized = false
    }

    companion object {
        const val MAX_WIDTH = 512
        const val PREVIEW_FPS_CAP = 60
    }
}