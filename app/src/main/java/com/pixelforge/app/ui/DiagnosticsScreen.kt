package com.pixelforge.app.ui

import android.os.Debug
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixelforge.app.diagnostics.BurstDiagnostics
import com.pixelforge.app.diagnostics.DiagnosticsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    diagnosticsState: DiagnosticsState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text("Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DiagnosticsCard(title = "ML Inference") {
                LabelValueRow {
                    Label("Delegate path")
                    Value(diagnosticsState.delegatePath)
                }
                LabelValueRow {
                    Label("Enhancement")
                    Value(
                        if (diagnosticsState.enhancementEnabled) "ON" else "OFF"
                    )
                }
                LabelValueRow {
                    Label("Degraded")
                    Value(if (diagnosticsState.enhancementDegraded) "YES" else "NO")
                }
            }

            DiagnosticsCard(title = "Startup") {
                LabelValueRow {
                    Label("Cold start")
                    Value(
                        diagnosticsState.coldStartMs?.let { "${it} ms" } ?: "—"
                    )
                }
            }

            DiagnosticsCard(title = "Capture Pipeline") {
                LabelValueRow {
                    Label("Shutter → save")
                    Value(
                        diagnosticsState.shutterToSaveMs?.let { "${it} ms" } ?: "—"
                    )
                }
                diagnosticsState.lastBurst?.let { burst ->
                    BurstSection(burst)
                }
            }

            DiagnosticsCard(title = "HAL Capabilities") {
                Text(
                    text = diagnosticsState.halSummary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            DiagnosticsCard(title = "Preview") {
                LabelValueRow {
                    Label("FPS")
                    Value(
                        diagnosticsState.previewFps?.let { "%.1f".format(it) } ?: "—"
                    )
                }
                LabelValueRow {
                    Label("Latency")
                    Value(
                        diagnosticsState.previewLatencyMs?.let { "${it} ms" } ?: "—"
                    )
                }
            }

            DiagnosticsCard(title = "Memory") {
                LabelValueRow {
                    Label("App RAM")
                    Value("${diagnosticsState.ramUsedMb} MB")
                }
                LabelValueRow {
                    Label("Native heap")
                    Value("${Debug.getNativeHeapAllocatedSize() / 1048576L} MB")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BurstSection(burst: BurstDiagnostics) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    LabelValueRow {
        Label("Frames")
        Value("${burst.framesCaptured} / ${burst.framesRequested}")
    }
    LabelValueRow {
        Label("Achieved FPS")
        Value("%.1f (target ${burst.targetFps})".format(burst.achievedFps))
    }
    LabelValueRow {
        Label("Dropped frames")
        Value("${burst.droppedFrames}")
    }
    LabelValueRow {
        Label("Duration")
        Value("${burst.durationMs} ms")
    }
}

@Composable
private fun LabelValueRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Value(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
