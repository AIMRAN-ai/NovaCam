package com.pixelforge.app.edit

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.coerceIn

data class PhotoAdjustments(
    val exposure: Float = 0f,
    val ev: Float = 0f,
    val brilliance: Float = 0f,
    val vignette: Float = 0f,
    val noiseReduction: Float = 0f,
    val definition: Float = 0f,
    val sharpness: Float = 0f,
    val tint: Float = 0f,
    val warmth: Float = 0f,
    val vibrance: Float = 0f,
    val blackPoint: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
) {
    companion object {
        private const val MIN = -100f
        private const val MAX = 100f
    }

    fun clamped(): PhotoAdjustments = PhotoAdjustments(
        exposure = exposure.coerceIn(MIN, MAX),
        ev = ev.coerceIn(MIN, MAX),
        brilliance = brilliance.coerceIn(MIN, MAX),
        vignette = vignette.coerceIn(MIN, MAX),
        noiseReduction = noiseReduction.coerceIn(MIN, MAX),
        definition = definition.coerceIn(MIN, MAX),
        sharpness = sharpness.coerceIn(MIN, MAX),
        tint = tint.coerceIn(MIN, MAX),
        warmth = warmth.coerceIn(MIN, MAX),
        vibrance = vibrance.coerceIn(MIN, MAX),
        blackPoint = blackPoint.coerceIn(MIN, MAX),
        brightness = brightness.coerceIn(MIN, MAX),
        contrast = contrast.coerceIn(MIN, MAX),
        shadows = shadows.coerceIn(MIN, MAX),
        highlights = highlights.coerceIn(MIN, MAX),
    )

    operator fun plus(other: PhotoAdjustments): PhotoAdjustments = PhotoAdjustments(
        exposure = exposure + other.exposure,
        ev = ev + other.ev,
        brilliance = brilliance + other.brilliance,
        vignette = vignette + other.vignette,
        noiseReduction = noiseReduction + other.noiseReduction,
        definition = definition + other.definition,
        sharpness = sharpness + other.sharpness,
        tint = tint + other.tint,
        warmth = warmth + other.warmth,
        vibrance = vibrance + other.vibrance,
        blackPoint = blackPoint + other.blackPoint,
        brightness = brightness + other.brightness,
        contrast = contrast + other.contrast,
        shadows = shadows + other.shadows,
        highlights = highlights + other.highlights,
    ).clamped()

    operator fun minus(other: PhotoAdjustments): PhotoAdjustments = PhotoAdjustments(
        exposure = exposure - other.exposure,
        ev = ev - other.ev,
        brilliance = brilliance - other.brilliance,
        vignette = vignette - other.vignette,
        noiseReduction = noiseReduction - other.noiseReduction,
        definition = definition - other.definition,
        sharpness = sharpness - other.sharpness,
        tint = tint - other.tint,
        warmth = warmth - other.warmth,
        vibrance = vibrance - other.vibrance,
        blackPoint = blackPoint - other.blackPoint,
        brightness = brightness - other.brightness,
        contrast = contrast - other.contrast,
        shadows = shadows - other.shadows,
        highlights = highlights - other.highlights,
    ).clamped()

    fun times(scale: Float): PhotoAdjustments = PhotoAdjustments(
        exposure = exposure * scale,
        ev = ev * scale,
        brilliance = brilliance * scale,
        vignette = vignette * scale,
        noiseReduction = noiseReduction * scale,
        definition = definition * scale,
        sharpness = sharpness * scale,
        tint = tint * scale,
        warmth = warmth * scale,
        vibrance = vibrance * scale,
        blackPoint = blackPoint * scale,
        brightness = brightness * scale,
        contrast = contrast * scale,
        shadows = shadows * scale,
        highlights = highlights * scale,
    ).clamped()

    fun toShaderUniforms(): Map<String, Float> = mapOf(
        "uExposure" to (exposure / 100f),
        "uEv" to (ev / 100f),
        "uBrilliance" to (brilliance / 100f),
        "uVignette" to (vignette / 100f),
        "uNoiseReduction" to (noiseReduction / 100f),
        "uDefinition" to (definition / 100f),
        "uSharpness" to (sharpness / 100f),
        "uTint" to (tint / 100f),
        "uWarmth" to (warmth / 100f),
        "uVibrance" to (vibrance / 100f),
        "uBlackPoint" to (blackPoint / 100f),
        "uBrightness" to (brightness / 100f),
        "uContrast" to (contrast / 100f),
        "uShadows" to (shadows / 100f),
        "uHighlights" to (highlights / 100f),
    )

    fun copy(
        exposure: Float = this.exposure,
        ev: Float = this.ev,
        brilliance: Float = this.brilliance,
        vignette: Float = this.vignette,
        noiseReduction: Float = this.noiseReduction,
        definition: Float = this.definition,
        sharpness: Float = this.sharpness,
        tint: Float = this.tint,
        warmth: Float = this.warmth,
        vibrance: Float = this.vibrance,
        blackPoint: Float = this.blackPoint,
        brightness: Float = this.brightness,
        contrast: Float = this.contrast,
        shadows: Float = this.shadows,
        highlights: Float = this.highlights,
    ): PhotoAdjustments = PhotoAdjustments(
        exposure = exposure,
        ev = ev,
        brilliance = brilliance,
        vignette = vignette,
        noiseReduction = noiseReduction,
        definition = definition,
        sharpness = sharpness,
        tint = tint,
        warmth = warmth,
        vibrance = vibrance,
        blackPoint = blackPoint,
        brightness = brightness,
        contrast = contrast,
        shadows = shadows,
        highlights = highlights,
    )
}

data class CropState(
    val rect: RectF = RectF(0f, 0f, 1f, 1f),
    val rotationDegrees: Float = 0f,
    val aspectRatio: AspectRatio? = null,
    val perspectiveTransform: Matrix? = null,
) {
    fun copy(
        rect: RectF = this.rect,
        rotationDegrees: Float = this.rotationDegrees,
        aspectRatio: AspectRatio? = this.aspectRatio,
        perspectiveTransform: Matrix? = this.perspectiveTransform,
    ): CropState = CropState(
        rect = rect,
        rotationDegrees = rotationDegrees,
        aspectRatio = aspectRatio,
        perspectiveTransform = perspectiveTransform,
    )
}

data class PhotoPreset(
    val id: String,
    val name: String,
    val thumbnailLutPath: String?,
    val adjustments: PhotoAdjustments,
    val isIPhonePreset: Boolean = false,
)

enum class AspectRatio(val label: String, val ratio: Float) {
    FREE("Free", 0f),
    SQUARE("1:1", 1f),
    RATIO_4_5("4:5", 0.8f),
    RATIO_9_16("9:16", 0.5625f),
    RATIO_16_9("16:9", 1.7777778f),
    RATIO_3_4("3:4", 0.75f),
}