package com.pixelforge.app.ml

import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.video.MediaCodecFrameEncoder
import com.pixelforge.app.video.MediaCodecFrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Coordinates frame interpolation over a decoded video stream.
 *
 * Maintains a sliding window of two frames (prev, current) and invokes
 * [RifeRunner] to generate intermediate frames. Encodes original + intermediate
 * frames in temporal order.
 */
class FrameInterpolator(
    private val rifeRunner: RifeRunner,
    private val diagnostics: Diagnostics,
) {

    data class Config(
        val scale: Int, // 2 or 4
        val doUpscale: Boolean,
        val targetWidth: Int,
        val targetHeight: Int,
        val useHevc: Boolean,
        val frameRate: Int,
    )

    data class Result(
        val framesEncoded: Int,
        val outputSizeBytes: Long,
    )

    /**
     * Runs the full interpolation pipeline.
     *
     * Flow:
     * 1. Probe source video
     * 2. Configure decoder (buffer mode) and encoder
     * 3. For each decoded frame:
     *    - If first frame: encode directly
     *    - Else: interpolate(prev, current) → intermediates
     *    - Encode prev + intermediates in order
     * 4. Encode final frame
     */
    suspend fun run(
        config: Config,
        inputPath: String,
        outputPath: String,
        srModelRunner: SrModelRunner?,
        onProgress: (Float) -> Unit,
    ): Result = withContext(Dispatchers.Default) {
        val probeSource = MediaCodecFrameSource()
        val probe = try {
            probeSource.probe(inputPath)
        } finally {
            probeSource.release()
        }

        val targetWidth = if (config.doUpscale) config.targetWidth else probe.width
        val targetHeight = if (config.doUpscale) config.targetHeight else probe.height
        val encoderFrameRate = config.frameRate * config.scale

        val encoder = MediaCodecFrameEncoder(
            width = targetWidth,
            height = targetHeight,
            frameRate = encoderFrameRate,
            bitRate = MediaCodecFrameEncoder.bitRateFor(targetWidth, targetHeight),
            useHevc = config.useHevc,
        )

        var framesEncoded = 0
        val startTimeNs = System.nanoTime()
        var prevFrame: Bitmap? = null
        var prevPresentationTimeUs: Long = 0

        try {
            val frameSource = MediaCodecFrameSource()
            frameSource.configureWithBuffers(inputPath)
            encoder.configure(outputPath)

            val totalInputFrames = probe.frameCount
            val totalOutputFrames = totalInputFrames * config.scale.toLong()

            // Channel for streaming frames from decode callback to coroutine
            val frameChannel = Channel<FrameData>(capacity = 16)

            // Start decode in a separate coroutine
            val decodeJob = launch(Dispatchers.Default) {
                try {
                    frameSource.decode { index, total, buffer, info ->
                        if (!isActive) {
                            frameChannel.close(CancellationException("Decode cancelled"))
                            return@decode
                        }
                        val pts = info?.presentationTimeUs ?: (index * 1_000_000L / config.frameRate)
                        val frameData = FrameData(
                            index = index,
                            total = total,
                            buffer = buffer,
                            presentationTimeUs = pts,
                            isEndOfStream = index.toLong() == total - 1L
                        )
                        // Use trySend since we're in a non-suspend callback
                        val result = frameChannel.trySend(frameData)
                        if (result.isClosed) {
                            return@decode
                        }
                    }
                    frameChannel.close()
                } catch (e: Exception) {
                    frameChannel.close(e)
                }
            }

            // Process frames from channel
            try {
                for (frameData in frameChannel) {
                    if (!isActive) throw CancellationException()
                    if (config.scale == 1 || frameData.buffer == null) {
                        // No interpolation: pass through (with optional upscale)
                        val frameBuffer = if (config.doUpscale && srModelRunner != null && frameData.buffer != null) {
                            val bitmap = decodeFrameToBitmap(frameData.buffer!!, probe.width, probe.height)
                            val upscaled = srModelRunner.upscale(bitmap, 2)
                            bitmap.recycle()
                            encodeBitmapToYuv(upscaled, targetWidth, targetHeight)
                        } else {
                            frameData.buffer ?: ByteBuffer.allocate(0)
                        }
                        encoder.encodeFrame(frameBuffer, frameData.presentationTimeUs)
                        framesEncoded++
                    } else {
                        val currentFrame = decodeFrameToBitmap(frameData.buffer!!, probe.width, probe.height)
                        val currentPts = frameData.presentationTimeUs

                        if (prevFrame != null) {
                            // Upscale prev frame if needed
                            val prevToEncode: Bitmap? = if (config.doUpscale && srModelRunner != null) {
                                val upscaled = srModelRunner.upscale(prevFrame!!, 2)
                                prevFrame!!.recycle()
                                upscaled
                            } else prevFrame

                            // Encode previous frame
                            val prevPts = prevPresentationTimeUs
                            val prevBuffer = if (prevToEncode != null) {
                                encodeBitmapToYuv(prevToEncode, targetWidth, targetHeight)
                            } else ByteBuffer.allocate(0)
                            encoder.encodeFrame(prevBuffer, prevPts)
                            framesEncoded++

                            // Interpolate and encode intermediates (suspend call)
                            if (prevToEncode != null) {
                                val intermediates = rifeRunner.interpolate(prevToEncode, currentFrame, config.scale)
                                val frameIntervalUs = (currentPts - prevPts) / (config.scale + 1)
                                for (i in intermediates.indices) {
                                    if (!isActive) throw CancellationException()
                                    val interpPts = prevPts + frameIntervalUs * (i + 1)
                                    val interpBuffer = encodeBitmapToYuv(intermediates[i], targetWidth, targetHeight)
                                    encoder.encodeFrame(interpBuffer, interpPts)
                                    framesEncoded++
                                    intermediates[i].recycle()
                                }
                            }
                        }

                        prevFrame = currentFrame
                        prevPresentationTimeUs = currentPts
                    }

                    val progress = if (totalOutputFrames > 0) framesEncoded.toFloat() / totalOutputFrames else 0f
                    onProgress(progress)
                }
            } finally {
                decodeJob.cancel()
            }

            // Encode final frame
            if (prevFrame != null) {
                val finalPts = prevPresentationTimeUs
                val finalBuffer = if (config.doUpscale && srModelRunner != null) {
                    val upscaled = srModelRunner.upscale(prevFrame!!, 2)
                    prevFrame!!.recycle()
                    encodeBitmapToYuv(upscaled, targetWidth, targetHeight)
                } else {
                    encodeBitmapToYuv(prevFrame!!, targetWidth, targetHeight)
                }
                encoder.encodeFrame(finalBuffer, finalPts)
                framesEncoded++
            }

            val outputSize = encoder.finish()
            val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0

            Result(
                framesEncoded = framesEncoded,
                outputSizeBytes = outputSize,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VideoPipelineException("Interpolation failed: ${e.message}", e)
        }
    }

    private data class FrameData(
        val index: Int,
        val total: Long,
        val buffer: ByteBuffer?,
        val presentationTimeUs: Long,
        val isEndOfStream: Boolean = false,
    )

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
}

class VideoPipelineException(message: String, cause: Throwable? = null) : Exception(message, cause)