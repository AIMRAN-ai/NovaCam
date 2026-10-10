package com.pixelforge.app.ml

import android.graphics.Bitmap
import com.pixelforge.app.diagnostics.Diagnostics

interface ModelRunner {
    val delegatePath: String
    val inputSize: Int

    suspend fun initialize()

    fun enhance(input: Bitmap): Bitmap

    fun release()
}
