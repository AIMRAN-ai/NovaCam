package com.pixelforge.app

import android.app.Application
import android.os.SystemClock
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.ml.ModelRunner
import com.pixelforge.app.ml.PlaceholderModelRunner
import com.pixelforge.app.ml.TfliteModelRunner

class PixelForgeApp : Application() {

    val diagnostics = Diagnostics()

    val coldStartElapsedRealtime: Long = SystemClock.elapsedRealtime()

    val modelRunner: ModelRunner by lazy { createModelRunner() }

    private fun createModelRunner(): ModelRunner {
        val assets = assets
        return if (assets.list("")?.contains(TFLITE_MODEL_ASSET) == true) {
            TfliteModelRunner(diagnostics, TFLITE_MODEL_ASSET, assets)
        } else {
            PlaceholderModelRunner(diagnostics)
        }
    }

    companion object {
        const val TFLITE_MODEL_ASSET = "sr_placeholder.tflite"
    }
}
