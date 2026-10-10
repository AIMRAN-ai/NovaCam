package com.pixelforge.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class PreviewEnhancer(
    private val modelRunner: ModelRunner,
    private val diagnostics: Diagnostics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val _enhancedFrames = MutableStateFlow<Bitmap?>(null)
    val enhancedFrames: StateFlow<Bitmap?> = _enhancedFrames

    private val lastProcessedAt = AtomicLong(0L)
    private val lastAnalyzedAt = AtomicLong(0L)
    private var degraded = false

    val isDegraded: Boolean get() = degraded

    suspend fun processFrame(bitmap: Bitmap) {
        if (degraded) return
        val now = System.nanoTime()
        val last = lastProcessedAt.get()
        val intervalNs = 1_000_000_000L / PREVIEW_FPS_CAP
        if (now - last < intervalNs) return
        if (!lastProcessedAt.compareAndSet(last, now)) return

        val scaled = scaleToMaxWidth(bitmap, MAX_WIDTH)
        try {
            val enhanced = withContext(dispatcher) { modelRunner.enhance(scaled) }
            _enhancedFrames.value = enhanced
            recordLatency()
        } catch (e: OutOfMemoryError) {
            degrade()
        } catch (e: Exception) {
            degrade()
        }
    }

    fun recordAnalysis() {
        val now = System.nanoTime()
        val last = lastAnalyzedAt.getAndSet(now)
        if (last > 0L) {
            val fps = 1_000_000_000.0 / (now - last)
            diagnostics.recordPreviewStats(fps, 0L)
        }
    }

    private fun recordLatency() {
        val now = System.nanoTime()
        val last = lastAnalyzedAt.get()
        if (last > 0L) {
            diagnostics.recordPreviewStats(0.0, (now - last) / 1_000_000L)
        }
    }

    private fun scaleToMaxWidth(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val scale = maxWidth.toFloat() / source.width.toFloat()
        val height = (source.height * scale).toInt()
        return Bitmap.createScaledBitmap(source, maxWidth, height, true)
    }

    private fun degrade() {
        if (degraded) return
        degraded = true
        _enhancedFrames.value = null
        diagnostics.recordEnhancementState(false, true)
    }

    fun release() {
        _enhancedFrames.value?.recycle()
        _enhancedFrames.value = null
    }

    companion object {
        const val PREVIEW_FPS_CAP = 15
        const val MAX_WIDTH = 512
    }
}
