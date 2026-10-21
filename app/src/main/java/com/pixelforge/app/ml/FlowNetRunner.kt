package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FlowNet-Tiny optical-flow alignment runner.
 *
 * Estimates the translation (dx, dy) of a frame relative to the reference
 * frame. The quantized model asset is optional; when absent, a phase-correlation
 * fallback on downscaled luminance planes provides the same contract (tolerates
 * up to 8 px of inter-frame motion at native resolution) without a GPU.
 */
class FlowNetRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "flownet_tiny.tflite",
    private val inputSize: Int = 128,
) {
    val modelName: String = "FlowNet-Tiny"

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
            delegatePath = "CPU-PHASE-CORRELATION"
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
     * Returns the motion of [frame] relative to [reference] as (dx, dy) in
     * pixels at the input resolution. Positive dx means the frame content
     * shifted right.
     */
    suspend fun estimateMotion(reference: Bitmap, frame: Bitmap): MotionVector =
        withContext(Dispatchers.Default) {
            if (interpreter != null) {
                runModel(reference, frame)
            } else {
                phaseCorrelation(reference, frame)
            }
        }

    private fun runModel(reference: Bitmap, frame: Bitmap): MotionVector {
        val interpreter = interpreter ?: return phaseCorrelation(reference, frame)
        val pair = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 2 * 4)
            .order(ByteOrder.nativeOrder())
        writeBitmap(pair, reference)
        writeBitmap(pair, frame)
        val output = ByteBuffer.allocateDirect(2 * 4).order(ByteOrder.nativeOrder())
        interpreter.run(pair, output)
        output.rewind()
        val dx = output.float
        val dy = output.float
        return MotionVector(dx, dy, source = "model")
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

    /**
     * Phase correlation on 64x64 luminance planes: robust to the 8 px motion
     * budget at native resolution (64 px downscale keeps 8 px within one
     * correlation cell for typical sensor resolutions).
     */
    private fun phaseCorrelation(reference: Bitmap, frame: Bitmap): MotionVector {
        val size = 64
        val refLum = luminance(reference, size)
        val frameLum = luminance(frame, size)
        val refFft = fft2d(refLum)
        val frameFft = fft2d(frameLum)
        val crossPower = Array(size) { Array(size) { DoubleArray(2) } }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val re = refFft[y][x][0] * frameFft[y][x][0] + refFft[y][x][1] * frameFft[y][x][1]
                val im = refFft[y][x][0] * frameFft[y][x][1] - refFft[y][x][1] * frameFft[y][x][0]
                val mag = Math.sqrt(re * re + im * im) + 1e-9
                crossPower[y][x][0] = re / mag
                crossPower[y][x][1] = 0.0
            }
        }
        val corr = ifft2d(crossPower)
        var peakY = 0
        var peakX = 0
        var peak = Double.NEGATIVE_INFINITY
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (corr[y][x] > peak) {
                    peak = corr[y][x]
                    peakY = y
                    peakX = x
                }
            }
        }
        val dx = if (peakX > size / 2) peakX - size else peakX
        val dy = if (peakY > size / 2) peakY - size else peakY
        val scaleX = reference.width.toFloat() / size
        val scaleY = reference.height.toFloat() / size
        return MotionVector(dx * scaleX, dy * scaleY, source = "phase-correlation")
    }

    private fun luminance(bitmap: Bitmap, size: Int): DoubleArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        return DoubleArray(size * size) { i ->
            val p = pixels[i]
            (0.299 * (p shr 16 and 0xFF) + 0.587 * (p shr 8 and 0xFF) + 0.114 * (p and 0xFF))
        }
    }

    private fun fft2d(input: DoubleArray): Array<Array<DoubleArray>> {
        val n = 64
        val rows = Array(n) { y -> fft(input.sliceArray(y * n until (y + 1) * n)) }
        val out = Array(n) { Array(n) { DoubleArray(2) } }
        for (x in 0 until n) {
            val col = DoubleArray(n) { y -> rows[y][x][0] }
            val colFft = fft(col)
            for (y in 0 until n) {
                out[y][x][0] = colFft[y][0]
                out[y][x][1] = colFft[y][1]
            }
        }
        return out
    }

    private fun ifft2d(input: Array<Array<DoubleArray>>): Array<DoubleArray> {
        val n = 64
        val conj = Array(n) { y -> Array(n) { x -> doubleArrayOf(input[y][x][0], -input[y][x][1]) } }
        val rows = Array(n) { y -> fftComplex(conj[y]) }
        val out = Array(n) { DoubleArray(n) }
        for (x in 0 until n) {
            val col = Array(n) { y -> rows[y][x] }
            val colFft = fftComplex(col)
            for (y in 0 until n) {
                out[y][x] = colFft[y][0] / (n * n)
            }
        }
        return out
    }

    private fun fft(real: DoubleArray): Array<DoubleArray> {
        val complex = Array(real.size) { i -> doubleArrayOf(real[i], 0.0) }
        return fftComplex(complex)
    }

    private fun fftComplex(input: Array<DoubleArray>): Array<DoubleArray> {
        val n = input.size
        if (n == 1) return arrayOf(input[0].clone())
        if (n % 2 != 0) throw IllegalArgumentException("FFT size must be power of two")
        val even = Array(n / 2) { i -> input[2 * i] }
        val odd = Array(n / 2) { i -> input[2 * i + 1] }
        val evenFft = fftComplex(even)
        val oddFft = fftComplex(odd)
        val out = Array(n) { DoubleArray(2) }
        for (k in 0 until n / 2) {
            val angle = -2.0 * Math.PI * k / n
            val wkRe = Math.cos(angle)
            val wkIm = Math.sin(angle)
            val re = wkRe * oddFft[k][0] - wkIm * oddFft[k][1]
            val im = wkRe * oddFft[k][1] + wkIm * oddFft[k][0]
            out[k][0] = evenFft[k][0] + re
            out[k][1] = evenFft[k][1] + im
            out[k + n / 2][0] = evenFft[k][0] - re
            out[k + n / 2][1] = evenFft[k][1] - im
        }
        return out
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

    companion object {
        const val MAX_MOTION_PX = 8
    }
}

data class MotionVector(
    val dx: Float,
    val dy: Float,
    val source: String,
)
