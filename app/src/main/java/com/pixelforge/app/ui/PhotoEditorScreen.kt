package com.pixelforge.app.ui

import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixelforge.app.R
import com.pixelforge.app.edit.PhotoEditorViewModel
import com.pixelforge.app.edit.PhotoEditorViewModel.EditorTab
import com.pixelforge.app.edit.AspectRatio
import com.pixelforge.app.edit.presets.PresetManager
import com.pixelforge.app.ui.theme.PixelForgeTheme
import com.pixelforge.app.util.FpsCounter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    onDone: (android.net.Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val presetManager = remember { PresetManager(context) }
    
    val adjustments by viewModel.adjustments.collectAsState()
    val cropState by viewModel.cropState.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val lutIntensity by viewModel.lutIntensity.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val canUndo = viewModel.canUndo()
    val canRedo = viewModel.canRedo()
    
    // Debug: FPS counter
    val showFps = false // Set to true for debug builds
    
    val textureView = remember { TextureView(context) }
    val surfaceTexture = remember { mutableStateOf<SurfaceTexture?>(null) }
    
    LaunchedEffect(Unit) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                surfaceTexture.value = surface
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                surfaceTexture.value = null
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }
    
    LaunchedEffect(surfaceTexture.value) {
        surfaceTexture.value?.let { st ->
            val fpsCounter = FpsCounter()
            while (true) {
                viewModel.renderPreview(st)
                val fps = fpsCounter.tick()
                kotlinx.coroutines.delay(16) // ~60fps
            }
        }
    }
    
    PixelForgeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shutter),
                                contentDescription = "Cancel",
                                tint = Color.White,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                            Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Undo", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                            Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Redo", tint = Color.White)
                        }
                        Button(onClick = {
                            val uri = viewModel.exportImage()
                            uri?.let { onDone(it) }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Done", color = Color.White)
                        }
                    },
                )
            },
            bottomBar = {
                Surface(color = Color.Black, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TabButton(
                                label = "Adjust",
                                selected = currentTab == EditorTab.ADJUST,
                                onClick = { viewModel.setCurrentTab(EditorTab.ADJUST) },
                            )
                            TabButton(
                                label = "Filters",
                                selected = currentTab == EditorTab.FILTERS,
                                onClick = { viewModel.setCurrentTab(EditorTab.FILTERS) },
                            )
                            TabButton(
                                label = "Crop",
                                selected = currentTab == EditorTab.CROP,
                                onClick = { viewModel.setCurrentTab(EditorTab.CROP) },
                            )
                        }
                        
                        when (currentTab) {
                            EditorTab.ADJUST -> AdjustPanel(viewModel, adjustments)
                            EditorTab.FILTERS -> FiltersPanel(viewModel, presetManager, selectedPreset, lutIntensity)
                            EditorTab.CROP -> CropPanel(viewModel, cropState)
                        }
                        
                        if (isLoading) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                                Text("Loading...", color = Color.White)
                            }
                        }
                        
                        if (exportProgress > 0f && exportProgress < 1f) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                                Text("Exporting: ${(exportProgress * 100).toInt()}%", color = Color.White)
                            }
                        }
                        
                        lastError?.let { error ->
                            Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
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
                    factory = { textureView },
                    modifier = Modifier.fillMaxSize(),
                )
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Loading image...", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, color = if (selected) Color.White else Color.Gray) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun AdjustPanel(viewModel: PhotoEditorViewModel, adjustments: com.pixelforge.app.edit.PhotoAdjustments) {
    val scrollState = rememberScrollState()
    val groups = listOf(
        AdjustmentGroup(
            title = "Light",
            adjustments = listOf(
                AdjustmentItem("Exposure", "exposure", adjustments.exposure, -100f, 100f),
                AdjustmentItem("Brilliance", "brilliance", adjustments.brilliance, -100f, 100f),
                AdjustmentItem("Highlights", "highlights", adjustments.highlights, -100f, 100f),
                AdjustmentItem("Shadows", "shadows", adjustments.shadows, -100f, 100f),
                AdjustmentItem("Contrast", "contrast", adjustments.contrast, -100f, 100f),
                AdjustmentItem("Brightness", "brightness", adjustments.brightness, -100f, 100f),
                AdjustmentItem("Black Point", "blackPoint", adjustments.blackPoint, -100f, 100f),
            )
        ),
        AdjustmentGroup(
            title = "Color",
            adjustments = listOf(
                AdjustmentItem("Warmth", "warmth", adjustments.warmth, -100f, 100f),
                AdjustmentItem("Tint", "tint", adjustments.tint, -100f, 100f),
                AdjustmentItem("Vibrance", "vibrance", adjustments.vibrance, -100f, 100f),
            )
        ),
        AdjustmentGroup(
            title = "Detail",
            adjustments = listOf(
                AdjustmentItem("Sharpness", "sharpness", adjustments.sharpness, -100f, 100f),
                AdjustmentItem("Definition", "definition", adjustments.definition, -100f, 100f),
                AdjustmentItem("Noise Reduction", "noiseReduction", adjustments.noiseReduction, -100f, 100f),
                AdjustmentItem("Vignette", "vignette", adjustments.vignette, -100f, 100f),
            )
        ),
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        groups.forEach { group ->
            Text(text = group.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            group.adjustments.forEach { item ->
                AdjustmentSlider(
                    label = item.label,
                    key = item.key,
                    value = item.value,
                    min = item.min,
                    max = item.max,
                    onValueChange = { viewModel.updateAdjustment(item.key, it) },
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { viewModel.resetAdjustments() }) {
                Text("Reset", color = Color.White)
            }
            Button(onClick = { viewModel.autoEnhance() }) {
                Text("Auto", color = Color.White)
            }
        }
    }
}

data class AdjustmentGroup(val title: String, val adjustments: List<AdjustmentItem>)
data class AdjustmentItem(val label: String, val key: String, val value: Float, val min: Float, val max: Float)

@Composable
fun AdjustmentSlider(label: String, key: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, modifier = Modifier.width(90.dp))
        Text(text = value.toInt().toString(), color = Color.Gray, modifier = Modifier.width(40.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
fun FiltersPanel(
    viewModel: PhotoEditorViewModel,
    presetManager: PresetManager,
    selectedPreset: com.pixelforge.app.edit.PhotoPreset?,
    lutIntensity: Float,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = "iPhone Presets", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        
        androidx.compose.foundation.layout.horizontalScroll(scrollState) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetManager.getIPhonePresets().forEach { preset ->
                    PresetChip(
                        preset = preset,
                        selected = selectedPreset?.id == preset.id,
                        onClick = { viewModel.applyPreset(preset) },
                    )
                }
            }
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Custom", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = { viewModel.clearPreset() },
                label = { Text("None", color = Color.White) },
            )
        }
        
        if (lutIntensity < 1f) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("LUT Intensity", color = Color.White, modifier = Modifier.padding(end = 16.dp))
                Slider(
                    value = lutIntensity,
                    onValueChange = { viewModel.setLutIntensity(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun PresetChip(preset: com.pixelforge.app.edit.PhotoPreset, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(preset.name, color = if (selected) Color.White else Color.Gray) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.height(36.dp),
    )
}

@Composable
fun CropPanel(viewModel: PhotoEditorViewModel, cropState: com.pixelforge.app.edit.CropState) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = "Aspect Ratio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        
        androidx.compose.foundation.layout.horizontalScroll(rememberScrollState()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AspectRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = cropState.aspectRatio == ratio,
                        onClick = { viewModel.setCropAspectRatio(if (cropState.aspectRatio == ratio) null else ratio) },
                        label = { Text(ratio.label, color = if (cropState.aspectRatio == ratio) Color.White else Color.Gray) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = if (cropState.aspectRatio == ratio) MaterialTheme.colorScheme.primary else Color.Transparent,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { viewModel.rotateCrop(-90f) }) {
                Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Rotate Left", tint = Color.White)
                Text("Rotate Left", color = Color.White, fontSize = 10.sp)
            }
            IconButton(onClick = { viewModel.rotateCrop(90f) }) {
                Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Rotate Right", tint = Color.White)
                Text("Rotate Right", color = Color.White, fontSize = 10.sp)
            }
            IconButton(onClick = { viewModel.flipCropHorizontal() }) {
                Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Flip Horizontal", tint = Color.White)
                Text("Flip H", color = Color.White, fontSize = 10.sp)
            }
            IconButton(onClick = { viewModel.flipCropVertical() }) {
                Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Flip Vertical", tint = Color.White)
                Text("Flip V", color = Color.White, fontSize = 10.sp)
            }
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Rotation: ${cropState.rotationDegrees.toInt()}°", color = Color.Gray, modifier = Modifier.padding(start = 16.dp))
    }
}