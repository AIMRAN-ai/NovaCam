package com.pixelforge.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Size
import com.pixelforge.app.diagnostics.Diagnostics

class CameraHalProbe(
    private val context: Context,
    private val diagnostics: Diagnostics,
) {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)

    fun probeRearCamera(): HalCapabilities? {
        val rearId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        return rearId?.let { probe(it) }
    }

    fun probe(cameraId: String): HalCapabilities {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray()

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: IntArray(0)
        val rawSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val level = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "FULL"
        }

        val fpsRanges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower..it.upper }
            ?: emptyList()

        val sensorResolution = rawSizes.maxByOrNull { it.width.toLong() * it.height }
            ?: yuvSizes.maxByOrNull { it.width.toLong() * it.height }
            ?: Size(0, 0)

        val halCapabilities = HalCapabilities(
            cameraId = cameraId,
            sensorResolution = sensorResolution,
            maxJpegSize = jpegSizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(0, 0),
            maxYuvSize = yuvSizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(0, 0),
            rawSupported = rawSupported,
            yuvSupported = yuvSizes.isNotEmpty(),
            hardwareLevel = level,
            fpsRanges = fpsRanges,
        )
        diagnostics.recordHalSummary(halCapabilities.summary)
        return halCapabilities
    }
}
