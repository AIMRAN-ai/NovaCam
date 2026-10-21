package com.pixelforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelforge.app.R
import com.pixelforge.app.settings.SettingsManager
import com.pixelforge.app.ui.theme.PixelForgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    
    val themeMode by settingsManager.themeMode.collectAsState()
    val gridType by settingsManager.gridType.collectAsState()
    val autoEnhance by settingsManager.autoEnhance.collectAsState()
    val exportQuality by settingsManager.exportQuality.collectAsState()
    val exportFormat by settingsManager.exportFormat.collectAsState()
    val delegatePreference by settingsManager.delegatePreference.collectAsState()
    val showHistogram by settingsManager.showHistogram.collectAsState()
    val saveOriginal by settingsManager.saveOriginal.collectAsState()
    val hapticFeedback by settingsManager.hapticFeedback.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shutter),
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsRow(
                    label = "Theme",
                    description = "Choose app theme",
                    trailing = {
                        ThemeSelector(
                            currentMode = themeMode,
                            onModeChange = { settingsManager.setThemeMode(it) },
                        )
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Grid Overlay",
                    description = "Show grid lines in camera preview",
                    trailing = {
                        GridSelector(
                            currentGrid = gridType,
                            onGridChange = { settingsManager.setGridType(it) },
                        )
                    },
                )
            }
            
            // Capture Section
            SettingsSection(title = "Capture") {
                SettingsRow(
                    label = "Auto Enhance",
                    description = "Automatically apply enhancements after capture",
                    trailing = {
                        Switch(
                            checked = autoEnhance,
                            onCheckedChange = { settingsManager.setAutoEnhance(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Save Original",
                    description = "Keep unedited original alongside edited version",
                    trailing = {
                        Switch(
                            checked = saveOriginal,
                            onCheckedChange = { settingsManager.setSaveOriginal(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Haptic Feedback",
                    description = "Vibrate on button presses and slider changes",
                    trailing = {
                        Switch(
                            checked = hapticFeedback,
                            onCheckedChange = { settingsManager.setHapticFeedback(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    },
                )
            }
            
            // Export Section
            SettingsSection(title = "Export") {
                SettingsRow(
                    label = "Export Quality",
                    description = "JPEG compression quality for exported images",
                    trailing = {
                        QualitySelector(
                            currentQuality = exportQuality,
                            onQualityChange = { settingsManager.setExportQuality(it) },
                        )
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Export Format",
                    description = "File format for exported images",
                    trailing = {
                        FormatSelector(
                            currentFormat = exportFormat,
                            onFormatChange = { settingsManager.setExportFormat(it) },
                        )
                    },
                )
            }
            
            // Performance Section
            SettingsSection(title = "Performance") {
                SettingsRow(
                    label = "ML Delegate",
                    description = "Hardware accelerator for ML inference",
                    trailing = {
                        DelegateSelector(
                            currentDelegate = delegatePreference,
                            onDelegateChange = { settingsManager.setDelegatePreference(it) },
                        )
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Show Histogram",
                    description = "Display RGB histogram in editor",
                    trailing = {
                        Switch(
                            checked = showHistogram,
                            onCheckedChange = { settingsManager.setShowHistogram(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    },
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                SettingsRow(
                    label = "Version",
                    description = "0.1.0-phase1",
                    trailing = { Text("Build: debug") },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Open Source",
                    description = "View licenses and source code",
                    trailing = {
                        IconButton(onClick = { /* Open licenses */ }) {
                            Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Open", tint = Color.White)
                        }
                    },
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SettingsRow(
                    label = "Report Issue",
                    description = "Send feedback or bug report",
                    trailing = {
                        IconButton(onClick = { /* Open issue tracker */ }) {
                            Icon(painter = painterResource(id = R.drawable.ic_shutter), contentDescription = "Report", tint = Color.White)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SettingsRow(
    label: String,
    description: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
fun ThemeSelector(
    currentMode: String,
    onModeChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SettingsManager.THEME_SYSTEM to "System",
            SettingsManager.THEME_LIGHT to "Light",
            SettingsManager.THEME_DARK to "Dark",
        ).forEach { (mode, label) ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (currentMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun GridSelector(
    currentGrid: String,
    onGridChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SettingsManager.GRID_NONE to "None",
            SettingsManager.GRID_THIRDS to "Thirds",
            SettingsManager.GRID_SQUARE to "Square",
            SettingsManager.GRID_GOLDEN to "Golden",
        ).forEach { (grid, label) ->
            FilterChip(
                selected = currentGrid == grid,
                onClick = { onGridChange(grid) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (currentGrid == grid) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun QualitySelector(
    currentQuality: String,
    onQualityChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SettingsManager.EXPORT_LOW to "Low",
            SettingsManager.EXPORT_MEDIUM to "Medium",
            SettingsManager.EXPORT_HIGH to "High",
            SettingsManager.EXPORT_MAX to "Max",
        ).forEach { (quality, label) ->
            FilterChip(
                selected = currentQuality == quality,
                onClick = { onQualityChange(quality) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (currentQuality == quality) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun FormatSelector(
    currentFormat: String,
    onFormatChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SettingsManager.FORMAT_JPEG to "JPEG",
            SettingsManager.FORMAT_HEIC to "HEIC",
            SettingsManager.FORMAT_PNG to "PNG",
        ).forEach { (format, label) ->
            FilterChip(
                selected = currentFormat == format,
                onClick = { onFormatChange(format) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (currentFormat == format) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun DelegateSelector(
    currentDelegate: String,
    onDelegateChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SettingsManager.DELEGATE_AUTO to "Auto",
            SettingsManager.DELEGATE_GPU to "GPU",
            SettingsManager.DELEGATE_NNAPI to "NNAPI",
            SettingsManager.DELEGATE_CPU to "CPU",
        ).forEach { (delegate, label) ->
            FilterChip(
                selected = currentDelegate == delegate,
                onClick = { onDelegateChange(delegate) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = if (currentDelegate == delegate) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}