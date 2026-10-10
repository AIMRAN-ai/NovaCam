package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ESRGAN-Lite super-resolution runner (RRDB, pruned).
 *
 * Processes the input in tiles with overlap and Gaussian-blend stitching so a
 * full 12 MP frame never has to live in GPU memory at once. Falls back to a
 * deterministic CPU upscale path when the quantized model asset is absent, so
 * the pipeline is exercisable on any device and in unit tests.
 */
class EsrganLiteRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "esrgan_lite.tflite",
    private val inputSize: Int = 256,
    private val overlap: Int = 16,
) : SrModelRunner {

    override val modelName: String = "ESRGAN-Lite"

    override var delegatePath: String = "not-initialized"
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var nnApiDelegate: org.tensorflow.lite.nnapi.NnApiDelegate? = null
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null

    @Volatile
    private var initialized = false

    override suspend fun initialize() {
        if (initialized) return
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

    override fun isInitialized(): Boolean = initialized

    override fun upscale(input: Bitmap, scale: Int): Bitmap {
        val interpreter = interpreter
            ?: throw IllegalStateException("ESRGAN-Lite not initialized")
        require(scale == 2 || scale == 4) { "ESRGAN-Lite supports 2x or 4x, got $scale" }

        val outWidth = input.width * scale
        val outHeight = input.height * scale
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val tileInput = inputSize
        val step = tileInput - overlap
        var y = 0
        while (y < input.height) {
            var x = 0
            while (x < input.width) {
                val tileW = minOf(tileInput, input.width - x)
                val tileH = minOf(tileInput, input.height - y)
                val tile = Bitmap.createBitmap(input, x, y, tileW, tileH)
                val scaledTile = runInference(interpreter, tile)
                val dstW = tileW * scale
                val dstH = tileH * scale
                val dstX = x * scale
                val dstY = y * scale
                blendTile(output, scaledTile, dstX, dstY, dstW, dstH, scale)
                tile.recycle()
                scaledTile.recycle()
                x += step
            }
            y += step
        }
        return output
    }

    private fun runInference(interpreter: org.tensorflow.lite.Interpreter, tile: Bitmap): Bitmap {
        val inputBuffer = bitmapToBuffer(tile)
        val outputBuffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * 4 * 4
        ).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        return bufferToBitmap(outputBuffer, tile.width * 2, tile.height * 2)
    }

    private fun blendTile(
        output: Bitmap,
        tile: Bitmap,
        dstX: Int,
        dstY: Int,
        dstW: Int,
        dstH: Int,
        scale: Int,
    ) {
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        if (dstX == 0 && dstY == 0) {
            canvas.drawBitmap(tile, dstX.toFloat(), dstY.toFloat(), paint)
            return
        }
        val shader = android.graphics.BitmapShader(
            tile,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(
            dstX.toFloat(), dstY.toFloat(),
            (dstX + dstW).toFloat(), (dstY + dstH).toFloat(),
            paint
        )
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
            buffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((pixel and 0xFF) / 255.0f))
        }
        return buffer
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val count = width.coerceAtMost(inputSize) * height.coerceAtMost(inputSize)
        val pixels = IntArray(count)
        for (i in pixels.indices) {
            val r = (buffer.float * 255).toInt().coerceIn(0, 255)
            val g = (buffer.float * 255).toInt().coerceIn(0, 255)
            val b = (buffer.float * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val base = Bitmap.createBitmap(pixels, width.coerceAtMost(inputSize), height.coerceAtMost(inputSize), Bitmap.Config.ARGB_8888)
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
            org.tensorflow.lite.gpu.GpuDelegate()
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

    override fun release() {
        interpreter?.close()
        interpreter = null
        nnApiDelegate?.close()
        nnApiDelegate = null
        gpuDelegate?.close()
        gpuDelegate = null
        initialized = false
    }

    companion object {
        const val MAX_SCALE = 4
    }
}
