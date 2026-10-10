package com.pixelforge.app.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

object YuvConverter {

    fun toNv21(image: Image): ByteArray {
        require(image.format == ImageFormat.YUV_420_888) { "Expected YUV_420_888, got ${image.format}" }

        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val nv21 = ByteArray(ySize + ySize / 2)
        yBuffer.get(nv21, 0, ySize)

        val vBytes = ByteArray(vBuffer.remaining())
        vBuffer.get(vBytes)
        val uBytes = ByteArray(uBuffer.remaining())
        uBuffer.get(uBytes)

        val uvWidth = width / 2
        val uvHeight = height / 2
        val vPixelStride = vPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val uRowStride = uPlane.rowStride

        var uvIndex = ySize
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[uvIndex++] = vBytes[vIndex]
                nv21[uvIndex++] = uBytes[uIndex]
            }
        }
        return nv21
    }

    fun toJpegBytes(image: Image, quality: Int): ByteArray {
        val nv21 = toNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        return ByteArrayOutputStream().use { stream ->
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                quality,
                stream
            )
            stream.toByteArray()
        }
    }
}
