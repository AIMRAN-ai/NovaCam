package com.pixelforge.app.edit.presets

import android.content.Context
import com.pixelforge.app.edit.PhotoPreset
import com.pixelforge.app.edit.presets.IPhonePresets
import java.io.File

class PresetManager(private val context: Context) {
    private val customPresets = mutableListOf<PhotoPreset>()
    
    fun getAllPresets(): List<PhotoPreset> = IPhonePresets.presets + customPresets
    
    fun getIPhonePresets(): List<PhotoPreset> = IPhonePresets.getIPhonePresets()
    
    fun getPreset(id: String): PhotoPreset? = getAllPresets().find { it.id == id }
    
    fun saveCustomPreset(name: String, adjustments: com.pixelforge.app.edit.PhotoAdjustments, lutPath: String? = null): PhotoPreset {
        val preset = PhotoPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            thumbnailLutPath = lutPath,
            adjustments = adjustments,
            isIPhonePreset = false,
        )
        customPresets.add(preset)
        return preset
    }
    
    fun deleteCustomPreset(id: String) {
        customPresets.removeAll { it.id == id }
    }
    
    fun generateLutThumbnail(adjustments: com.pixelforge.app.edit.PhotoAdjustments): String? {
        return null
    }
    
    fun hasLutFile(path: String): Boolean {
        try {
            val inputStream = context.assets.open(path)
            inputStream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}