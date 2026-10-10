package com.pixelforge.app.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class AspectRatioTest {

    @Test
    fun crop9x16Of4x3SourceKeepsFullHeight() {
        val window = AspectRatio.RATIO_9_16.cropWindow(4032, 3024)
        assertEquals(3024, window.height)
        assertEquals(1701, window.width)
        assertEquals(1165, window.left)
        assertEquals(0, window.top)
    }

    @Test
    fun crop16x9OfTallSourceKeepsFullWidth() {
        val window = AspectRatio.RATIO_16_9.cropWindow(3024, 4032)
        assertEquals(3024, window.width)
        assertEquals(1701, window.height)
        assertEquals(0, window.left)
        assertEquals(1165, window.top)
    }

    @Test
    fun crop1x1IsCenteredSquare() {
        val window = AspectRatio.RATIO_1_1.cropWindow(4032, 3024)
        assertEquals(3024, window.width)
        assertEquals(3024, window.height)
        assertEquals(504, window.left)
        assertEquals(0, window.top)
    }

    @Test
    fun cropWindowStaysWithinSourceBounds() {
        val window = AspectRatio.RATIO_9_16.cropWindow(1920, 1080)
        assert(window.left >= 0)
        assert(window.top >= 0)
        assert(window.right <= 1920)
        assert(window.bottom <= 1080)
    }
}
