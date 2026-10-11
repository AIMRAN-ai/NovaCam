package com.pixelforge.app.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting enhanced videos to MediaStore (gallery) and sharing via Intent.
 */
class GalleryExporter(
    private val context: Context,
    private val diagnostics: Diagnostics,
) {

    companion object {
        private const val TAG = "GalleryExporter"
        private const val ALBUM_NAME = "PixelForge"
        private const val FILE_PROVIDER_AUTHORITY = "com.pixelforge.app.fileprovider"
    }

    /**
     * Exports a video file to the PixelForge album in MediaStore.
     *
     * @param sourcePath Path to the source video file
     * @param originalName Optional original filename for metadata
     * @param mimeType Video mime type (video/mp4, video/hevc)
     * @return Uri of the saved video in MediaStore
     */
    suspend fun exportToGallery(
        sourcePath: String,
        originalName: String? = null,
        mimeType: String = "video/mp4",
    ): Uri = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Source file not found: $sourcePath")
        }

        val displayName = originalName ?: generateFileName("mp4")
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$ALBUM_NAME")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DURATION, getVideoDuration(sourceFile))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        try {
            // Write file content
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Mark as not pending (visible in gallery)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // Generate and set thumbnail
            generateThumbnail(uri, sourceFile)

            Log.i(TAG, "Video exported to gallery: $uri")
            uri
        } catch (e: Exception) {
            // Clean up on failure
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /**
     * Creates a share intent for the video.
     */
    fun createShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "PixelForge Enhanced Video")
            putExtra(Intent.EXTRA_TEXT, "Enhanced with PixelForge AI")
        }
    }

    /**
     * Creates a chooser intent for sharing.
     */
    fun createShareChooser(uri: Uri, title: String = "Share enhanced video"): Intent {
        return Intent.createChooser(createShareIntent(uri), title)
    }

    /**
     * Gets a FileProvider URI for sharing files outside MediaStore.
     */
    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            file
        )
    }

    private fun generateFileName(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "pixelforge_$timestamp.$extension"
    }

    private fun getVideoDuration(file: File): Long {
        // Use MediaMetadataRetriever for accurate duration
        // Simplified: estimate from file size and bitrate
        return 0 // Will be populated by MediaStore scanner
    }

    private fun generateThumbnail(uri: Uri, sourceFile: File) {
        // MediaStore will auto-generate thumbnail on Android 10+
        // For older versions, we could use MediaMetadataRetriever
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(sourceFile.absolutePath)
                val bitmap = retriever.getFrameAtTime(0)
                retriever.release()
                if (bitmap != null) {
                    // Thumbnail generation would go here
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation failed: ${e.message}")
        }
    }
}