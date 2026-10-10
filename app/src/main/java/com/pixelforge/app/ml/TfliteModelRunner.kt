package com.pixelforge.app.ml

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import com.pixelforge.app.diagnostics.Diagnostics
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteModelRunner(
    private val diagnostics: Diagnostics,
    private val modelAsset: String,
    private val assets: AssetManager,
) : ModelRunner {

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null

    override var delegatePath: String = "not-initialized"
        private set

    override val inputSize: Int = 256

    override suspend fun initialize() {
        val modelBuffer = loadModelFile(modelAsset)
        val options = Interpreter.Options()

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
        interpreter = Interpreter(modelBuffer, options)
        diagnostics.recordDelegatePath(delegatePath)
    }

    override fun enhance(input: Bitmap): Bitmap {
        val interpreter = interpreter ?: throw IllegalStateException("Model not initialized")
        val inputBuffer = bitmapToBuffer(input)
        val outputBuffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * 4
        ).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        return bufferToBitmap(outputBuffer, inputSize, inputSize)
    }

    override fun release() {
        interpreter?.close()
        interpreter = null
        nnApiDelegate?.close()
        nnApiDelegate = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    private fun tryCreateNnApi(): NnApiDelegate? =
        try {
            NnApiDelegate()
        } catch (e: Exception) {
            null
        }

    private fun tryCreateGpu(): GpuDelegate? =
        try {
            GpuDelegate()
        } catch (e: Exception) {
            null
        }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val descriptor: AssetFileDescriptor = assets.openFd(assetName)
        val inputStream = FileInputStream(descriptor.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength
        )
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel and 0xFF) / 255.0f))
        }
        return buffer
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (buffer.float * 255).toInt().coerceIn(0, 255)
            val g = (buffer.float * 255).toInt().coerceIn(0, 255)
            val b = (buffer.float * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
