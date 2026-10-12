package com.pixelforge.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dual-camera controller for simultaneous wide + main capture.
 *
 * Uses Camera2 logical multi-camera API (Android 9+) to capture
 * from both physical cameras simultaneously. Requires
 * REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA.
 *
 * This is a stub implementation - full dual-camera sync requires
 * Camera2 session with multiple outputs and frame synchronization.
 */
class DualCameraController(
    private val context: Context,
    private val diagnostics: Diagnostics,
) {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)

    private var logicalCameraId: String? = null
    private var mainPhysicalId: String? = null
    private var widePhysicalId: String? = null

    var isConfigured: Boolean = false

    data class DualFrame(
        val mainFrame: android.graphics.Bitmap,
        val wideFrame: android.graphics.Bitmap,
        val timestamp: Long,
    )

    fun isDualCameraSupported(): Boolean {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                // Use reflection or string constant for API 29+
                val physicalIds = try {
                    val field = CameraCharacteristics::class.java.getField("LOGICAL_MULTI_CAMERA_PHYSICAL_CAMERA_IDS")
                    field.get(chars) as? List<String> ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                if (physicalIds.size >= 2) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun configure(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Find logical multi-camera
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
                if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                    logicalCameraId = id
                    val physicalIds = try {
                        val field = CameraCharacteristics::class.java.getField("LOGICAL_MULTI_CAMERA_PHYSICAL_CAMERA_IDS")
                        field.get(chars) as? List<String> ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    // Identify main (wide) and ultra-wide
                    for (pid in physicalIds) {
                        val pChars = manager.getCameraCharacteristics(pid)
                        val facing = pChars.get(CameraCharacteristics.LENS_FACING)
                        val focalLength = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f

                        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                            if (focalLength < 24f && widePhysicalId == null) {
                                widePhysicalId = pid
                            } else if (mainPhysicalId == null) {
                                mainPhysicalId = pid
                            }
                        }
                    }

                    if (mainPhysicalId != null && widePhysicalId != null) {
                        isConfigured = true
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            diagnostics.recordVideoError("dual-cam-config: ${e.message}")
            false
        }
    }

    /**
     * Captures a synchronized burst from both cameras.
     * Full implementation requires Camera2 multi-output session.
     * Currently returns null (stub).
     */
    suspend fun captureDualBurst(frameCount: Int): List<DualFrame>? {
        if (!isConfigured) return null
        // TODO: Implement dual-camera burst capture using Camera2
        // Requires: createCaptureSession with multiple outputs, synchronized capture
        return null
    }

    fun shutdown() {
        isConfigured = false
        logicalCameraId = null
        mainPhysicalId = null
        widePhysicalId = null
    }
}