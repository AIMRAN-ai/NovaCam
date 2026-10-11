package com.pixelforge.app.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

/**
 * Hardware H.264/H.265 encoder wrapping MediaCodec + MediaMuxer.
 *
 * Encodes raw frames (ByteBuffer in the configured color format)
 * into an MP4 at the target resolution. Used by [VideoUpscalePipeline]
 * to write enhanced frames.
 *
 * H.264 is the default (TRD: HEVC unstable on Helio G85);
 * H.265 is opt-in via [useHevc].
 */
class MediaCodecFrameEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitRate: Long,
    private val useHevc: Boolean = false,
) {

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false

    val isConfigured: Boolean get() = encoder != null

    fun configure(outputPath: String) {
        val mime = if (useHevc) "video/hevc" else "video/avc"
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec

        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Feeds one frame to the encoder and drains encoded output
     * into the muxer.
     */
    fun encodeFrame(buffer: ByteBuffer, presentationTimeUs: Long) {
        val codec = encoder ?: throw IllegalStateException("Not configured")
        val inputBufferId = codec.dequeueInputBuffer(10_000)
        if (inputBufferId >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
            inputBuffer.clear()
            inputBuffer.put(buffer)
            codec.queueInputBuffer(
                inputBufferId, 0, buffer.remaining(),
                presentationTimeUs, 0
            )
        }
        drainEncoder(false)
    }

    fun finish(): Long {
        val codec = encoder ?: throw IllegalStateException("Not configured")
        drainEncoder(true)
        val outputSize = muxerSize
        release()
        return outputSize
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = encoder ?: return
        val muxer = muxer ?: return
        val info = MediaCodec.BufferInfo()

        if (endOfStream) {
            val inputBufferId = codec.dequeueInputBuffer(10_000)
            if (inputBufferId >= 0) {
                codec.queueInputBuffer(
                    inputBufferId, 0, 0, 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
        }

        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        Log.w(TAG, "format changed twice")
                        return
                    }
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputBufferId >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputBufferId)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted) {
                        encodedData.position(info.offset)
                        encodedData.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, encodedData, info)
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return
                    }
                }
            }
        }
    }

    private val muxerSize: Long
        get() {
            val muxer = muxer ?: return 0L
            return try {
                val method = MediaMuxer::class.java.getMethod("getFileSize")
                method.invoke(muxer) as Long
            } catch (e: Exception) {
                0L
            }
        }

    private val colorFormat: Int
        get() = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface

    fun release() {
        try {
            encoder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "encoder stop failed", e)
        }
        encoder?.release()
        encoder = null
        try {
            if (muxerStarted) muxer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "muxer stop failed", e)
        }
        muxer?.release()
        muxer = null
        muxerStarted = false
        trackIndex = -1
    }

    companion object {
        private const val TAG = "MediaCodecFrameEncoder"

        /**
         * Bitrate budget for a given resolution, calibrated for
         * Helio G85 hardware encoders. Keeps 60s clips within
         * the 12-minute NFR at 4K.
         */
        fun bitRateFor(width: Int, height: Int): Long {
            val pixels = width.toLong() * height
            return when {
                pixels >= 3840L * 2160L -> 24_000_000L
                pixels >= 1920L * 1080L -> 12_000_000L
                else -> 8_000_000L
            }
        }
    }
}
