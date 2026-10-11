package com.pixelforge.app.video

import com.pixelforge.app.ml.SrModelRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VideoUpscalePipelineTest {

    @Test
    fun configCreation() {
        val exportConfig = VideoExportConfig(VideoQualityTier.ENHANCED_1080P, useHevc = true)
        val config = VideoUpscalePipeline.Config(
            inputPath = "/test/input.mp4",
            outputPath = "/test/output.mp4",
            exportConfig = exportConfig,
        )
        assertEquals("/test/input.mp4", config.inputPath)
        assertEquals("/test/output.mp4", config.outputPath)
        assertEquals(VideoQualityTier.ENHANCED_1080P, config.exportConfig.tier)
        assertEquals(true, config.exportConfig.useHevc)
        assertEquals(30, config.exportConfig.effectiveFrameRate)
    }

    @Test
    fun resultFields() {
        val result = VideoUpscalePipeline.Result(
            outputPath = "/test/output.mp4",
            framesProcessed = 1800,
            totalFrames = 1800,
            throughputFps = 15.5,
            outputSizeBytes = 50 * 1024 * 1024L,
            tier = VideoQualityTier.SMOOTH_60FPS,
        )
        assertEquals("/test/output.mp4", result.outputPath)
        assertEquals(1800, result.framesProcessed)
        assertEquals(1800, result.totalFrames)
        assertEquals(15.5, result.throughputFps, 0.01)
        assertEquals(50 * 1024 * 1024L, result.outputSizeBytes)
        assertEquals(VideoQualityTier.SMOOTH_60FPS, result.tier)
    }

    @Test
    fun tierRifeScaleUsedInTotalFrames() {
        val probeFrames = 900L // 30s @ 30fps
        val tiers = listOf(
            VideoQualityTier.ORIGINAL to 1,
            VideoQualityTier.ENHANCED_1080P to 1,
            VideoQualityTier.ENHANCED_4K to 1,
            VideoQualityTier.SMOOTH_60FPS to 2,
            VideoQualityTier.SMOOTH_120FPS to 4,
        )
        for ((tier, scale) in tiers) {
            val expectedTotal = probeFrames * scale
            assertEquals(expectedTotal, probeFrames * tier.rifeScale.toLong())
        }
    }

    @Test
    fun tierTargetResolution() {
        assertEquals(1920, VideoQualityTier.ENHANCED_1080P.targetWidth)
        assertEquals(1080, VideoQualityTier.ENHANCED_1080P.targetHeight)
        assertEquals(3840, VideoQualityTier.ENHANCED_4K.targetWidth)
        assertEquals(2160, VideoQualityTier.ENHANCED_4K.targetHeight)
        assertEquals(1920, VideoQualityTier.SMOOTH_60FPS.targetWidth)
        assertEquals(1080, VideoQualityTier.SMOOTH_60FPS.targetHeight)
    }

    @Test
    fun videoPipelineExceptionCreation() {
        val cause = Exception("root cause")
        val exception = VideoPipelineException("pipeline failed", cause)
        assertEquals("pipeline failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun thermalGuardShouldPause() {
        // Just verify the class can be instantiated
        val context = org.robolectric.RuntimeEnvironment.application
        if (context == null) return // Skip if no application context
        val guard = ThermalGuard(context)
        // On test environment, thermal status is not severe
        assertEquals(false, guard.shouldPause())
    }
}