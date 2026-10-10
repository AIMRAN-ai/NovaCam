package com.pixelforge.app.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class BurstPipelineTest {

    @Test
    fun toneMapAppliesSCurve() {
        val pipeline = createPipeline()
        val method = BurstPipeline::class.java.getDeclaredMethod(
            "toneChannel", Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        val black = method.invoke(pipeline, 0) as Int
        val mid = method.invoke(pipeline, 127) as Int
        val white = method.invoke(pipeline, 255) as Int
        assertEquals(0, black)
        assertEquals(255, white)
        assert(mid in 110..145) { "mid-tone should stay near 127, was $mid" }
    }

    @Test
    fun qualityTierLabelsAreStable() {
        assertEquals("Fast", QualityTier.FAST.label)
        assertEquals("Balanced", QualityTier.BALANCED.label)
        assertEquals("Quality", QualityTier.QUALITY.label)
    }

    @Test
    fun pipelineResultCarriesStageTimings() {
        val timings = mapOf("align" to 12L, "fuse" to 34L)
        val result = PipelineResult(
            fusedBitmap = null,
            upscaledBitmap = null,
            scale = 2,
            tier = QualityTier.FAST,
            stageTimingsMs = timings,
            motionSources = listOf("reference", "phase-correlation"),
        )
        assertEquals(2, result.scale)
        assertEquals(46L, result.stageTimingsMs.values.sum())
        assertEquals(2, result.motionSources.size)
    }

    private fun createPipeline(): BurstPipeline {
        val diagnostics = com.pixelforge.app.diagnostics.Diagnostics()
        val constructor = android.content.res.AssetManager::class.java
            .getDeclaredConstructor()
        constructor.isAccessible = true
        val assets = constructor.newInstance()
        return BurstPipeline(
            diagnostics = diagnostics,
            flowNet = com.pixelforge.app.ml.FlowNetRunner(diagnostics, assets),
            fusion = com.pixelforge.app.ml.BurstFusionRunner(diagnostics, assets),
            esrgan = com.pixelforge.app.ml.EsrganLiteRunner(diagnostics, assets),
        )
    }
}
