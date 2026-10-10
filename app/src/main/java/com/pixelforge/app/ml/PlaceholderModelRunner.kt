package com.pixelforge.app.ml

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.pixelforge.app.diagnostics.Diagnostics

class PlaceholderModelRunner(
    private val diagnostics: Diagnostics,
) : ModelRunner {

    override val delegatePath: String = "CPU-PLACEHOLDER"

    override val inputSize: Int = 512

    override suspend fun initialize() {
        diagnostics.recordDelegatePath(delegatePath)
    }

    override fun enhance(input: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(enhancementMatrix())
        }
        canvas.drawBitmap(input, Rect(0, 0, input.width, input.height),
            Rect(0, 0, output.width, output.height), paint)
        return output
    }

    override fun release() {
    }

    private fun enhancementMatrix(): ColorMatrix {
        val sharpen = floatArrayOf(
            0f, -1f, 0f, 0f, 0f,
            -1f, 5f, -1f, 0f, 0f,
            0f, -1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val matrix = ColorMatrix()
        matrix.set(sharpen)
        val exposure = ColorMatrix(floatArrayOf(
            1.08f, 0f, 0f, 0f, 6f,
            0f, 1.08f, 0f, 0f, 6f,
            0f, 0f, 1.08f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(exposure)
        val saturation = ColorMatrix()
        saturation.setSaturation(1.12f)
        matrix.postConcat(saturation)
        return matrix
    }
}
