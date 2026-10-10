package com.pixelforge.app.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreSaver(private val context: Context) {

    suspend fun saveJpeg(bytes: ByteArray, displayName: String): Uri = withContext(Dispatchers.IO) {
        saveToMediaStore(bytes, displayName, "image/jpeg")
    }

    suspend fun saveRaw(dngBytes: ByteArray, displayName: String): Uri = withContext(Dispatchers.IO) {
        saveToMediaStore(dngBytes, displayName, "image/dng")
    }

    private fun saveToMediaStore(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/PixelForge")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("MediaStore output stream failed")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
