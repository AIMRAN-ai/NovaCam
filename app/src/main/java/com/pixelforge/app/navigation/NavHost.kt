package com.pixelforge.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixelforge.app.edit.PhotoEditorViewModel
import com.pixelforge.app.settings.SettingsManager
import com.pixelforge.app.ui.CameraScreen
import com.pixelforge.app.ui.CameraViewModel
import com.pixelforge.app.ui.DiagnosticsScreen
import com.pixelforge.app.ui.PhotoEditorScreen
import com.pixelforge.app.ui.SettingsScreen
import com.pixelforge.app.diagnostics.DiagnosticsState

/**
 * Navigation graph for the app.
 */
@Composable
fun AppNavHost(
    cameraViewModel: CameraViewModel,
    settingsManager: SettingsManager,
    photoEditorViewModel: PhotoEditorViewModel,
) {
    val navController = rememberNavController()
    val diagnosticsState = cameraViewModel.diagnosticsState
    val editorUri = cameraViewModel.editorUri
    
    NavHost(navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                viewModel = cameraViewModel,
                uiState = cameraViewModel.uiState.value,
                diagnosticsState = diagnosticsState.value,
                editorUri = editorUri.value,
                onOpenDiagnostics = { navController.navigate("diagnostics") },
                onOpenSettings = { navController.navigate("settings") },
                onEditorDone = { uri ->
                    cameraViewModel.closeEditor()
                    // Navigate back to camera or show success
                },
                onEditorCancel = {
                    cameraViewModel.closeEditor()
                },
            )
        }
        
        composable("diagnostics") {
            DiagnosticsScreen(
                diagnosticsState = diagnosticsState.value,
                onBack = { navController.popBackStack() },
            )
        }
        
        composable("settings") {
            SettingsScreen(
                settingsManager = settingsManager,
                onBack = { navController.popBackStack() },
            )
        }
        
        composable(
            route = "editor",
            arguments = androidx.navigation.navArgument("uri") { type = androidx.navigation.UriType }
        ) { backStackEntry ->
            val uri = backStackEntry.getString()?.let { android.net.Uri.parse(it) }
            uri?.let {
                PhotoEditorScreen(
                    viewModel = photoEditorViewModel,
                    onDone = { resultUri ->
                        cameraViewModel.closeEditor()
                        navController.popBackStack()
                    },
                    onCancel = {
                        cameraViewModel.closeEditor()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

/**
 * Screen identifiers for type-safe navigation.
 */
object Screen {
    const val Camera = "camera"
    const val Diagnostics = "diagnostics"
    const val Settings = "settings"
    const val Editor = "editor"
    const val Gallery = "gallery"
}