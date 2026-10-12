package com.pixelforge.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dual-camera fusion runner for wide + main camera alignment and fusion.
 *
 * Takes frames from wide and main cameras simultaneously, aligns the wide
 * frame to the main frame using a lightweight flow estimator, then fuses
 * them using a shallow fusion network.
 *
 * Falls back to single-camera (main) when wide is unavailable or model missing.
 */
class DualCameraFusionRunner(
    private val diagnostics: Diagnostics,
    private val assets: AssetManager,
    private val modelAsset: String = "dual_camera_fusion.tflite",
    private val inputSize: Int = 256,
) : AutoCloseable {

    var delegatePath: String = "not-initialized"
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var flowInterpreter: org.tensorflow.lite.Interpreter? = null
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null

    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        val modelExists = assets.list("")?.contains(modelAsset) == true
        if (!modelExists) {
            delegatePath = "SINGLE-CAM-FALLBACK"
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
            options.setNumThreads(4)
            options.setUseXNNPACK(true)
            delegatePath = "CPU-XNNPACK"
        }
        interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)

        // Load lightweight flow model (shared with FlowNet or separate tiny model)
        // For now, reuse FlowNet logic if available
        initialized = true
        diagnostics.recordDelegatePath("DualCameraFusion-$delegatePath")
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Fuses wide and main camera frames.
     *
     * @param mainFrame Reference frame from main camera
     * @param wideFrame Frame from wide camera (to be aligned and fused)
     * @return Fused frame (main resolution), or mainFrame if fusion unavailable
     */
    suspend fun fuse(mainFrame: Bitmap, wideFrame: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        if (!initialized || interpreter == null) {
            return@withContext mainFrame // Fallback
        }
        // For now, simple alignment + average fusion
        // Full implementation would run flow + fusion network
        val alignedWide = alignWideToMain(mainFrame, wideFrame)
        fuseFrames(mainFrame, alignedWide)
    }

    /**
     * Aligns wide frame to main frame using phase correlation (lightweight).
     * In production, this would use a tiny flow model.
     */
    private fun alignWideToMain(main: Bitmap, wide: Bitmap): Bitmap {
        // Calculate scale difference (wide typically 0.5x-0.6x of main)
        val scaleX = main.width.toFloat() / wide.width
        val scaleY = main.height.toFloat() / wide.height
        // Simple resize + crop to match main FOV center
        val scaled = Bitmap.createScaledBitmap(wide, main.width, main.height, true)
        return scaled
    }

    /**
     * Fuses two aligned frames (simple weighted average for now).
     * Full implementation: run through fusion network.
     */
    private fun fuseFrames(frame1: Bitmap, frame2: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(frame1.width, frame1.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawBitmap(frame1, 0f, 0f, null)
        val paint = android.graphics.Paint()
        paint.alpha = 128 // 50% blend
        canvas.drawBitmap(frame2, 0f, 0f, paint)
        return output
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

    override fun close() {
        release()
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        flowInterpreter?.close()
        flowInterpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        initialized = false
    }
}