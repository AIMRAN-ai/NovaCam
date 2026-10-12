package com.pixelforge.app.camera

import android.content.Context
import android.content.ContentValues
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.io.MediaStoreSaver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * RAW video controller for 12-bit DNG sequence capture.
 *
 * Uses Camera2 RAW_SENSOR output via ImageReader to capture
 * uncompressed RAW frames. Saves as DNG sequence in MediaStore.
 * Storage intensive (~150 MB/s at 4K30) - user must opt-in,
 * max 10s clips, requires >2 GB free space.
 */
class RawVideoController(
    private val context: Context,
    private val diagnostics: Diagnostics,
    private val saver: MediaStoreSaver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var isRecording = false
    private var frameCount = 0
    private var startTime = 0L
    private val maxFrames = 300 // 10s @ 30fps
    private val outputDir: File? = null

    data class RawFrame(
        val dngBytes: ByteArray,
        val timestamp: Long,
        val frameNumber: Int,
    )

    private val frameChannel = Channel<RawFrame>(capacity = 10)

    fun isRawVideoSupported(): Boolean {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                val rawSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                if (rawSizes != null && rawSizes.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun configure(): Boolean = withContext(dispatcher) {
        return@withContext try {
            // Find RAW-capable rear camera
            for (id in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
                    if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                        cameraId = id
                        break
                    }
                }
            }

            if (cameraId == null) return@withContext false

            // Check storage space
            val freeBytes = getFreeStorageBytes()
            if (freeBytes < 2_000_000_000L) { // 2 GB minimum
                diagnostics.recordVideoError("raw-video: insufficient storage")
                return@withContext false
            }

            // Create output directory
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), "PixelForgeRAW")
            dir.mkdirs()

            // Open camera
            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }
            manager.openCamera(cameraId!!, stateCallback, handler)

            // Create ImageReader for RAW
            val chars = manager.getCameraCharacteristics(cameraId!!)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray()
            val rawSize = rawSizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(0, 0)

            imageReader = ImageReader.newInstance(
                rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, maxFrames + 2
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let { processRawImage(it) }
            }, handler)

            return@withContext true
        } catch (e: Exception) {
            diagnostics.recordVideoError("raw-video-config: ${e.message}")
            false
        }
    }

    private fun processRawImage(image: Image) {
        if (!isRecording || frameCount >= maxFrames) {
            image.close()
            return
        }

        val planes = image.planes
        val buffer = planes[0].buffer
        val rowStride = planes[0].rowStride
        val pixelStride = planes[0].pixelStride

        val width = image.width
        val height = image.height
        val size = buffer.remaining()

        val data = ByteArray(size)
        buffer.get(data)

        // Write DNG
        val dngBytes = writeDng(data, width, height, rowStride, pixelStride)

        val frame = RawFrame(
            dngBytes = dngBytes,
            timestamp = System.currentTimeMillis(),
            frameNumber = frameCount,
        )
        frameCount++

        val result = frameChannel.trySend(frame)
        if (result.isClosed) {
            // Channel closed, stop recording
        }

        image.close()
    }

    private fun writeDng(
        rawData: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): ByteArray {
        // Simplified DNG writer - in production use Adobe DNG SDK or similar
        // For now, wrap raw data with minimal DNG header
        val output = java.io.ByteArrayOutputStream()

        // TIFF header (DNG is based on TIFF/EP)
        output.write("II".toByteArray()) // Little-endian
        output.write(shortToBytes(42)) // TIFF magic
        output.write(intToBytes(8)) // Offset to first IFD

        // IFD entry count (simplified)
        output.write(shortToBytes(10))

        // Minimal DNG tags
        writeTag(output, 256, 4, 1, width) // ImageWidth
        writeTag(output, 257, 4, 1, height) // ImageLength
        writeTag(output, 258, 3, 1, 12) // BitsPerSample (12-bit)
        writeTag(output, 259, 3, 1, 1) // Compression = none
        writeTag(output, 262, 3, 1, 32803) // PhotometricInterpretation = CFA
        writeTag(output, 273, 4, 1, output.size() + 10 + 4) // StripOffsets (after IFD)
        writeTag(output, 277, 3, 1, 1) // SamplesPerPixel
        writeTag(output, 278, 4, 1, height) // RowsPerStrip
        writeTag(output, 279, 4, 1, rawData.size) // StripByteCounts
        output.write(intToBytes(0)) // Next IFD

        // Raw data
        output.write(rawData)

        return output.toByteArray()
    }

    private fun writeTag(out: java.io.ByteArrayOutputStream, tag: Int, type: Int, count: Int, value: Int) {
        out.write(shortToBytes(tag))
        out.write(shortToBytes(type))
        out.write(intToBytes(count))
        out.write(intToBytes(value))
    }

    private fun shortToBytes(value: Int): ByteArray = ByteArray(2).apply {
        this[0] = (value and 0xFF).toByte()
        this[1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun intToBytes(value: Int): ByteArray = ByteArray(4).apply {
        this[0] = (value and 0xFF).toByte()
        this[1] = ((value shr 8) and 0xFF).toByte()
        this[2] = ((value shr 16) and 0xFF).toByte()
        this[3] = ((value shr 24) and 0xFF).toByte()
    }

    suspend fun startRecording(): Boolean = withContext(dispatcher) {
        if (cameraDevice == null || imageReader == null) return@withContext false

        // Create capture session with RAW output
        val rawSurface = imageReader!!.surface
        val outputConfig = OutputConfiguration(rawSurface)

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(outputConfig),
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    this@RawVideoController.session = session
                    startRepeating()
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    diagnostics.recordVideoError("raw-video-session-failed")
                }
            }
        )

        try {
            cameraDevice!!.createCaptureSession(sessionConfig)
        } catch (e: CameraAccessException) {
            diagnostics.recordVideoError("raw-video-session-create: ${e.message}")
            return@withContext false
        }

        isRecording = true
        frameCount = 0
        startTime = System.currentTimeMillis()
        return@withContext true
    }

    private fun startRepeating() {
        val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        requestBuilder.addTarget(imageReader!!.surface)
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)

        try {
            session!!.setRepeatingRequest(requestBuilder.build(), null, handler)
        } catch (e: CameraAccessException) {
            diagnostics.recordVideoError("raw-video-repeat: ${e.message}")
        }
    }

    suspend fun stopRecording(): List<Uri> = withContext(dispatcher) {
        isRecording = false
        session?.stopRepeating()
        session?.close()
        session = null

        // Close ImageReader
        imageReader?.close()
        imageReader = null

        // Collect frames from channel and save
        val uris = mutableListOf<Uri>()
        for (frame in frameChannel) {
            val fileName = "raw_${String.format("%04d", frame.frameNumber)}.dng"
            val uri = saver.saveRaw(frame.dngBytes, fileName)
            uri?.let { uris.add(it) }
        }

        // Close camera
        cameraDevice?.close()
        cameraDevice = null
        executor.shutdown()

        diagnostics.recordVideoStats(
            framesProcessed = frameCount,
            totalFrames = frameCount.toLong(),
            throughputFps = frameCount.toDouble() / ((System.currentTimeMillis() - startTime) / 1000.0),
            encodeMs = 0,
            outputSizeBytes = uris.size.toLong() * 50_000_000L, // Estimate
            tier = "RAW_VIDEO",
            stageTimingsMs = mapOf(),
            delegateUsed = "Camera2",
            thermalDegradation = com.pixelforge.app.diagnostics.ThermalDegradation.NONE,
        )

        uris
    }

    private fun getFreeStorageBytes(): Long {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun shutdown() {
        isRecording = false
        session?.close()
        session = null
        imageReader?.close()
        imageReader = null
        cameraDevice?.close()
        cameraDevice = null
        executor.shutdown()
    }
}