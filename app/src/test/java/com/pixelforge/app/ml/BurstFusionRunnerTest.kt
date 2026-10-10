package com.pixelforge.app.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class BurstFusionRunnerTest {

    @Test
    fun fusionDefaultsToFiveFramesMax() {
        val runner = createRunner()
        val field = BurstFusionRunner::class.java.getDeclaredField("maxFrames")
        field.isAccessible = true
        assertEquals(5, field.get(runner))
    }

    @Test
    fun fusionModelNameIsStable() {
        val runner = createRunner()
        assertEquals("BurstFusion-Net", runner.modelName)
    }

    @Test
    fun fusionReportsFallbackDelegateBeforeInit() {
        val runner = createRunner()
        assertEquals("not-initialized", runner.delegatePath)
    }

    private fun createRunner(): BurstFusionRunner {
        val diagnostics = com.pixelforge.app.diagnostics.Diagnostics()
        val constructor = android.content.res.AssetManager::class.java
            .getDeclaredConstructor()
        constructor.isAccessible = true
        val assets = constructor.newInstance()
        return BurstFusionRunner(diagnostics, assets)
    }
}
