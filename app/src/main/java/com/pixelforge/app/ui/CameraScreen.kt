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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixelforge.app.R
import com.pixelforge.app.camera.AspectRatio
import com.pixelforge.app.ui.CaptureMode
import com.pixelforge.app.camera.QualityTier
import com.pixelforge.app.diagnostics.DiagnosticsState
import com.pixelforge.app.edit.PhotoEditorScreen
import com.pixelforge.app.ui.theme.PixelForgeTheme
import com.pixelforge.app.video.VideoQualityTier
import com.pixelforge.app.video.AudioMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    uiState: CameraUiState,
    diagnosticsState: DiagnosticsState,
    editorUri: Uri?,
    onOpenDiagnostics: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditorDone: (Uri) -> Unit = {},
    onEditorCancel: () -> Unit = {},
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
        viewModel.bindVideoCapture(lifecycleOwner)
    }

    PixelForgeTheme {
        if (editorUri != null) {
            PhotoEditorScreen(
                viewModel = viewModel.getEditorViewModel(),
                onDone = onEditorDone,
                onCancel = onEditorCancel,
            )
        } else {
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
                        OutlinedButton(onClick = onOpenSettings) {
                            Text("Settings")
                        }
                        // Share last enhanced video if available
                        uiState.lastVideoUri?.let { uri ->
                            OutlinedButton(onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("video/*")
                                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share video"))
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shutter),
                                    contentDescription = "Share video",
                                    tint = Color.White,
                                )
                            }
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
                            FilterChip(
                                selected = uiState.captureMode == CaptureMode.RAW_VIDEO,
                                onClick = { viewModel.setCaptureMode(CaptureMode.RAW_VIDEO) },
                                label = { Text("RAW Video") },
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
                            FilterChip(
                                selected = uiState.srEnabled,
                                onClick = { viewModel.setSrEnabled(!uiState.srEnabled) },
                                label = { Text("Burst SR") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            FilterChip(
                                selected = uiState.previewEnhancementEnabled,
                                onClick = { viewModel.setPreviewEnhancementEnabled(!uiState.previewEnhancementEnabled) },
                                label = { Text("Live Enhance") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            FilterChip(
                                selected = uiState.dualCameraEnabled,
                                onClick = { viewModel.setDualCameraEnabled(!uiState.dualCameraEnabled) },
                                label = { Text("Dual Cam") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            QualityTier.entries.forEach { tier ->
                                FilterChip(
                                    selected = uiState.qualityTier == tier,
                                    onClick = { viewModel.setQualityTier(tier) },
                                    label = { Text(tier.label) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            FilterChip(
                                selected = uiState.srScale == 4,
                                onClick = {
                                    viewModel.setSrScale(
                                        if (uiState.srScale == 4) 2 else 4
                                    )
                                },
                                label = { Text(if (uiState.srScale == 4) "4x" else "2x") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Video Quality",
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, bottom = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // Standard tiers
                            listOf(
                                VideoQualityTier.ORIGINAL,
                                VideoQualityTier.ENHANCED_1080P,
                                VideoQualityTier.ENHANCED_4K,
                            ).forEach { tier ->
                                FilterChip(
                                    selected = uiState.videoQualityTier == tier,
                                    onClick = { viewModel.setVideoQualityTier(tier) },
                                    label = { Text(tier.label) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // Slow-motion tiers
                            listOf(
                                VideoQualityTier.SMOOTH_60FPS,
                                VideoQualityTier.SMOOTH_120FPS,
                            ).forEach { tier ->
                                FilterChip(
                                    selected = uiState.videoQualityTier == tier,
                                    onClick = { viewModel.setVideoQualityTier(tier) },
                                    label = { Text(tier.label) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // HDR tiers
                            listOf(
                                VideoQualityTier.HDR_1080P,
                                VideoQualityTier.HDR_4K,
                                VideoQualityTier.HDR_SMOOTH_60FPS,
                            ).forEach { tier ->
                                FilterChip(
                                    selected = uiState.videoQualityTier == tier,
                                    onClick = { viewModel.setVideoQualityTier(tier) },
                                    label = { Text(tier.label) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Audio mode selector (only shown for slow-motion tiers)
                        if (uiState.videoQualityTier.rifeScale > 1) {
                            Text(
                                text = "Audio Mode",
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, bottom = 4.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                AudioMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = uiState.audioMode == mode,
                                        onClick = { viewModel.setAudioMode(mode) },
                                        label = { Text(mode.label) },
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Button(
                                    onClick = { viewModel.onShutter() },
                                    enabled = !uiState.isCapturing && !uiState.isRecording,
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
                                if (uiState.isRecording) {
                                    Button(
                                        onClick = {
                                            when (uiState.captureMode) {
                                                CaptureMode.RAW_VIDEO -> viewModel.startRawVideoRecording(lifecycleOwner)
                                                CaptureMode.BURST_YUV -> viewModel.startVideoRecording(lifecycleOwner)
                                                else -> viewModel.startHdrVideoRecording(lifecycleOwner)
                                            }
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .align(Alignment.CenterEnd),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_shutter),
                                            contentDescription = "Stop recording",
                                            tint = Color.White,
                                        )
                                    }
                                } else {
                                    // Start recording buttons
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                    ) {
                                        // Normal video
                                        Button(
                                            onClick = { viewModel.startVideoRecording(lifecycleOwner) },
                                            enabled = !uiState.isCapturing && !uiState.isRecording,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                            ),
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Text("●", color = Color.White)
                                        }
                                        // HDR video (if HDR tier selected)
                                        if (uiState.videoQualityTier.isHdr) {
                                            Button(
                                                onClick = { viewModel.startHdrVideoRecording(lifecycleOwner) },
                                                enabled = !uiState.isCapturing && !uiState.isRecording,
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                                ),
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Text("HDR", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                        // RAW video
                                        if (uiState.captureMode == CaptureMode.RAW_VIDEO) {
                                            Button(
                                                onClick = { viewModel.startRawVideoRecording(lifecycleOwner) },
                                                enabled = !uiState.isCapturing && !uiState.isRecording,
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                ),
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Text("RAW", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (uiState.isRecording) {
                            val fpsLabel = when (uiState.videoQualityTier) {
                                VideoQualityTier.SMOOTH_60FPS -> " @ 60fps"
                                VideoQualityTier.SMOOTH_120FPS -> " @ 120fps"
                                VideoQualityTier.HDR_SMOOTH_60FPS -> " @ 60fps HDR"
                                else -> ""
                            }
                            val hdrLabel = if (uiState.videoQualityTier.isHdr) " HDR" else ""
                            Text(
                                text = "Recording…$fpsLabel$hdrLabel",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
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
