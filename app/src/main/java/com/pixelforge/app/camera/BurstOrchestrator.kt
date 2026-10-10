package com.pixelforge.app.camera

import android.graphics.ImageFormat
import android.media.Image
import com.pixelforge.app.diagnostics.Diagnostics

class BurstOrchestrator(
    private val controller: Camera2Controller,
    private val diagnostics: Diagnostics,
) {

    suspend fun captureBurst(
        frameCount: Int,
        targetFps: Int,
        format: Int = ImageFormat.YUV_420_888,
        pixelBudget: Long = Camera2Controller.DEFAULT_BURST_PIXEL_BUDGET,
        frameSink: BurstFrameSink,
    ): BurstResult {
        var remaining = frameCount.coerceIn(MIN_BURST_FRAMES, MAX_BURST_FRAMES)
        while (true) {
            try {
                val stats = controller.captureBurst(format, remaining, targetFps, pixelBudget) { image, index ->
                    frameSink.onFrame(image, index)
                }
                return BurstResult(stats = stats, format = format)
            } catch (e: Exception) {
                if (remaining > MIN_BURST_FRAMES) {
                    remaining = (remaining / 2).coerceAtLeast(MIN_BURST_FRAMES)
                    continue
                }
                throw e
            }
        }
    }

    companion object {
        const val MIN_BURST_FRAMES = 5
        const val MAX_BURST_FRAMES = 12
    }
}

data class BurstResult(
    val stats: BurstStats,
    val format: Int,
)

data class BurstStats(
    val framesCaptured: Int,
    val framesRequested: Int,
    val achievedFps: Double,
    val droppedFrames: Int,
    val durationMs: Long,
)

interface BurstFrameSink {
    fun onFrame(image: Image, index: Int)
}

class DiscardingFrameSink : BurstFrameSink {
    override fun onFrame(image: Image, index: Int) {
        image.close()
    }
}

class ReferenceFrameSink(
    private val jpegQuality: Int = 92,
) : BurstFrameSink {

    @Volatile
    var referenceJpeg: ByteArray? = null
        private set

    override fun onFrame(image: Image, index: Int) {
        if (index == 0 && image.format == ImageFormat.YUV_420_888) {
            referenceJpeg = YuvConverter.toJpegBytes(image, jpegQuality)
        }
        image.close()
    }
}
