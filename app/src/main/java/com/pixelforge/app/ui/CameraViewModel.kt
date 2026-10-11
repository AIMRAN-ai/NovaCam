package com.pixelforge.app.ui

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelforge.app.PixelForgeApp
import com.pixelforge.app.camera.AspectRatio
import com.pixelforge.app.camera.BurstOrchestrator
import com.pixelforge.app.camera.BurstPipeline
import com.pixelforge.app.camera.BurstResult
import com.pixelforge.app.camera.Camera2Controller
import com.pixelforge.app.camera.CameraException
import com.pixelforge.app.camera.CameraHalProbe
import com.pixelforge.app.camera.HalCapabilities
import com.pixelforge.app.camera.PreviewController
import com.pixelforge.app.camera.QualityTier
import com.pixelforge.app.camera.ReferenceFrameSink
import com.pixelforge.app.diagnostics.BurstDiagnostics
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.io.MediaStoreSaver
import com.pixelforge.app.ml.PreviewEnhancer
import com.pixelforge.app.video.VideoCaptureController
import com.pixelforge.app.video.VideoEnhancementWorker
import com.pixelforge.app.video.VideoExportConfig
import com.pixelforge.app.video.VideoQualityTier
import com.pixelforge.app.video.AudioMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(
    private val app: PixelForgeApp,
) : ViewModel() {

    private val diagnostics: Diagnostics = app.diagnostics
    private val controller = Camera2Controller(app.applicationContext, diagnostics)
    private val halProbe = CameraHalProbe(app.applicationContext, diagnostics)
    private val orchestrator = BurstOrchestrator(controller, diagnostics)
    private val saver = MediaStoreSaver(app.applicationContext)
    private val previewController = PreviewController(
        context = app.applicationContext,
        diagnostics = diagnostics,
        appColdStartElapsedRealtime = { app.coldStartElapsedRealtime },
    )
    private val enhancer = PreviewEnhancer(app.modelRunner, diagnostics)
    private val videoCaptureController = VideoCaptureController(
        context = app.applicationContext,
        diagnostics = diagnostics,
        saver = saver,
    )

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val diagnosticsState = diagnostics.state

    private var halCapabilities: HalCapabilities? = null

    init {
        viewModelScope.launch {
            app.modelRunner.initialize()
            diagnostics.recordEnhancementState(true, false)
        }
    }

    fun attachPreviewView(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
    ) {
        viewModelScope.launch {
            try {
                previewController.bind(
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    analyzer = object : ImageAnalysis.Analyzer {
                        override fun analyze(imageProxy: ImageProxy) {
                            enhancer.recordAnalysis()
                            val bitmap: Bitmap? = imageProxy.toBitmap()
                            if (bitmap != null) {
                                viewModelScope.launch {
                                    enhancer.processFrame(bitmap)
                                }
                            }
                            imageProxy.close()
                        }
                    },
                )
            } catch (e: Exception) {
            }
        }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun setCaptureMode(mode: CaptureMode) {
        _uiState.update { it.copy(captureMode = mode) }
    }

    fun setBurstFrames(count: Int) {
        _uiState.update { it.copy(burstFrames = count.coerceIn(5, 12)) }
    }

    fun toggleEnhancement() {
        val enabled = !_uiState.value.enhancementEnabled
        _uiState.update { it.copy(enhancementEnabled = enabled) }
        diagnostics.recordEnhancementState(enabled, enhancer.isDegraded)
    }

    fun setSrEnabled(enabled: Boolean) {
        _uiState.update { it.copy(srEnabled = enabled) }
    }

    fun setQualityTier(tier: QualityTier) {
        _uiState.update { it.copy(qualityTier = tier) }
    }

    fun setSrScale(scale: Int) {
        _uiState.update { it.copy(srScale = scale.coerceIn(2, 4)) }
    }

    fun setVideoQualityTier(tier: VideoQualityTier) {
        _uiState.update { it.copy(videoQualityTier = tier) }
    }

    fun setAudioMode(mode: AudioMode) {
        _uiState.update { it.copy(audioMode = mode) }
    }

    fun bindVideoCapture(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        viewModelScope.launch {
            try {
                videoCaptureController.bind(lifecycleOwner)
            } catch (e: Exception) {
                _uiState.update { it.copy(lastError = "Video bind failed: ${e.message}") }
            }
        }
    }

    fun startVideoRecording(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(isRecording = true) }
        videoCaptureController.startRecording(lifecycleOwner) { result ->
            result.fold(
                onSuccess = { uri ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            lastVideoUri = uri,
                            lastError = null,
                        )
                    }
                    val path = uriToPath(uri)
                    if (path != null && _uiState.value.videoQualityTier != VideoQualityTier.ORIGINAL) {
                        enqueueVideoEnhancement(path)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            lastError = "Recording failed: ${error.message}",
                        )
                    }
                },
            )
        }
    }

    fun stopVideoRecording() {
        videoCaptureController.stopRecording()
    }

    private fun uriToPath(uri: Uri): String? {
        return try {
            val cursor = app.applicationContext.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Video.Media.DATA),
                null, null, null
            )
            cursor?.use {
                val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                if (it.moveToFirst()) it.getString(columnIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun enqueueVideoEnhancement(inputPath: String) {
        val outputDir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DCIM
            ),
            "PixelForge"
        )
        outputDir.mkdirs()
        val outputPath = java.io.File(
            outputDir,
            generateFileName("enhanced.mp4")
        ).absolutePath
        val state = _uiState.value
        val exportConfig = VideoExportConfig(state.videoQualityTier, state.audioMode)
        VideoEnhancementWorker.enqueue(
            context = app.applicationContext,
            inputPath = inputPath,
            outputPath = outputPath,
            tier = state.videoQualityTier,
            audioMode = state.audioMode,
            useHevc = false,
            exportToGallery = true,
        )
    }

    fun onShutter() {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true) }
        viewModelScope.launch {
            val shutterStart = System.nanoTime()
            try {
                previewController.unbind()
                val capabilities = halProbe.probeRearCamera()
                halCapabilities = capabilities
                val cameraId = capabilities?.cameraId
                    ?: throw CameraException("No rear camera available")
                controller.openCamera(cameraId)
                val state = _uiState.value
                val result: BurstResult
                val savedUri: Uri?
                val burstBytes: MutableList<ByteArray> = mutableListOf()
                if (state.captureMode == CaptureMode.RAW_DNG && capabilities.rawSupported) {
                    val sink = ReferenceFrameSink()
                    result = orchestrator.captureBurst(
                        frameCount = state.burstFrames,
                        targetFps = TARGET_BURST_FPS,
                        format = ImageFormat.YUV_420_888,
                        frameSink = sink,
                    )
                    savedUri = captureRaw()
                    sink.referenceJpeg?.let { burstBytes.add(it) }
                } else {
                    val sink = ReferenceFrameSink()
                    result = orchestrator.captureBurst(
                        frameCount = state.burstFrames,
                        targetFps = TARGET_BURST_FPS,
                        format = ImageFormat.YUV_420_888,
                        frameSink = sink,
                    )
                    savedUri = sink.referenceJpeg?.let { bytes ->
                        saver.saveJpeg(bytes, generateFileName("jpg"))
                    }
                    sink.referenceJpeg?.let { burstBytes.add(it) }
                }
                val shutterToSaveMs = (System.nanoTime() - shutterStart) / 1_000_000L
                diagnostics.recordShutterToSave(shutterToSaveMs)
                diagnostics.recordBurst(
                    BurstDiagnostics(
                        framesCaptured = result.stats.framesCaptured,
                        framesRequested = result.stats.framesRequested,
                        achievedFps = result.stats.achievedFps,
                        targetFps = TARGET_BURST_FPS,
                        droppedFrames = result.stats.droppedFrames,
                        durationMs = result.stats.durationMs,
                    )
                )
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastSavedUri = savedUri,
                        lastError = null,
                    )
                }
                if (state.srEnabled && burstBytes.isNotEmpty()) {
                    launchSrPipeline(burstBytes, state.qualityTier, state.srScale)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastError = e.message ?: "Capture failed",
                    )
                }
            } finally {
                controller.closeCamera()
                rebindPreview()
            }
        }
    }

    private suspend fun captureRaw(): Uri? {
        var dngBytes: ByteArray? = null
        controller.captureRawDng { bytes -> dngBytes = bytes }
        return dngBytes?.let { saver.saveRaw(it, generateFileName("dng")) }
    }

    /**
     * Runs the Temporal Burst SR pipeline (align -> fuse ->
     * tone-map -> upscale) after the original file is already
     * saved, so shutter-to-save stays under the 1.5 s budget.
     * The enhanced result is saved as a separate JPEG.
     */
    private fun launchSrPipeline(
        burstBytes: List<ByteArray>,
        tier: QualityTier,
        scale: Int,
    ) {
        viewModelScope.launch {
            try {
                val pipeline = BurstPipeline(
                    diagnostics = diagnostics,
                    flowNet = app.flowNet,
                    fusion = app.burstFusion,
                    esrgan = app.esrgan,
                )
                val result = pipeline.process(burstBytes, tier, scale)
                result.upscaledBitmap?.let { bitmap ->
                    val jpeg = bitmapToJpeg(bitmap, 92)
                    val uri = saver.saveJpeg(jpeg, generateFileName("sr.jpg"))
                    _uiState.update {
                        it.copy(lastEnhancedUri = uri)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(lastError = "SR pipeline failed: ${e.message}")
                }
            }
        }
    }

    private fun bitmapToJpeg(bitmap: android.graphics.Bitmap, quality: Int): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun rebindPreview() {
        viewModelScope.launch {
            try {
                previewController.bind(
                    lifecycleOwner = currentLifecycleOwner ?: return@launch,
                    previewView = currentPreviewView ?: return@launch,
                    analyzer = null,
                )
            } catch (e: Exception) {
            }
        }
    }

    private var currentLifecycleOwner: androidx.lifecycle.LifecycleOwner? = null
    private var currentPreviewView: androidx.camera.view.PreviewView? = null

    fun retainPreviewReferences(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
    ) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView
    }

    fun generateFileName(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date())
        return "pixelforge_$timestamp.$extension"
    }

    override fun onCleared() {
        super.onCleared()
        controller.shutdown()
        previewController.shutdown()
        videoCaptureController.shutdown()
        enhancer.release()
        app.modelRunner.release()
        app.flowNet.release()
        app.burstFusion.release()
        app.esrgan.release()
    }

    companion object {
        const val TARGET_BURST_FPS = 15
    }
}

enum class CaptureMode {
    BURST_YUV,
    RAW_DNG,
}

data class CameraUiState(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val captureMode: CaptureMode = CaptureMode.BURST_YUV,
    val burstFrames: Int = 8,
    val enhancementEnabled: Boolean = true,
    val srEnabled: Boolean = true,
    val qualityTier: QualityTier = QualityTier.FAST,
    val srScale: Int = 2,
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val videoQualityTier: VideoQualityTier = VideoQualityTier.ENHANCED_1080P,
    val audioMode: AudioMode = AudioMode.STRETCH,
    val lastSavedUri: Uri? = null,
    val lastEnhancedUri: Uri? = null,
    val lastVideoUri: Uri? = null,
    val lastError: String? = null,
)
