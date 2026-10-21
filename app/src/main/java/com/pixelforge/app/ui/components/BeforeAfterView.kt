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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.dragDetectedGestures
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelforge.app.R
import kotlinx.coroutines.launch

/**
 * Before/After comparison view with draggable split slider.
 */
@Composable
fun BeforeAfterView(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var splitPosition by remember { mutableStateOf(0.5f) }
    var isDragging by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Original image (left side)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                originalBitmap?.let { bitmap ->
                    drawImageRect(
                        image = bitmap.asImageBitmap(),
                        src = Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                        dst = Rect(0f, 0f, size.width * splitPosition, size.height),
                    )
                }
            }
        }
        
        // Edited image (right side)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    translationX = size.width * splitPosition
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                editedBitmap?.let { bitmap ->
                    drawImageRect(
                        image = bitmap.asImageBitmap(),
                        src = Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                        dst = Rect(0f, 0f, size.width * (1 - splitPosition), size.height),
                    )
                }
            }
        }
        
        // Split divider
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .offset(x = (size.width * splitPosition - 2.dp).value.dp)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    awaitFirstDown(requireView = false)
                    dragDetectedGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, _ ->
                            val newPos = (splitPosition + change.positionChange().x / size.width)
                                .coerceIn(0.05f, 0.95f)
                            splitPosition = newPos
                        },
                        onDragEnd = { isDragging = false },
                    )
                },
        ) {
            // Divider handle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .offset(y = (size.height / 2 - 12.dp).value.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shutter),
                    contentDescription = "Split",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        
        // Labels
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(8.dp, 4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Original", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                
                Box(
                    modifier = Modifier
                        .padding(8.dp, 4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Edited", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Tap-hold to show original (simpler alternative).
 */
@Composable
fun TapHoldBeforeAfter(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var showOriginal by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bitmap = if (showOriginal) originalBitmap else editedBitmap
            bitmap?.let { bmp ->
                drawImageRect(
                    image = bmp.asImageBitmap(),
                    src = Rect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()),
                    dst = Rect(0f, 0f, size.width, size.height),
                )
            }
        }
        
        // Touch handler
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitFirstDown(requireView = false)
                    showOriginal = true
                    // Wait for release
                    while (true) {
                        val event = awaitFirstDown(requireView = true)
                        if (!event.changes.any { it.pressed }) break
                    }
                    showOriginal = false
                },
        )
        
        // Indicator
        if (showOriginal) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                    ),
                ) {
                    Text("Original (hold to compare)", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}