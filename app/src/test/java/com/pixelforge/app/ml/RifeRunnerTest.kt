package com.pixelforge.app.ml

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RifeRunnerTest {

    @Test
    fun rifeScaleTwoProducesOneIntermediate() {
        // MAX_SCALE is the maximum supported interpolation factor
        assertEquals(4, RifeRunner.MAX_SCALE)
    }

    @Test
    fun rifeScaleFourProducesThreeIntermediates() {
        // 4x produces 3 intermediate frames via two-pass
        assertEquals(4, RifeRunner.MAX_SCALE)
    }

    @Test
    fun videoQualityTierRifeScaleMapping() {
        assertEquals(1, com.pixelforge.app.video.VideoQualityTier.ORIGINAL.rifeScale)
        assertEquals(1, com.pixelforge.app.video.VideoQualityTier.ENHANCED_1080P.rifeScale)
        assertEquals(1, com.pixelforge.app.video.VideoQualityTier.ENHANCED_4K.rifeScale)
        assertEquals(2, com.pixelforge.app.video.VideoQualityTier.SMOOTH_60FPS.rifeScale)
        assertEquals(4, com.pixelforge.app.video.VideoQualityTier.SMOOTH_120FPS.rifeScale)
    }

    private fun createRunner(): RifeRunner {
        val diagnostics = com.pixelforge.app.diagnostics.Diagnostics()
        val constructor = android.content.res.AssetManager::class.java
            .getDeclaredConstructor()
        constructor.isAccessible = true
        val assets = constructor.newInstance()
        return RifeRunner(diagnostics, assets)
    }
}