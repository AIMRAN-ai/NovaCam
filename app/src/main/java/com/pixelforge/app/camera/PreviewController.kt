package com.pixelforge.app.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PreviewController(
    private val context: Context,
    private val diagnostics: Diagnostics,
    private val appColdStartElapsedRealtime: () -> Long,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var firstFrameRecorded = false

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer?,
    ) {
        val provider = cameraProvider ?: initProvider()
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analyzer?.let { analysis.setAnalyzer(analysisExecutor, wrapAnalyzer(it)) }

        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }

    suspend fun unbind() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        analysisExecutor.shutdown()
    }

    private suspend fun initProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                continuation.resume(provider)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun wrapAnalyzer(base: ImageAnalysis.Analyzer): ImageAnalysis.Analyzer =
        ImageAnalysis.Analyzer { imageProxy ->
            if (!firstFrameRecorded) {
                firstFrameRecorded = true
                diagnostics.recordColdStart(
                    android.os.SystemClock.elapsedRealtime() - appColdStartElapsedRealtime()
                )
            }
            base.analyze(imageProxy)
        }
}
