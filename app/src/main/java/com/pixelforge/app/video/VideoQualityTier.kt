package com.pixelforge.app.video

/**
 * Output quality tiers for video enhancement.
 * Maps to the TRD accuracy/speed trade-off table:
 * ESRGAN block count and tile size per tier.
 */
enum class VideoQualityTier(
    val label: String,
    val esrganBlocks: Int,
    val tileSize: Int,
    val targetWidth: Int,
    val targetHeight: Int,
) {
    ORIGINAL("Original", 0, 0, 0, 0),
    ENHANCED_1080P("Enhanced 1080p", 4, 256, 1920, 1080),
    ENHANCED_4K("Enhanced 4K-SR", 6, 320, 3840, 2160),
}
