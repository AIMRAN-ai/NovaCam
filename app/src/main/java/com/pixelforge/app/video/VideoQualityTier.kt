package com.pixelforge.app.video

/**
 * Output quality tiers for video enhancement.
 * Maps to the TRD accuracy/speed trade-off table:
 * ESRGAN block count and tile size per tier.
 * RIFE tiers add frame interpolation (rifeScale = 2 or 4).
 */
enum class VideoQualityTier(
    val label: String,
    val esrganBlocks: Int,
    val tileSize: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val rifeScale: Int = 1,
) {
    ORIGINAL("Original", 0, 0, 0, 0, 1),
    ENHANCED_1080P("Enhanced 1080p", 4, 256, 1920, 1080, 1),
    ENHANCED_4K("Enhanced 4K-SR", 6, 320, 3840, 2160, 1),
    SMOOTH_60FPS("Smooth 60fps", 4, 256, 1920, 1080, 2),
    SMOOTH_120FPS("Smooth 120fps", 4, 256, 1920, 1080, 4),
}
