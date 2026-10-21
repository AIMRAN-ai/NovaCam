package com.pixelforge.app.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

object AutoEnhance {
    private const val SAMPLE_SIZE = 256
    
    suspend fun analyzeAndEnhance(context: Context, uri: Uri): PhotoAdjustments = withContext(Dispatchers.IO) {
        val bitmap = loadSampleBitmap(context, uri)
        if (bitmap == null) return@withContext PhotoAdjustments()
        
        val histogram = computeHistogram(bitmap)
        val stats = analyzeHistogram(histogram, bitmap.width * bitmap.height)
        
        PhotoAdjustments(
            exposure = computeExposureAdjustment(stats),
            ev = 0f,
            brilliance = computeBrillianceAdjustment(stats),
            vignette = 0f,
            noiseReduction = 0f,
            definition = computeDefinitionAdjustment(stats),
            sharpness = computeSharpnessAdjustment(stats),
            tint = 0f,
            warmth = computeWarmthAdjustment(stats),
            vibrance = computeVibranceAdjustment(stats),
            blackPoint = computeBlackPointAdjustment(stats),
            brightness = computeBrightnessAdjustment(stats),
            contrast = computeContrastAdjustment(stats),
            shadows = computeShadowsAdjustment(stats),
            highlights = computeHighlightsAdjustment(stats),
        ).clamped()
    }
    
    private fun loadSampleBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                
                var sampleSize = 1
                while (options.outWidth / sampleSize > SAMPLE_SIZE || options.outHeight / sampleSize > SAMPLE_SIZE) {
                    sampleSize *= 2
                }
                
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                
                val stream2 = context.contentResolver.openInputStream(uri)
                stream2?.use { s ->
                    BitmapFactory.decodeStream(s, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun computeHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256 * 3)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            histogram[r]++
            histogram[256 + g]++
            histogram[512 + b]++
        }
        return histogram
    }
    
    private data class HistogramStats(
        val mean: Float,
        val stdDev: Float,
        val minVal: Int,
        val maxVal: Int,
        val shadowRatio: Float,
        val highlightRatio: Float,
        val midtoneContrast: Float,
        val colorBalance: FloatArray,
    )
    
    private fun analyzeHistogram(histogram: IntArray, totalPixels: Int): HistogramStats {
        var sum = 0L
        var count = 0
        var minVal = 255
        var maxVal = 0
        var shadowCount = 0
        var highlightCount = 0
        
        for (i in 0..255) {
            val r = histogram[i]
            val g = histogram[256 + i]
            val b = histogram[512 + i]
            val total = r + g + b
            
            if (total > 0) {
                minVal = minOf(minVal, i)
                maxVal = maxOf(maxVal, i)
            }
            
            sum += (i.toLong() * total)
            count += total
            
            if (i < 64) shadowCount += total
            if (i > 192) highlightCount += total
        }
        
        val mean = if (count > 0) sum.toFloat() / count else 128f
        
        var variance = 0f
        for (i in 0..255) {
            val total = histogram[i] + histogram[256 + i] + histogram[512 + i]
            val diff = i - mean
            variance += (diff * diff * total).toFloat()
        }
        val stdDev = if (count > 0) kotlin.math.sqrt(variance / count) else 0f
        
        val shadowRatio = shadowCount.toFloat() / totalPixels
        val highlightRatio = highlightCount.toFloat() / totalPixels
        
        var midtoneSum = 0L
        var midtoneCount = 0
        for (i in 64..192) {
            val total = histogram[i] + histogram[256 + i] + histogram[512 + i]
            midtoneSum += (i.toLong() * total)
            midtoneCount += total
        }
        val midtoneMean = if (midtoneCount > 0) midtoneSum.toFloat() / midtoneCount else 128f
        val midtoneContrast = abs(midtoneMean - 128f) / 128f
        
        val colorBalance = FloatArray(3)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        for (i in 0..255) {
            totalR += (i.toLong() * histogram[i])
            totalG += (i.toLong() * histogram[256 + i])
            totalB += (i.toLong() * histogram[512 + i])
        }
        val meanR = if (count > 0) totalR.toFloat() / count else 128f
        val meanG = if (count > 0) totalG.toFloat() / count else 128f
        val meanB = if (count > 0) totalB.toFloat() / count else 128f
        colorBalance[0] = meanR
        colorBalance[1] = meanG
        colorBalance[2] = meanB
        
        return HistogramStats(
            mean = mean,
            stdDev = stdDev,
            minVal = minVal,
            maxVal = maxVal,
            shadowRatio = shadowRatio,
            highlightRatio = highlightRatio,
            midtoneContrast = midtoneContrast,
            colorBalance = colorBalance,
        )
    }
    
    private fun computeExposureAdjustment(stats: HistogramStats): Float {
        val targetMean = 110f
        val diff = targetMean - stats.mean
        return (diff / 2.5f).coerceIn(-100f, 100f)
    }
    
    private fun computeBrillianceAdjustment(stats: HistogramStats): Float {
        if (stats.midtoneContrast < 0.3f) return 30f
        if (stats.midtoneContrast > 0.7f) return -20f
        return 0f
    }
    
    private fun computeDefinitionAdjustment(stats: HistogramStats): Float {
        if (stats.stdDev < 30f) return 25f
        if (stats.stdDev > 60f) return -15f
        return 0f
    }
    
    private fun computeSharpnessAdjustment(stats: HistogramStats): Float {
        if (stats.stdDev < 25f) return 20f
        return 0f
    }
    
    private fun computeWarmthAdjustment(stats: HistogramStats): Float {
        val r = stats.colorBalance[0]
        val b = stats.colorBalance[2]
        val diff = r - b
        return (-diff / 2f).coerceIn(-100f, 100f)
    }
    
    private fun computeVibranceAdjustment(stats: HistogramStats): Float {
        val saturation = sqrt(
            (stats.colorBalance[0] - stats.mean).pow(2) +
            (stats.colorBalance[1] - stats.mean).pow(2) +
            (stats.colorBalance[2] - stats.mean).pow(2)
        ) / stats.mean
        if (saturation < 0.1f) return 20f
        if (saturation > 0.4f) return -15f
        return 0f
    }
    
    private fun computeBlackPointAdjustment(stats: HistogramStats): Float {
        if (stats.minVal > 10) return -stats.minVal.toFloat()
        return 0f
    }
    
    private fun computeBrightnessAdjustment(stats: HistogramStats): Float {
        val targetMean = 115f
        val diff = targetMean - stats.mean
        return (diff / 3f).coerceIn(-100f, 100f)
    }
    
    private fun computeContrastAdjustment(stats: HistogramStats): Float {
        if (stats.stdDev < 35f) return 20f
        if (stats.stdDev > 55f) return -15f
        return 0f
    }
    
    private fun computeShadowsAdjustment(stats: HistogramStats): Float {
        if (stats.shadowRatio > 0.4f) return -30f
        if (stats.shadowRatio < 0.1f) return 25f
        return 0f
    }
    
    private fun computeHighlightsAdjustment(stats: HistogramStats): Float {
        if (stats.highlightRatio > 0.2f) return -30f
        if (stats.highlightRatio < 0.05f) return 15f
        return 0f
    }
}