package com.pixelforge.app.video

import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.diagnostics.VideoJobStats

/**
 * Diagnostics extensions for the video pipeline. Kept in
 * the shared Diagnostics class via record* methods (see Diagnostics.kt).
 */
class VideoDiagnostics(private val diagnostics: Diagnostics) {
    fun recordJobState(state: String, progress: Float = 0f) {
        diagnostics.recordVideoJobState(state, progress)
    }

    fun recordStats(stats: VideoJobStats) {
        diagnostics.recordVideoStats(stats)
    }

    fun recordError(error: String) {
        diagnostics.recordVideoError(error)
    }
}
