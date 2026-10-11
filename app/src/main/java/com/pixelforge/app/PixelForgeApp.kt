package com.pixelforge.app

import android.app.Application
import android.os.SystemClock
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.ml.BurstFusionRunner
import com.pixelforge.app.ml.EsrganLiteRunner
import com.pixelforge.app.ml.FlowNetRunner
import com.pixelforge.app.ml.ModelManager
import com.pixelforge.app.ml.ModelRunner
import com.pixelforge.app.ml.PlaceholderModelRunner
import com.pixelforge.app.ml.RifeRunner
import com.pixelforge.app.ml.TfliteModelRunner

class PixelForgeApp : Application() {

    val diagnostics = Diagnostics()

    val coldStartElapsedRealtime: Long = SystemClock.elapsedRealtime()

    val modelRunner: ModelRunner by lazy { createModelRunner() }

    val flowNet: FlowNetRunner by lazy {
        FlowNetRunner(diagnostics, assets)
    }

    val burstFusion: BurstFusionRunner by lazy {
        BurstFusionRunner(diagnostics, assets)
    }

    val esrgan: EsrganLiteRunner by lazy {
        EsrganLiteRunner(diagnostics, assets)
    }

    val rife: RifeRunner by lazy {
        RifeRunner(diagnostics, assets)
    }

    val modelManager: ModelManager by lazy {
        ModelManager(this, diagnostics)
    }

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
