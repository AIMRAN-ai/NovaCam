package com.pixelforge.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pixelforge.app.PixelForgeApp
import com.pixelforge.app.settings.SettingsManager
import com.pixelforge.app.ui.theme.PixelForgeTheme

class MainActivity : ComponentActivity() {

    private val settingsManager = SettingsManager(this)
    
    private val viewModel: CameraViewModel by viewModels {
        CameraViewModelFactory(application as PixelForgeApp, settingsManager)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setContentSafe()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setContentSafe()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setContentSafe() {
        setContent {
            PixelForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val diagnosticsState by viewModel.diagnosticsState.collectAsState()
                    val editorUri by viewModel.editorUri.collectAsState()
                    CameraScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        diagnosticsState = diagnosticsState,
                        editorUri = editorUri,
                        onOpenDiagnostics = { /* TODO: navigate to diagnostics */ },
                        onOpenSettings = { /* TODO: navigate to settings */ },
                        onEditorDone = { uri ->
                            viewModel.closeEditor()
                            // Optionally share or show success
                        },
                        onEditorCancel = {
                            viewModel.closeEditor()
                        },
                    )
                }
            }
        }
    }
}
