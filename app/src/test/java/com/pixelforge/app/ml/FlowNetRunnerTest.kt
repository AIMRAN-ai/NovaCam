package com.pixelforge.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowNetRunnerTest {

    @Test
    fun fftRoundTripIsIdentity() {
        val runner = createRunner()
        val fft2d = FlowNetRunner::class.java.getDeclaredMethod(
            "fft2d", DoubleArray::class.java
        )
        fft2d.isAccessible = true
        val ifftParamType = arrayOf(arrayOf(doubleArrayOf(1.0)))::class.java
        val ifft = FlowNetRunner::class.java.getDeclaredMethod(
            "ifft2d", ifftParamType
        )
        ifft.isAccessible = true

        val size = 64
        val image = DoubleArray(size * size) { i ->
            Math.sin(2.0 * Math.PI * (i / size) / 8) *
                Math.cos(2.0 * Math.PI * (i % size) / 16)
        }
        val spectrum = fft2d.invoke(runner, image) as Array<Array<DoubleArray>>
        val restored = ifft.invoke(runner, spectrum) as Array<DoubleArray>
        for (y in 0 until size) {
            for (x in 0 until size) {
                assertEquals(image[y * size + x], restored[y][x], 1e-6)
            }
        }
    }

    @Test
    fun fftOfConstantIsDeltaAtZero() {
        val runner = createRunner()
        val fft = FlowNetRunner::class.java.getDeclaredMethod(
            "fft", DoubleArray::class.java
        )
        fft.isAccessible = true
        val input = DoubleArray(64) { 1.0 }
        val spectrum = fft.invoke(runner, input) as Array<DoubleArray>
        assertEquals(64.0, spectrum[0][0], 1e-6)
        for (i in 1 until 64) {
            assertEquals(0.0, spectrum[i][0], 1e-6)
            assertEquals(0.0, spectrum[i][1], 1e-6)
        }
    }

    @Test
    fun motionVectorCarriesSource() {
        val motion = MotionVector(1.5f, -2.5f, "model")
        assertEquals(1.5f, motion.dx, 0.001f)
        assertEquals(-2.5f, motion.dy, 0.001f)
        assertTrue(motion.source.isNotEmpty())
    }

    @Test
    fun maxMotionBudgetIsEightPx() {
        assertEquals(8, FlowNetRunner.MAX_MOTION_PX)
    }

    private fun createRunner(): FlowNetRunner {
        val diagnostics = com.pixelforge.app.diagnostics.Diagnostics()
        val constructor = android.content.res.AssetManager::class.java
            .getDeclaredConstructor()
        constructor.isAccessible = true
        val assets = constructor.newInstance()
        return FlowNetRunner(diagnostics, assets)
    }
}
