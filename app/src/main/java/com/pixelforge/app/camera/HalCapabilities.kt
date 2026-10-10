package com.pixelforge.app.camera

import android.util.Size

data class HalCapabilities(
    val cameraId: String,
    val sensorResolution: Size,
    val maxJpegSize: Size,
    val maxYuvSize: Size,
    val rawSupported: Boolean,
    val yuvSupported: Boolean,
    val hardwareLevel: String,
    val fpsRanges: List<IntRange>,
) {
    val summary: String
        get() = buildString {
            append("camera=$cameraId ")
            append("sensor=${sensorResolution.width}x${sensorResolution.height} ")
            append("jpeg=${maxJpegSize.width}x${maxJpegSize.height} ")
            append("yuv=${maxYuvSize.width}x${maxYuvSize.height} ")
            append("raw=$rawSupported ")
            append("level=$hardwareLevel ")
            append("fps=")
            fpsRanges.forEach { append("[${it.first}-${it.last}] ") }
        }
}
