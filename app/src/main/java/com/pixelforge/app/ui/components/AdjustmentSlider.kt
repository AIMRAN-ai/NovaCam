package com.pixelforge.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelforge.app.R

/**
 * Polished adjustment slider with icon, label, value display, and reset button.
 */
@Composable
fun AdjustmentSlider(
    label: String,
    key: String,
    value: Float,
    min: Float = -100f,
    max: Float = 100f,
    icon: Int? = null,
    showValue: Boolean = true,
    showReset: Boolean = true,
    onValueChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val isDefault = value == 0f
    
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        // Header with label, icon, value
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    )
                }
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (isDefault) FontWeight.Normal else FontWeight.Medium,
                    modifier = Modifier.width(80.dp),
                )
            }
            
            if (showValue) {
                Text(
                    text = value.toInt().toString(),
                    color = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
        
        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            ),
            thumb = { thumbState ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .offset { IntOffset(-10.dp.roundToPx(), (-10 + thumbState.thumbOffset).roundToPx()) }
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                )
            },
        )
        
        // Reset button
        if (showReset && !isDefault) {
            onReset?.let { resetAction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .align(Alignment.End),
                ) {
                    IconButton(onClick = resetAction) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shutter),
                            contentDescription = "Reset $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact slider for tight spaces (like in bottom sheet).
 */
@Composable
fun CompactAdjustmentSlider(
    label: String,
    value: Float,
    min: Float = -100f,
    max: Float = 100f,
    onValueChange: (Float) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            modifier = Modifier.width(70.dp),
        )
        Text(
            text = value.toInt().toString(),
            color = if (value == 0f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(32.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

/**
 * Adjustment group with title and list of sliders.
 */
@Composable
fun AdjustmentGroup(
    title: String,
    icon: Int? = null,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                )
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            
            onExpandedChange?.let { callback ->
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { callback(!expanded) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shutter),
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        
        if (expanded) {
            content()
        }
    }
}