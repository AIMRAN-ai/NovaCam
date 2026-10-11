package com.pixelforge.app.video

/**
 * Audio processing mode for slow-motion video export.
 *
 * STRETCH: Audio is time-stretched to match the slowed-down video duration.
 *          Natural for speech/actions; may introduce artifacts at extreme stretch.
 * KEEP_ORIGINAL: Audio plays at original speed while video is interpolated.
 *                Good for music videos; audio/video will drift out of sync.
 */
enum class AudioMode(val label: String, val description: String) {
    STRETCH(
        "Stretch Audio",
        "Audio slows down with video (natural slow-mo)"
    ),
    KEEP_ORIGINAL(
        "Keep Original Audio",
        "Audio plays at normal speed, video interpolated"
    );

    /**
     * Returns the recommended default mode for a given frame rate multiplier.
     * STRETCH for 2× (60fps), KEEP_ORIGINAL for 4× (120fps).
     */
    companion object {
        fun defaultForRifeScale(rifeScale: Int): AudioMode = when (rifeScale) {
            2 -> STRETCH
            4 -> KEEP_ORIGINAL
            else -> STRETCH
        }
    }
}