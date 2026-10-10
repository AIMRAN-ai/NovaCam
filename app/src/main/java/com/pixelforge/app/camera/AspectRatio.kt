package com.pixelforge.app.camera

import android.graphics.Rect

data class CropWindow(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

enum class AspectRatio(
    val label: String,
    private val width: Float,
    private val height: Float,
) {
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_1_1("1:1", 1f, 1f);

    fun cropWindow(sourceWidth: Int, sourceHeight: Int): CropWindow {
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetAspect = width / height
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = sourceHeight
            cropWidth = (sourceHeight * targetAspect).toInt()
        } else {
            cropWidth = sourceWidth
            cropHeight = (sourceWidth / targetAspect).toInt()
        }
        val left = (sourceWidth - cropWidth) / 2
        val top = (sourceHeight - cropHeight) / 2
        return CropWindow(left, top, cropWidth, cropHeight)
    }

    fun cropRect(sourceWidth: Int, sourceHeight: Int): Rect {
        val window = cropWindow(sourceWidth, sourceHeight)
        return Rect(window.left, window.top, window.right, window.bottom)
    }
}
