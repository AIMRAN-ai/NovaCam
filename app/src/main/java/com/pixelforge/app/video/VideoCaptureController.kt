package com.pixelforge.app.video

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.io.MediaStoreSaver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX VideoCapture wrapper. Records at the device's max
 * stable resolution (1080p/30 class on Helio G85) and saves
 * the original clip to MediaStore immediately on stop, so the
 * shutter-to-saved budget holds even before enhancement runs.
 */
class VideoCaptureController(
    private val context: Context,
    private val diagnostics: Diagnostics,
    private val saver: MediaStoreSaver,
) {

    private val recorderExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var recorder: Recorder? = null
    private var recording: Recording? = null

    val isRecording: Boolean get() = recording != null

    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        val provider = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }

        recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HIGHEST)
            )
            .build()

        val videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder!!)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                videoCapture
            )
        } catch (e: Exception) {
            diagnostics.recordVideoError("bind-failed: ${e.message}")
        }
    }

    fun unbind() {
        stopRecording()
    }

    /**
     * Starts recording to a MediaStore-backed output. The file
     * is committed by MediaStore on stop; returns immediately.
     */
    fun startRecording(
        lifecycleOwner: LifecycleOwner,
        onResult: (Result<Uri>) -> Unit,
    ) {
        val recorder = recorder ?: run {
            onResult(Result.failure(IllegalStateException("VideoCapture not bound")))
            return
        }

        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, generateFileName())
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                "${android.os.Environment.DIRECTORY_DCIM}/PixelForge"
            )
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        recording = recorder.prepareRecording(context, outputOptions)
            .start(recorderExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        val uri = event.outputResults.outputUri
                        if (event.hasError()) {
                            diagnostics.recordVideoError(
                                "finalize-error: ${event.error}"
                            )
                            onResult(
                                Result.failure(
                                    VideoCaptureException(
                                        "Recording failed: ${event.error}",
                                        event.cause
                                    )
                                )
                            )
                        } else {
                            onResult(Result.success(uri))
                        }
                    }
                    else -> {
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun shutdown() {
        stopRecording()
        recorderExecutor.shutdown()
    }

    private fun generateFileName(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
            .format(java.util.Date())
        return "pixelforge_video_$timestamp.mp4"
    }

    companion object {
        const val TARGET_WIDTH = 1920
        const val TARGET_HEIGHT = 1080
        const val TARGET_FPS = 30
    }
}

class VideoCaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
