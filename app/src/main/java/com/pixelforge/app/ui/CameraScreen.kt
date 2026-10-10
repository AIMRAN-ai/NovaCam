package com.pixelforge.app.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixelforge.app.R
import com.pixelforge.app.camera.AspectRatio
import com.pixelforge.app.diagnostics.DiagnosticsState
import com.pixelforge.app.ui.theme.PixelForgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    uiState: CameraUiState,
    diagnosticsState: DiagnosticsState,
    onOpenDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        viewModel.retainPreviewReferences(lifecycleOwner, previewView)
        viewModel.attachPreviewView(lifecycleOwner, previewView)
    }

    PixelForgeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PixelForge") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
                    actions = {
                        OutlinedButton(onClick = onOpenDiagnostics) {
                            Text("Diagnostics")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(color = Color.Black) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AspectRatio.entries.forEach { ratio ->
                                FilterChip(
                                    selected = uiState.aspectRatio == ratio,
                                    onClick = { viewModel.setAspectRatio(ratio) },
                                    label = { Text(ratio.label) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            FilterChip(
                                selected = uiState.captureMode == CaptureMode.BURST_YUV,
                                onClick = { viewModel.setCaptureMode(CaptureMode.BURST_YUV) },
                                label = { Text("Burst YUV") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            FilterChip(
                                selected = uiState.captureMode == CaptureMode.RAW_DNG,
                                onClick = { viewModel.setCaptureMode(CaptureMode.RAW_DNG) },
                                label = { Text("RAW DNG") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Frames: ${uiState.burstFrames}",
                                color = Color.White,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Slider(
                                value = uiState.burstFrames.toFloat(),
                                onValueChange = { viewModel.setBurstFrames(it.toInt()) },
                                valueRange = 5f..12f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            FilterChip(
                                selected = uiState.enhancementEnabled,
                                onClick = { viewModel.toggleEnhancement() },
                                label = { Text("Enhancement") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Button(
                                onClick = { viewModel.onShutter() },
                                enabled = !uiState.isCapturing,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shutter),
                                    contentDescription = "Shutter button",
                                    tint = Color.White,
                                )
                            }
                        }
                        if (uiState.isCapturing) {
                            Text(
                                text = "Capturing burst…",
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        uiState.lastError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
