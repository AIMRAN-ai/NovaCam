package com.pixelforge.app.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Decodes a video file into individual frames using
 * MediaExtractor + MediaCodec.
 *
 * Two output modes:
 * - [configureWithSurface]: frames are rendered to a Surface
 *   (used when the consumer does GPU processing).
 * - [configureWithBuffers]: frames are returned as ByteBuffer
 *   (used for CPU/NNAPI paths, e.g. ESRGAN on Bitmap).
 *
 * The decoder is configured for the video's native resolution;
 * upscaling happens downstream in [VideoUpscalePipeline].
 */
class MediaCodecFrameSource {

    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var outputSurface: Surface? = null

    var frameCallback: ((frameIndex: Int, totalFrames: Long) -> Unit)? = null

    data class TrackInfo(
        val width: Int,
        val height: Int,
        val frameCount: Long,
        val durationUs: Long,
        val rotationDegrees: Int,
        val mime: String,
        val frameRate: Int = 30,
    )

    fun probe(path: String): TrackInfo {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        return try {
            val trackIndex = selectVideoTrack(extractor)
                ?: throw IllegalArgumentException("No video track in $path")
            val format = extractor.getTrackFormat(trackIndex)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else 0
            val frameCount = estimateFrameCount(format, durationUs)
            val frameRate = when {
                format.containsKey("frame-rate") -> format.getInteger("frame-rate")
                format.containsKey(MediaFormat.KEY_FRAME_RATE) -> format.getInteger(MediaFormat.KEY_FRAME_RATE)
                else -> 30
            }
            TrackInfo(width, height, frameCount, durationUs, rotation, "video/avc", frameRate)
        } finally {
            extractor.release()
        }
    }

    fun configureWithSurface(path: String, surface: Surface) {
        outputSurface = surface
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val trackIndex = selectVideoTrack(extractor)
            ?: throw IllegalArgumentException("No video track in $path")
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        extractor.selectTrack(trackIndex)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, surface, null, 0)
        decoder.start()

        this.extractor = extractor
        this.decoder = decoder
    }

    fun configureWithBuffers(path: String) {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val trackIndex = selectVideoTrack(extractor)
            ?: throw IllegalArgumentException("No video track in $path")
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        extractor.selectTrack(trackIndex)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        this.extractor = extractor
        this.decoder = decoder
    }

    /**
     * Runs the decode loop. Blocks the calling thread; callers
     * should run this on a background dispatcher.
     *
     * [onFrame] receives (index, totalFrames, buffer, bufferInfo)
     * for buffer mode, or (index, totalFrames, null, null)
     * for surface mode (frame already rendered to Surface).
     */
    fun decode(
        onFrame: (index: Int, totalFrames: Long, buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) -> Unit,
    ) {
        val decoder = decoder ?: throw IllegalStateException("Not configured")
        val extractor = extractor ?: throw IllegalStateException("Not configured")
        val info = MediaCodec.BufferInfo()
        val totalFrames = estimateTotalFrames()
        var frameIndex = 0
        var sawInputEnd = false
        var sawOutputEnd = false

        while (!sawOutputEnd) {
            if (!sawInputEnd) {
                val inputBufferId = decoder.dequeueInputBuffer(10_000)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferId, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEnd = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(
                            inputBufferId, 0, sampleSize,
                            presentationTimeUs, 0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputBufferId = decoder.dequeueOutputBuffer(info, 10_000)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                }
                outputBufferId >= 0 -> {
                    val isEndOfStream =
                        info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!isEndOfStream) {
                        val buffer = if (outputSurface == null) {
                            decoder.getOutputBuffer(outputBufferId)
                        } else null
                        onFrame(frameIndex, totalFrames, buffer, info)
                        frameIndex++
                    }
                    decoder.releaseOutputBuffer(outputBufferId, outputSurface != null)
                    if (isEndOfStream) sawOutputEnd = true
                }
            }
        }
    }

    fun release() {
        decoder?.stop()
        decoder?.release()
        decoder = null
        extractor?.release()
        extractor = null
        outputSurface?.release()
        outputSurface = null
        frameCallback = null
    }

    private fun estimateTotalFrames(): Long {
        val extractor = extractor ?: return 0L
        val trackIndex = selectVideoTrack(extractor) ?: return 0L
        val format = extractor.getTrackFormat(trackIndex)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        return estimateFrameCount(format, durationUs)
    }

    private fun estimateFrameCount(format: MediaFormat, durationUs: Long): Long {
        val fps = when {
            format.containsKey("frame-rate") -> format.getInteger("frame-rate")
            format.containsKey(MediaFormat.KEY_FRAME_RATE) -> format.getInteger(MediaFormat.KEY_FRAME_RATE)
            else -> 30
        }
        val safeFps = if (fps <= 0) 30 else fps
        return (durationUs / 1_000_000 * safeFps).coerceAtLeast(1)
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return null
    }
}
