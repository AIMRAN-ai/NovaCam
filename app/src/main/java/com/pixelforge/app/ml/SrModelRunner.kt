package com.pixelforge.app.ml

import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics

interface SrModelRunner {
    val delegatePath: String
    val modelName: String

    suspend fun initialize()

    fun isInitialized(): Boolean

    fun upscale(input: Bitmap, scale: Int): Bitmap

    fun release()
}
