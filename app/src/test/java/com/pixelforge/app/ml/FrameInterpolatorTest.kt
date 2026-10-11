package com.pixelforge.app.ml

import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.video.MediaCodecFrameEncoder
import com.pixelforge.app.video.MediaCodecFrameSource
import com.pixelforge.app.video.VideoQualityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameInterpolatorTest {

    @Test
    fun interpolationConfigScaleTwo() {
        val config = FrameInterpolator.Config(
            scale = 2,
            doUpscale = false,
            targetWidth = 1920,
            targetHeight = 1080,
            useHevc = false,
            frameRate = 30,
        )
        assertEquals(2, config.scale)
        assertEquals(30, config.frameRate)
    }

    @Test
    fun interpolationConfigScaleFour() {
        val config = FrameInterpolator.Config(
            scale = 4,
            doUpscale = true,
            targetWidth = 1920,
            targetHeight = 1080,
            useHevc = true,
            frameRate = 30,
        )
        assertEquals(4, config.scale)
        assertEquals(30, config.frameRate)
    }

    @Test
    fun tierRifeScaleMapping() {
        assertEquals(1, VideoQualityTier.ORIGINAL.rifeScale)
        assertEquals(1, VideoQualityTier.ENHANCED_1080P.rifeScale)
        assertEquals(1, VideoQualityTier.ENHANCED_4K.rifeScale)
        assertEquals(2, VideoQualityTier.SMOOTH_60FPS.rifeScale)
        assertEquals(4, VideoQualityTier.SMOOTH_120FPS.rifeScale)
    }

    @Test
    fun tierTargetResolution() {
        assertEquals(1920, VideoQualityTier.SMOOTH_60FPS.targetWidth)
        assertEquals(1080, VideoQualityTier.SMOOTH_60FPS.targetHeight)
        assertEquals(1920, VideoQualityTier.SMOOTH_120FPS.targetWidth)
        assertEquals(1080, VideoQualityTier.SMOOTH_120FPS.targetHeight)
    }

    @Test
    fun encoderFrameRateCalculation() {
        val baseFrameRate = 30
        assertEquals(30, baseFrameRate * VideoQualityTier.ORIGINAL.rifeScale)
        assertEquals(30, baseFrameRate * VideoQualityTier.ENHANCED_1080P.rifeScale)
        assertEquals(60, baseFrameRate * VideoQualityTier.SMOOTH_60FPS.rifeScale)
        assertEquals(120, baseFrameRate * VideoQualityTier.SMOOTH_120FPS.rifeScale)
    }

    @Test
    fun frameInterpolatorResultFields() {
        val result = FrameInterpolator.Result(
            framesEncoded = 100,
            outputSizeBytes = 1024 * 1024L,
        )
        assertEquals(100, result.framesEncoded)
        assertEquals(1024 * 1024L, result.outputSizeBytes)
    }
}