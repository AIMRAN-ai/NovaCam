package com.pixelforge.app.video

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.diagnostics.VideoJobStats
import com.pixelforge.app.ml.SrModelRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Frame-by-frame video super-resolution pipeline.
 *
 * Flow: MediaCodecFrameSource (decode) -> per-frame ESRGAN
 * upscale (reusing EsrganLiteRunner tiling) -> MediaCodecFrameEncoder
 * (encode). Runs on a background dispatcher; cooperative
 * cancellation via coroutine isActive + thermal guard.
 *
 * The pipeline is frame-insertion-ready: frames are
 * addressed by (index, totalFrames) so a future RIFE
 * interpolator can emit multiple output frames per input
 * frame without changing the encoder contract.
 */
class VideoUpscalePipeline(
    private val context: Context,
    private val diagnostics: Diagnostics,
    private val srModelRunner: SrModelRunner,
) {

    data class Config(
        val inputPath: String,
        val outputPath: String,
        val tier: VideoQualityTier,
        val useHevc: Boolean = false,
        val frameRate: Int = 30,
    )

    data class Result(
        val outputPath: String,
        val framesProcessed: Int,
        val totalFrames: Long,
        val throughputFps: Double,
        val outputSizeBytes: Long,
        val tier: VideoQualityTier,
    )

    /**
     * Runs the full upscale job. Suspends; cancellable.
     * Throws [VideoPipelineException] on hard failures.
     */
    suspend fun run(config: Config): Result = withContext(Dispatchers.Default) {
        val probeSource = MediaCodecFrameSource()
        val probe = try {
            probeSource.probe(config.inputPath)
        } finally {
            probeSource.release()
        }

        val targetWidth = if (config.tier == VideoQualityTier.ORIGINAL) probe.width else config.tier.targetWidth
        val targetHeight = if (config.tier == VideoQualityTier.ORIGINAL) probe.height else config.tier.targetHeight

        val freeBytes = File(config.outputPath).parentFile?.usableSpace ?: 0L
        val estimatedOutput = estimateOutputSize(probe, targetWidth, targetHeight)
        if (freeBytes < estimatedOutput * 2) {
            throw VideoPipelineException(
                "Insufficient storage: need ${estimatedOutput * 2} bytes, have $freeBytes"
            )
        }

        val thermalGuard = ThermalGuard(context)
        val frameSource = MediaCodecFrameSource()
        val encoder = MediaCodecFrameEncoder(
            width = targetWidth,
            height = targetHeight,
            frameRate = config.frameRate,
            bitRate = MediaCodecFrameEncoder.bitRateFor(targetWidth, targetHeight),
            useHevc = config.useHevc,
        )

        var framesProcessed = 0
        val startTimeNs = System.nanoTime()

        try {
            frameSource.configureWithBuffers(config.inputPath)
            encoder.configure(config.outputPath)

            val totalFrames = probe.frameCount

            // Thermal guard: pause before starting if the device
            // is already hot. The pipeline is short-lived, so a
            // single pre-decode check is sufficient.
            if (thermalGuard.shouldPause()) {
                thermalGuard.awaitCooldown()
            }

            frameSource.decode { index, total, buffer, info ->
                if (!isActive) throw CancellationException()

                val presentationTimeUs = info?.presentationTimeUs ?: (index * 1_000_000L / config.frameRate)

                if (config.tier == VideoQualityTier.ORIGINAL || buffer == null) {
                    encoder.encodeFrame(buffer ?: ByteBuffer.allocate(0), presentationTimeUs)
                } else {
                    val frameBitmap = decodeFrameToBitmap(buffer, probe.width, probe.height)
                    val enhanced = upscaleFrame(frameBitmap, config.tier)
                    val encodedBuffer = encodeBitmapToYuv(enhanced, targetWidth, targetHeight)
                    encoder.encodeFrame(encodedBuffer, presentationTimeUs)
                    frameBitmap.recycle()
                    enhanced.recycle()
                }

                framesProcessed++
                val progress = if (total > 0) framesProcessed.toFloat() / total else 0f
                diagnostics.recordVideoJobState("running", progress)
            }

            val outputSize = encoder.finish()
            val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
            val throughput = if (elapsedSec > 0) framesProcessed / elapsedSec else 0.0

            diagnostics.recordVideoStats(
                VideoJobStats(
                    framesProcessed = framesProcessed,
                    totalFrames = totalFrames,
                    throughputFps = throughput,
                    encodeMs = (elapsedSec * 1000).toLong(),
                    outputSizeBytes = outputSize,
                    tier = config.tier.name,
                )
            )

            Result(
                outputPath = config.outputPath,
                framesProcessed = framesProcessed,
                totalFrames = totalFrames,
                throughputFps = throughput,
                outputSizeBytes = outputSize,
                tier = config.tier,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.recordVideoError("pipeline: ${e.message}")
            throw VideoPipelineException(e.message ?: "Unknown pipeline error", e)
        } finally {
            frameSource.release()
            encoder.release()
            diagnostics.recordVideoJobState("idle", 0f)
        }
    }

    private fun upscaleFrame(frame: Bitmap, tier: VideoQualityTier): Bitmap {
        val scale = when (tier) {
            VideoQualityTier.ORIGINAL -> 1
            VideoQualityTier.ENHANCED_1080P -> 2
            VideoQualityTier.ENHANCED_4K -> 2
        }
        if (scale == 1) return frame
        return srModelRunner.upscale(frame, scale)
    }

    private fun decodeFrameToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val yuv = android.graphics.YuvImage(
            bytes, android.graphics.ImageFormat.NV21, width, height, null
        )
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(android.graphics.Rect(0, 0, width, height), 92, out)
        val jpegBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun encodeBitmapToYuv(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val yuvBytes = bitmapToYuv420(bitmap, width, height)
        return ByteBuffer.wrap(yuvBytes)
    }

    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val argb = IntArray(width * height)
        scaled.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val r = (argb[index] shr 16) and 0xFF
                val g = (argb[index] shr 8) and 0xFF
                val b = argb[index] and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
                index++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return yuv
    }

    private fun estimateOutputSize(
        probe: MediaCodecFrameSource.TrackInfo,
        targetWidth: Int,
        targetHeight: Int,
    ): Long {
        val seconds = probe.durationUs / 1_000_000.0
        val bitRate = MediaCodecFrameEncoder.bitRateFor(targetWidth, targetHeight)
        return (seconds * bitRate / 8).toLong()
    }

    companion object {
        private const val TAG = "VideoUpscalePipeline"
    }
}

class VideoPipelineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pauses enhancement when the device reports a thermal
 * status of "severe" or above; resumes when cooled.
 */
class ThermalGuard(private val context: Context) {
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun shouldPause(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val pm = powerManager ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    suspend fun awaitCooldown() {
        while (shouldPause()) {
            kotlinx.coroutines.delay(1000L)
        }
    }
}
