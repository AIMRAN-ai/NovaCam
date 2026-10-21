package com.pixelforge.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Bitmap pool for efficient memory management on budget devices.
 * Reuses Bitmap objects to reduce GC pressure and allocation overhead.
 */
class BitmapPool(
    private val maxSize: Int = 20,  // Max bitmaps to keep in pool
    private val maxBytes: Long = 50 * 1024 * 1024,  // 50MB max
) {
    private val pool = ConcurrentLinkedQueue<Bitmap>()
    private var currentBytes = 0L
    private val lock = Any()

    /**
     * Get a bitmap from the pool matching the required config and size.
     * Returns null if no suitable bitmap available.
     */
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val requiredBytes = width * height * bytesPerPixel(config).toLong()
        
        synchronized(lock) {
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (bitmap.width >= width && bitmap.height >= height && bitmap.config == config) {
                    if (bitmap.isMutable) {
                        iterator.remove()
                        currentBytes -= bitmap.byteCount.toLong()
                        bitmap.reconfigure(width, height, config)
                        return bitmap
                    }
                }
            }
        }
        return null
    }

    /**
     * Return a bitmap to the pool for reuse.
     */
    fun put(bitmap: Bitmap) {
        if (!bitmap.isMutable || bitmap.isRecycled) return
        
        val bytes = bitmap.byteCount.toLong()
        if (bytes > maxBytes) return
        
        synchronized(lock) {
            // Evict old bitmaps if needed
            while ((pool.size >= maxSize || currentBytes + bytes > maxBytes) && pool.isNotEmpty()) {
                val oldest = pool.poll()
                oldest?.let { currentBytes -= it.byteCount.toLong() }
            }
            
            pool.offer(bitmap)
            currentBytes += bytes
        }
    }

    /**
     * Clear the pool and recycle all bitmaps.
     */
    fun clear() {
        synchronized(lock) {
            pool.forEach { it.recycle() }
            pool.clear()
            currentBytes = 0
        }
    }

    /**
     * Get current pool stats for diagnostics.
     */
    fun getStats(): PoolStats = synchronized(lock) {
        PoolStats(pool.size, currentBytes, maxBytes)
    }

    private fun bytesPerPixel(config: Bitmap.Config): Int = when (config) {
        Bitmap.Config.ARGB_8888 -> 4
        Bitmap.Config.RGB_565 -> 2
        Bitmap.Config.ARGB_4444 -> 2
        Bitmap.Config.ALPHA_8 -> 1
        Bitmap.Config.RGBA_F16 -> 8
        Bitmap.Config.HARDWARE -> 4
        else -> 4
    }

    data class PoolStats(
        val count: Int,
        val currentBytes: Long,
        val maxBytes: Long,
    )
}

/**
 * Companion object for global pool access.
 */
object BitmapPool {
    private var instance: BitmapPool? = null
    
    fun getInstance(): BitmapPool {
        return instance ?: BitmapPool().also { instance = it }
    }
    
    fun setInstance(pool: BitmapPool) {
        instance = pool
    }
}