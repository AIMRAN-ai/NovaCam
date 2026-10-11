package com.pixelforge.app.video

/**
 * Complete export configuration for video enhancement jobs.
 * Encapsulates quality tier, audio mode, and codec choice.
 */
data class VideoExportConfig(
    val tier: VideoQualityTier,
    val audioMode: AudioMode = AudioMode.defaultForRifeScale(tier.rifeScale),
    val useHevc: Boolean = false,
) {
    /**
     * Creates a config from the current UI state.
     */
    companion object {
        fun fromUiState(
            tier: VideoQualityTier,
            audioMode: AudioMode? = null,
            useHevc: Boolean = false,
        ): VideoExportConfig {
            return VideoExportConfig(
                tier = tier,
                audioMode = audioMode ?: AudioMode.defaultForRifeScale(tier.rifeScale),
                useHevc = useHevc,
            )
        }
    }

    /**
     * Effective frame rate after RIFE interpolation.
     */
    val effectiveFrameRate: Int
        get() = 30 * tier.rifeScale

    /**
     * Whether audio processing is needed.
     */
    val needsAudioProcessing: Boolean
        get() = audioMode == AudioMode.STRETCH && tier.rifeScale > 1

    /**
     * Estimated output duration multiplier.
     */
    val durationMultiplier: Float
        get() = tier.rifeScale.toFloat()
}