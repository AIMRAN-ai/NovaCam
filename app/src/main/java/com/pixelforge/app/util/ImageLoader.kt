package com.pixelforge.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance image loader using Glide with optimized settings for photo editing.
 */
class ImageLoader(private val context: Context) {
    
    private val defaultOptions = RequestOptions()
        .format(DecodeFormat.PREFER_ARGB_8888)
        .downsample(DownsampleStrategy.AT_LEAST)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .skipMemoryCache(false)
    
    private val previewOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .downsample(DownsampleStrategy.AT_LEAST)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .override(1024, 1024)
    
    /**
     * Load full-resolution bitmap for editing (async).
     */
    suspend fun loadForEditing(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        Glide.with(context)
            .asBitmap()
            .apply(defaultOptions)
            .load(uri)
            .submit()
            .get()
    }
    
    /**
     * Load downsampled bitmap for preview (async).
     */
    suspend fun loadForPreview(uri: Uri, maxDimension: Int = 1024): Bitmap = withContext(Dispatchers.IO) {
        Glide.with(context)
            .asBitmap()
            .apply(previewOptions.override(maxDimension, maxDimension))
            .load(uri)
            .submit()
            .get()
    }
    
    /**
     * Load thumbnail for gallery/grid (async).
     */
    suspend fun loadThumbnail(uri: Uri, size: Int): Bitmap = withContext(Dispatchers.IO) {
        Glide.with(context)
            .asBitmap()
            .apply(
                RequestOptions()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .override(size, size)
            )
            .load(uri)
            .submit()
            .get()
    }
    
    /**
     * Load into ImageView with placeholder/error handling.
     */
    fun loadIntoView(uri: Uri, imageView: ImageView, placeholder: Int? = null, error: Int? = null) {
        var request = Glide.with(context)
            .load(uri)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholder ?: android.R.color.darker_gray)
            )
        
        error?.let { request = request.error(it) }
        
        request.into(imageView)
    }
    
    /**
     * Preload image into cache (fire and forget).
     */
    fun preload(uri: Uri) {
        Glide.with(context)
            .load(uri)
            .apply(defaultOptions)
            .preload()
    }
    
    /**
     * Clear memory cache.
     */
    fun clearMemoryCache() {
        Glide.get(context).clearMemory()
    }
    
    /**
     * Clear disk cache (async).
     */
    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        Glide.get(context).clearDiskCache()
    }
}

/**
 * Companion for global access.
 */
object ImageLoader {
    private var instance: ImageLoader? = null
    
    fun getInstance(context: Context): ImageLoader {
        return instance ?: ImageLoader(context.applicationContext).also { instance = it }
    }
    
    fun setInstance(loader: ImageLoader) {
        instance = loader
    }
}