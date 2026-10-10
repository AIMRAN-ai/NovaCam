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
import com.pixelforge.app.camera.BurstResult
import com.pixelforge.app.camera.Camera2Controller
import com.pixelforge.app.camera.CameraException
import com.pixelforge.app.camera.CameraHalProbe
import com.pixelforge.app.camera.HalCapabilities
import com.pixelforge.app.camera.PreviewController
import com.pixelforge.app.camera.ReferenceFrameSink
import com.pixelforge.app.diagnostics.BurstDiagnostics
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.io.MediaStoreSaver
import com.pixelforge.app.ml.PreviewEnhancer
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
                if (state.captureMode == CaptureMode.RAW_DNG && capabilities.rawSupported) {
                    val sink = ReferenceFrameSink()
                    result = orchestrator.captureBurst(
                        frameCount = state.burstFrames,
                        targetFps = TARGET_BURST_FPS,
                        format = ImageFormat.YUV_420_888,
                        frameSink = sink,
                    )
                    savedUri = captureRaw()
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
        enhancer.release()
        app.modelRunner.release()
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
    val isCapturing: Boolean = false,
    val lastSavedUri: Uri? = null,
    val lastError: String? = null,
)
