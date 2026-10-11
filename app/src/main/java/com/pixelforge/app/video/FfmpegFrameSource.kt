package com.pixelforge.app.video

import android.util.Log
import com.pixelforge.app.BuildConfig
import java.io.File

/**
 * FFmpegKit fallback frame source. Only compiled into the
 * `ffmpeg` product flavor (BuildConfig.ENABLE_FFMPEG_KIT).
 *
 * Used when MediaCodec cannot decode an exotic container
 * (e.g. MKV/AVI with unusual codecs). Extracts frames to
 * a temporary directory as JPEGs, which the pipeline then
 * feeds through the same ESRGAN path.
 *
 * GPL-licensed (ffmpeg-kit-full-gpl). Not in the default
 * build: keeps APK within the 120MB install NFR.
 */
class FfmpegFrameSource(
    private val inputPath: String,
    private val frameDir: File,
) {

    data class ExtractResult(
        val frameCount: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Extracts all frames to [frameDir] as frame_%06d.jpg.
     * Returns null on failure.
     */
    fun extractFrames(): ExtractResult? {
        if (!BuildConfig.ENABLE_FFMPEG_KIT) {
            Log.w(TAG, "FFmpegKit not enabled in this build")
            return null
        }
        frameDir.mkdirs()
        val pattern = File(frameDir, "frame_%06d.jpg").absolutePath
        val cmd = "-i $inputPath -vsync 0 $pattern"

        val session: Any = runFfmpeg(cmd)
        val success = isFfmpegSuccess(session)

        if (!success) {
            val logs = ffmpegLogs(session)
            Log.e(TAG, "ffmpeg extract failed: $logs")
            return null
        }

        val frames = frameDir.listFiles { file -> file.name.startsWith("frame_") }
            ?.sortedBy { it.name }
            ?: emptyList()

        val dimensions = probeFirstFrame(frames.firstOrNull())
        return ExtractResult(frames.size, dimensions.first, dimensions.second)
    }

    private fun runFfmpeg(cmd: String): Any {
        val clazz = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        val method = clazz.getMethod("execute", String::class.java)
        return method.invoke(null, cmd)
    }

    private fun isFfmpegSuccess(session: Any): Boolean {
        val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
        val sessionClass = Class.forName("com.arthenica.ffmpegkit.FFmpegSession")
        val getReturnCode = sessionClass.getMethod("getReturnCode")
        val returnCode = getReturnCode.invoke(session)
        val isSuccess = returnCodeClass.getMethod("isSuccess", returnCodeClass)
        return isSuccess.invoke(null, returnCode) as Boolean
    }

    private fun ffmpegLogs(session: Any): String {
        val sessionClass = Class.forName("com.arthenica.ffmpegkit.FFmpegSession")
        val method = sessionClass.getMethod("getAllLogsAsString")
        return method.invoke(session) as String
    }

    private fun probeFirstFrame(frame: File?): Pair<Int, Int> {
        if (frame == null) return 0 to 0
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(frame.absolutePath, options)
        return (options.outWidth) to (options.outHeight)
    }

    companion object {
        private const val TAG = "FfmpegFrameSource"
    }
}
