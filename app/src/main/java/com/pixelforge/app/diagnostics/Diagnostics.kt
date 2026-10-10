package com.pixelforge.app.diagnostics

import android.os.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BurstDiagnostics(
    val framesCaptured: Int,
    val framesRequested: Int,
    val achievedFps: Double,
    val targetFps: Int,
    val droppedFrames: Int,
    val durationMs: Long,
)

data class DiagnosticsState(
    val delegatePath: String = "not-initialized",
    val coldStartMs: Long? = null,
    val shutterToSaveMs: Long? = null,
    val lastBurst: BurstDiagnostics? = null,
    val lastPipeline: PipelineDiagnostics? = null,
    val halSummary: String = "not-probed",
    val ramUsedMb: Int = 0,
    val previewFps: Double? = null,
    val previewLatencyMs: Long? = null,
    val enhancementEnabled: Boolean = true,
    val enhancementDegraded: Boolean = false,
)

data class PipelineDiagnostics(
    val frames: Int,
    val tier: String,
    val scale: Int,
    val totalMs: Long,
    val stageTimingsMs: Map<String, Long>,
)

class Diagnostics {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state

    fun recordColdStart(ms: Long) {
        _state.update { it.copy(coldStartMs = ms) }
    }

    fun recordShutterToSave(ms: Long) {
        _state.update { it.copy(shutterToSaveMs = ms) }
    }

    fun recordBurst(burst: BurstDiagnostics) {
        _state.update { it.copy(lastBurst = burst) }
    }

    fun recordBurstPipeline(
        frames: Int,
        tier: String,
        scale: Int,
        totalMs: Long,
        stageTimingsMs: Map<String, Long>,
    ) {
        _state.update {
            it.copy(
                lastPipeline = PipelineDiagnostics(
                    frames = frames,
                    tier = tier,
                    scale = scale,
                    totalMs = totalMs,
                    stageTimingsMs = stageTimingsMs,
                )
            )
        }
    }

    fun recordHalSummary(summary: String) {
        _state.update { it.copy(halSummary = summary) }
    }

    fun recordDelegatePath(path: String) {
        _state.update { it.copy(delegatePath = path) }
    }

    fun recordPreviewStats(fps: Double, latencyMs: Long) {
        _state.update { it.copy(previewFps = fps, previewLatencyMs = latencyMs) }
    }

    fun recordEnhancementState(enabled: Boolean, degraded: Boolean) {
        _state.update { it.copy(enhancementEnabled = enabled, enhancementDegraded = degraded) }
    }

    fun recordRam(mb: Int) {
        _state.update { it.copy(ramUsedMb = mb) }
    }

    fun measureRam(): Int {
        val runtime = Runtime.getRuntime()
        val javaMb = ((runtime.totalMemory() - runtime.freeMemory()) / 1048576L).toInt()
        val nativeMb = (Debug.getNativeHeapAllocatedSize() / 1048576L).toInt()
        return javaMb + nativeMb
    }
}
