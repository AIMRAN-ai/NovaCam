package com.pixelforge.app.edit.presets

import com.pixelforge.app.edit.PhotoAdjustments
import com.pixelforge.app.edit.PhotoPreset

object IPhonePresets {
    val presets: List<PhotoPreset> = listOf(
        PhotoPreset(
            id = "original",
            name = "Original",
            thumbnailLutPath = null,
            adjustments = PhotoAdjustments(),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "vivid",
            name = "Vivid",
            thumbnailLutPath = "luts/iphone/vivid.cube",
            adjustments = PhotoAdjustments(
                exposure = 10f,
                contrast = 15f,
                vibrance = 25f,
                warmth = 5f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "dramatic",
            name = "Dramatic",
            thumbnailLutPath = "luts/iphone/dramatic.cube",
            adjustments = PhotoAdjustments(
                exposure = -5f,
                contrast = 30f,
                shadows = -20f,
                highlights = -15f,
                blackPoint = 10f,
                definition = 20f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "silvertone",
            name = "Silvertone",
            thumbnailLutPath = "luts/iphone/silvertone.cube",
            adjustments = PhotoAdjustments(
                exposure = 5f,
                contrast = 10f,
                vibrance = -100f,
                definition = 10f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "noir",
            name = "Noir",
            thumbnailLutPath = "luts/iphone/noir.cube",
            adjustments = PhotoAdjustments(
                exposure = -10f,
                contrast = 40f,
                shadows = -30f,
                highlights = -20f,
                blackPoint = 20f,
                vibrance = -100f,
                definition = 15f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "warmth",
            name = "Warmth",
            thumbnailLutPath = "luts/iphone/warmth.cube",
            adjustments = PhotoAdjustments(
                warmth = 40f,
                tint = 5f,
                exposure = 5f,
                vibrance = 10f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "cool",
            name = "Cool",
            thumbnailLutPath = "luts/iphone/cool.cube",
            adjustments = PhotoAdjustments(
                warmth = -40f,
                tint = -5f,
                exposure = 5f,
                vibrance = 10f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "fade",
            name = "Fade",
            thumbnailLutPath = "luts/iphone/fade.cube",
            adjustments = PhotoAdjustments(
                exposure = 15f,
                contrast = -20f,
                shadows = 20f,
                highlights = -10f,
                blackPoint = -15f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "chrome",
            name = "Chrome",
            thumbnailLutPath = "luts/iphone/chrome.cube",
            adjustments = PhotoAdjustments(
                contrast = 20f,
                vibrance = 30f,
                warmth = -10f,
                definition = 15f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "process",
            name = "Process",
            thumbnailLutPath = "luts/iphone/process.cube",
            adjustments = PhotoAdjustments(
                exposure = 10f,
                contrast = 25f,
                warmth = 15f,
                tint = 10f,
                vibrance = 20f,
                definition = 10f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "transfer",
            name = "Transfer",
            thumbnailLutPath = "luts/iphone/transfer.cube",
            adjustments = PhotoAdjustments(
                exposure = -5f,
                contrast = -10f,
                shadows = 15f,
                highlights = -10f,
                warmth = -20f,
                vibrance = -30f,
            ),
            isIPhonePreset = true,
        ),
        PhotoPreset(
            id = "instant",
            name = "Instant",
            thumbnailLutPath = "luts/iphone/instant.cube",
            adjustments = PhotoAdjustments(
                exposure = 10f,
                contrast = -15f,
                shadows = 20f,
                warmth = 20f,
                tint = 10f,
                vignette = 15f,
                vibrance = -20f,
            ),
            isIPhonePreset = true,
        ),
    )
    
    fun getPreset(id: String): PhotoPreset? = presets.find { it.id == id }
    
    fun getIPhonePresets(): List<PhotoPreset> = presets.filter { it.isIPhonePreset }
}