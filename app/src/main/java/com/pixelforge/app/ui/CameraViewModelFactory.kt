package com.pixelforge.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pixelforge.app.PixelForgeApp
import com.pixelforge.app.settings.SettingsManager

class CameraViewModelFactory(
    private val app: PixelForgeApp,
    private val settingsManager: SettingsManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(app, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
