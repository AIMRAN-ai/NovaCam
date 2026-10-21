package com.pixelforge.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Histogram view showing RGB and Luminance distributions.
 */
@Composable
fun HistogramView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val histogram = remember(bitmap) {
        if (bitmap != null) computeHistogram(bitmap) else null
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Histogram",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            
            Row(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                // Luminance histogram
                HistogramChannel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(160.dp),
                    histogram = histogram?.luminance,
                    color = Color.White.copy(alpha = 0.8f),
                    label = "L",
                )
                
                // RGB histograms
                Column(modifier = Modifier.fillMaxHeight()) {
                    HistogramChannel(
                        modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 4.dp),
                        histogram = histogram?.red,
                        color = Color.Red.copy(alpha = 0.8f),
                        label = "R",
                    )
                    HistogramChannel(
                        modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 4.dp),
                        histogram = histogram?.green,
                        color = Color.Green.copy(alpha = 0.8f),
                        label = "G",
                    )
                    HistogramChannel(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        histogram = histogram?.blue,
                        color = Color.Blue.copy(alpha = 0.8f),
                        label = "B",
                    )
                }
            }
        }
    }
}

@Composable
private fun HistogramChannel(
    modifier: Modifier,
    histogram: IntArray?,
    color: Color,
    label: String,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        histogram?.let { hist ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxCount = hist.maxOrNull() ?: 1
                val width = size.width
                val height = size.height
                val barWidth = width / 256f
                
                val path = Path()
                path.moveTo(0f, height)
                
                for (i in 0..255) {
                    val count = hist[i].toFloat()
                    val barHeight = (count / maxCount) * height * 0.9f
                    val x = i * barWidth
                    val y = height - barHeight
                    
                    if (i == 0) {
                        path.moveTo(x, height)
                    }
                    path.lineTo(x, y)
                }
                
                path.lineTo(width, height)
                path.close()
                
                drawPath(
                    path,
                    color = color,
                    style = androidx.compose.ui.graphics.Stroke(width = 1f),
                )
            }
        }
        
        // Channel label
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .padding(4.dp)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

private data class HistogramData(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luminance: IntArray,
)

private fun computeHistogram(bitmap: Bitmap): HistogramData {
    val red = IntArray(256)
    val green = IntArray(256)
    val blue = IntArray(256)
    val luminance = IntArray(256)
    
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    
    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        red[r]++
        green[g]++
        blue[b]++
        
        // Luminance using Rec. 709 coefficients
        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
        luminance[lum]++
    }
    
    return HistogramData(red, green, blue, luminance)
}