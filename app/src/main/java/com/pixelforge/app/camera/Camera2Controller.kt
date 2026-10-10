package com.pixelforge.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Camera2Controller(
    private val context: Context,
    private val diagnostics: Diagnostics,
) {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)

    private val captureThread = HandlerThread("PixelForge-Capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var characteristics: CameraCharacteristics? = null

    val isOpen: Boolean get() = cameraDevice != null

    @SuppressLint("MissingPermission")
    suspend fun openCamera(cameraId: String) {
        characteristics = manager.getCameraCharacteristics(cameraId)
        var lastError: Exception? = null
        repeat(OPEN_CAMERA_RETRIES) { attempt ->
            try {
                openCameraOnce(cameraId)
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < OPEN_CAMERA_RETRIES - 1) {
                    withContext(Dispatchers.IO) { delay(OPEN_CAMERA_RETRY_DELAY_MS) }
                }
            }
        }
        throw lastError ?: CameraException("Failed to open camera")
    }

    private suspend fun openCameraOnce(cameraId: String) = suspendCancellableCoroutine { continuation ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                continuation.resume(Unit)
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice = null
                if (continuation.isActive) {
                    continuation.resumeWithException(CameraException("Camera disconnected"))
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice = null
                if (continuation.isActive) {
                    continuation.resumeWithException(CameraException("Camera error $error"))
                }
            }
        }, captureHandler)
    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
    }

    fun shutdown() {
        closeCamera()
        captureThread.quitSafely()
    }

    fun largestOutputSize(format: Int): Size {
        val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        return map?.getOutputSizes(format)?.maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: Size(1920, 1080)
    }

    fun largestOutputSizeWithinBudget(format: Int, maxPixels: Long): Size {
        val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(format) ?: return Size(1920, 1080)
        if (sizes.isEmpty()) return Size(1920, 1080)
        val withinBudget = sizes.filter { it.width.toLong() * it.height.toLong() <= maxPixels }
        return if (withinBudget.isNotEmpty()) {
            withinBudget.maxByOrNull { it.width.toLong() * it.height.toLong() }!!
        } else {
            sizes.minByOrNull { it.width.toLong() * it.height.toLong() }!!
        }
    }

    fun targetFpsRange(preferred: Int): Range<Int> {
        val ranges = characteristics
            ?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: emptyArray()
        return ranges
            .filter { it.lower <= preferred && it.upper >= preferred }
            .maxByOrNull { it.lower }
            ?: ranges.maxByOrNull { it.upper }
            ?: Range(15, 30)
    }

    private suspend fun openSession(
        device: CameraDevice,
        reader: ImageReader,
    ): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
        device.createCaptureSession(
            listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    continuation.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(CameraException("Capture session configuration failed"))
                    }
                }
            },
            captureHandler
        )
    }

    private suspend fun captureOnce(
        session: CameraCaptureSession,
        request: CaptureRequest,
    ): TotalCaptureResult = suspendCancellableCoroutine { continuation ->
        session.capture(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                continuation.resume(result)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure,
            ) {
                if (continuation.isActive) {
                    continuation.resumeWithException(CameraException("Capture failed: ${failure.reason}"))
                }
            }
        }, captureHandler)
    }

    suspend fun captureRawDng(onDng: (ByteArray) -> Unit) {
        val device = cameraDevice ?: throw CameraException("Camera is not open")
        val chars = characteristics ?: throw CameraException("Camera characteristics unavailable")
        val size = largestOutputSize(ImageFormat.RAW_SENSOR)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 1)
        try {
            val session = openSession(device, reader)
            val imageDeferred = kotlinx.coroutines.CompletableDeferred<Image>()
            reader.setOnImageAvailableListener({ readerRef ->
                val image = readerRef.acquireNextImage()
                if (image != null && !imageDeferred.isCompleted) {
                    imageDeferred.complete(image)
                } else {
                    image?.close()
                }
            }, captureHandler)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange(30))
            }.build()
            val result = captureOnce(session, request)
            val image = imageDeferred.await()
            val dngCreator = DngCreator(chars, result)
            val bytes = ByteArrayOutputStream().use { stream ->
                dngCreator.writeImage(stream, image)
                stream.toByteArray()
            }
            image.close()
            onDng(bytes)
        } finally {
            reader.close()
        }
    }

    suspend fun captureBurst(
        format: Int,
        frameCount: Int,
        targetFps: Int,
        pixelBudget: Long = DEFAULT_BURST_PIXEL_BUDGET,
        onFrame: (Image, Int) -> Unit,
    ): BurstStats {
        val device = cameraDevice ?: throw CameraException("Camera is not open")
        val size = largestOutputSizeWithinBudget(format, pixelBudget)
        val reader = ImageReader.newInstance(size.width, size.height, format, frameCount + BURST_BUFFER_SLACK)
        try {
            val session = openSession(device, reader)
            val requests = List(frameCount) {
                device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange(targetFps))
                }.build()
            }
            return runBurst(session, requests, reader, frameCount, targetFps, onFrame)
        } finally {
            reader.close()
        }
    }

    private suspend fun runBurst(
        session: CameraCaptureSession,
        requests: List<CaptureRequest>,
        reader: ImageReader,
        frameCount: Int,
        targetFps: Int,
        onFrame: (Image, Int) -> Unit,
    ): BurstStats = suspendCancellableCoroutine { continuation ->
        val received = AtomicInteger(0)
        val resolved = AtomicInteger(0)
        val expected = AtomicInteger(frameCount)
        val timestamps = LongArray(frameCount)
        val finished = AtomicBoolean(false)
        val expectedIntervalNs = 1_000_000_000L / targetFps

        val timeoutRunnable = Runnable {
            if (finished.compareAndSet(false, true)) {
                continuation.resumeWithException(
                    CameraException("Burst timed out after ${received.get()} of $frameCount frames")
                )
            }
        }
        captureHandler.postDelayed(timeoutRunnable, BURST_TIMEOUT_MS)

        fun maybeFinish(): BurstStats? {
            if (resolved.get() < frameCount || received.get() < expected.get()) return null
            if (!finished.compareAndSet(false, true)) return null
            captureHandler.removeCallbacks(timeoutRunnable)
            val count = received.get().coerceAtMost(frameCount)
            val sorted = timestamps.take(count).filter { it > 0L }.sorted()
            val durationNs = if (sorted.size >= 2) sorted.last() - sorted.first() else 0L
            val fps = if (durationNs > 0L) (sorted.size - 1) * 1_000_000_000.0 / durationNs else 0.0
            var dropped = 0
            for (i in 1 until sorted.size) {
                if (sorted[i] - sorted[i - 1] > expectedIntervalNs * DROPPED_FRAME_TOLERANCE) dropped++
            }
            return BurstStats(
                framesCaptured = count,
                framesRequested = frameCount,
                achievedFps = fps,
                droppedFrames = dropped,
                durationMs = durationNs / 1_000_000L,
            )
        }

        reader.setOnImageAvailableListener({ readerRef ->
            val image = readerRef.acquireNextImage() ?: return@setOnImageAvailableListener
            val index = received.getAndIncrement()
            if (index < frameCount) {
                timestamps[index] = image.timestamp
                onFrame(image, index)
            } else {
                image.close()
            }
            val stats = maybeFinish()
            if (stats != null) continuation.resume(stats)
        }, captureHandler)

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                resolved.incrementAndGet()
                val stats = maybeFinish()
                if (stats != null) continuation.resume(stats)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure,
            ) {
                resolved.incrementAndGet()
                expected.decrementAndGet()
                val stats = maybeFinish()
                if (stats != null) continuation.resume(stats)
            }

            override fun onCaptureSequenceAborted(
                session: CameraCaptureSession,
                sequenceId: Int,
            ) {
                if (finished.compareAndSet(false, true)) {
                    captureHandler.removeCallbacks(timeoutRunnable)
                    continuation.resumeWithException(CameraException("Burst sequence aborted"))
                }
            }
        }

        try {
            session.captureBurst(requests, captureCallback, captureHandler)
        } catch (e: Exception) {
            if (finished.compareAndSet(false, true)) {
                captureHandler.removeCallbacks(timeoutRunnable)
                continuation.resumeWithException(e)
            }
        }
    }

    companion object {
        private const val OPEN_CAMERA_RETRIES = 3
        private const val OPEN_CAMERA_RETRY_DELAY_MS = 250L
        private const val BURST_TIMEOUT_MS = 10_000L
        private const val BURST_BUFFER_SLACK = 2
        private const val DROPPED_FRAME_TOLERANCE = 1.5
        const val DEFAULT_BURST_PIXEL_BUDGET = 12_000_000L
    }
}
