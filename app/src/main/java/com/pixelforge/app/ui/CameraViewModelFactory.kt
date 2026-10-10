package com.pixelforge.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pixelforge.app.PixelForgeApp

class CameraViewModelFactory(
    private val app: PixelForgeApp,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
