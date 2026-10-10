package com.pixelforge.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.ml.BurstFusionRunner
import com.pixelforge.app.ml.EsrganLiteRunner
import com.pixelforge.app.ml.FlowNetRunner
import com.pixelforge.app.ml.MotionVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class QualityTier(val label: String) {
    FAST("Fast"),
    BALANCED("Balanced"),
    QUALITY("Quality"),
}

data class PipelineResult(
    val fusedBitmap: Bitmap?,
    val upscaledBitmap: Bitmap?,
    val scale: Int,
    val tier: QualityTier,
    val stageTimingsMs: Map<String, Long>,
    val motionSources: List<String>,
)

/**
 * Temporal Burst Super-Resolution pipeline:
 *
 *   frames -> align (FlowNet-Tiny) -> fuse (BurstFusion-Net)
 *          -> tone-map -> upscale (ESRGAN-Lite, tiled)
 *
 * Every stage is timed and reported through [Diagnostics]. The
 * pipeline is fully on-device; no frame leaves the process.
 */
class BurstPipeline(
    private val diagnostics: Diagnostics,
    private val flowNet: FlowNetRunner,
    private val fusion: BurstFusionRunner,
    private val esrgan: EsrganLiteRunner,
) {
    suspend fun process(
        frames: List<ByteArray>,
        tier: QualityTier = QualityTier.FAST,
        scale: Int = 2,
    ): PipelineResult = withContext(Dispatchers.Default) {
        val timings = mutableMapOf<String, Long>()

        val decodeStart = System.nanoTime()
        val bitmaps = frames.mapNotNull { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        timings["decode"] = (System.nanoTime() - decodeStart) / 1_000_000L
        if (bitmaps.isEmpty()) {
            throw IllegalStateException("Burst pipeline received no decodable frames")
        }

        val reference = bitmaps[0]

        val alignStart = System.nanoTime()
        val aligned = mutableListOf(reference)
        val motionSources = mutableListOf("reference")
        for (i in 1 until bitmaps.size) {
            val motion: MotionVector = flowNet.estimateMotion(reference, bitmaps[i])
            val shifted = shiftBitmap(bitmaps[i], -motion.dx, -motion.dy)
            aligned.add(shifted)
            motionSources.add(motion.source)
        }
        timings["align"] = (System.nanoTime() - alignStart) / 1_000_000L

        val fuseStart = System.nanoTime()
        val fused = fusion.fuse(aligned)
        timings["fuse"] = (System.nanoTime() - fuseStart) / 1_000_000L

        val toneStart = System.nanoTime()
        val toneMapped = toneMap(fused)
        timings["tone-map"] = (System.nanoTime() - toneStart) / 1_000_000L

        val upscaleStart = System.nanoTime()
        val upscaled = esrgan.upscale(toneMapped, scale)
        timings["upscale"] = (System.nanoTime() - upscaleStart) / 1_000_000L

        diagnostics.recordBurstPipeline(
            frames = bitmaps.size,
            tier = tier.label,
            scale = scale,
            totalMs = timings.values.sum(),
            stageTimingsMs = timings
        )

        PipelineResult(
            fusedBitmap = toneMapped,
            upscaledBitmap = upscaled,
            scale = scale,
            tier = tier,
            stageTimingsMs = timings,
            motionSources = motionSources,
        )
    }

    /**
     * Translates [source] by (dx, dy) with edge clamping. Used to
     * register burst frames onto the reference frame before fusion.
     */
    private fun shiftBitmap(source: Bitmap, dx: Float, dy: Float): Bitmap {
        if (dx == 0f && dy == 0f) return source
        val matrix = Matrix().apply { postTranslate(dx, dy) }
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    /**
     * Mild S-curve tone map: lifts shadows, protects highlights.
     * Keeps ESRGAN from amplifying sensor noise in dark regions.
     */
    private fun toneMap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = toneChannel(p shr 16 and 0xFF)
            val g = toneChannel(p shr 8 and 0xFF)
            val b = toneChannel(p and 0xFF)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun toneChannel(value: Int): Int {
        val v = value / 255.0f
        val mapped = v * v * (3f - 2f * v)
        return (mapped * 255f).toInt().coerceIn(0, 255)
    }

    companion object {
        const val MAX_ALIGNMENT_PX = FlowNetRunner.MAX_MOTION_PX
    }
}
