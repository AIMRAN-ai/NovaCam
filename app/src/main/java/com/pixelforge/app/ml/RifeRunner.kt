package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RIFE (Real-Time Intermediate Flow Estimation) frame interpolation runner.
 *
 * Uses IFNet architecture (rife_v4.6.tflite) to generate intermediate frames
 * between two input frames. Supports 2x (1 intermediate) and 4x (3 intermediates
 * via two-pass) interpolation factors.
 *
 * Processes frames in tiles with overlap to stay within memory budget on
 * budget devices (Helio G85 / Mali-G52). Falls back to CPU when model asset
 * is absent so pipeline remains exercisable in tests.
 */
class RifeRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "rife_v4.6.tflite",
    private val tileSize: Int = 256,
    private val overlap: Int = 16,
) : AutoCloseable {

    var delegatePath: String = "not-initialized"
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var nnApiDelegate: org.tensorflow.lite.nnapi.NnApiDelegate? = null
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null

    @Volatile
    private var initialized = false

    suspend fun initialize() {
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
        diagnostics.recordDelegatePath("RIFE-$delegatePath")
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Generates intermediate frames between frame0 and frame1.
     * scale = 2 → returns 1 intermediate frame (frame0.5)
     * scale = 4 → returns 3 intermediate frames (frame0.25, frame0.5, frame0.75) via two passes
     */
    suspend fun interpolate(frame0: Bitmap, frame1: Bitmap, scale: Int): List<Bitmap> {
        require(scale == 2 || scale == 4) { "RIFE supports 2x or 4x, got $scale" }
        val interpreter = interpreter
            ?: throw IllegalStateException("RIFE not initialized")

        return withContext(Dispatchers.Default) {
            if (scale == 2) {
                listOf(interpolateOnce(interpreter, frame0, frame1))
            } else {
                // 4x: two-pass interpolation
                // Pass 1: frame0 + frame1 → mid (frame0.5)
                val mid = interpolateOnce(interpreter, frame0, frame1)
                // Pass 2: frame0 + mid → quarter (frame0.25)
                val quarter = interpolateOnce(interpreter, frame0, mid)
                // Pass 3: mid + frame1 → three-quarter (frame0.75)
                val threeQuarter = interpolateOnce(interpreter, mid, frame1)
                listOf(quarter, mid, threeQuarter)
            }
        }
    }

    private fun interpolateOnce(
        interpreter: org.tensorflow.lite.Interpreter,
        frame0: Bitmap,
        frame1: Bitmap,
    ): Bitmap {
        val width = frame0.width
        val height = frame0.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val step = tileSize - overlap
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val tileW = minOf(tileSize, width - x)
                val tileH = minOf(tileSize, height - y)
                val tile0 = Bitmap.createBitmap(frame0, x, y, tileW, tileH)
                val tile1 = Bitmap.createBitmap(frame1, x, y, tileW, tileH)
                val interpolatedTile = runInference(interpreter, tile0, tile1)
                val dstX = x
                val dstY = y
                blendTile(output, interpolatedTile, dstX, dstY, tileW, tileH)
                tile0.recycle()
                tile1.recycle()
                interpolatedTile.recycle()
                x += step
            }
            y += step
        }
        return output
    }

    private fun runInference(
        interpreter: org.tensorflow.lite.Interpreter,
        tile0: Bitmap,
        tile1: Bitmap,
    ): Bitmap {
        val inputBuffer = tilePairToBuffer(tile0, tile1)
        val outputBuffer = ByteBuffer.allocateDirect(
            tileSize * tileSize * 3 * 4
        ).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        return bufferToBitmap(outputBuffer, tile0.width, tile1.height)
    }

    private fun tilePairToBuffer(tile0: Bitmap, tile1: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(tileSize * tileSize * 6 * 4)
            .order(ByteOrder.nativeOrder())
        val scaled0 = if (tile0.width == tileSize && tile0.height == tileSize) tile0
        else Bitmap.createScaledBitmap(tile0, tileSize, tileSize, true)
        val scaled1 = if (tile1.width == tileSize && tile1.height == tileSize) tile1
        else Bitmap.createScaledBitmap(tile1, tileSize, tileSize, true)

        val pixels0 = IntArray(tileSize * tileSize)
        val pixels1 = IntArray(tileSize * tileSize)
        scaled0.getPixels(pixels0, 0, tileSize, 0, 0, tileSize, tileSize)
        scaled1.getPixels(pixels1, 0, tileSize, 0, 0, tileSize, tileSize)

        // RIFE expects 6 channels: RGB0 + RGB1, normalized to [-1, 1]
        for (i in 0 until (tileSize * tileSize)) {
            val p0 = pixels0[i]
            val p1 = pixels1[i]
            buffer.putFloat(((p0 shr 16 and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((p0 shr 8 and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((p0 and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((p1 shr 16 and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((p1 shr 8 and 0xFF) / 127.5f) - 1f)
            buffer.putFloat(((p1 and 0xFF) / 127.5f) - 1f)
        }
        return buffer
    }

    private fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val count = width.coerceAtMost(tileSize) * height.coerceAtMost(tileSize)
        val pixels = IntArray(count)
        for (i in pixels.indices) {
            val r = ((buffer.float + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((buffer.float + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((buffer.float + 1f) * 127.5f).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val base = Bitmap.createBitmap(pixels, width.coerceAtMost(tileSize), height.coerceAtMost(tileSize), Bitmap.Config.ARGB_8888)
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

    override fun close() {
        release()
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

    companion object {
        const val MAX_SCALE = 4
    }
}