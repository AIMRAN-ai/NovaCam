package com.pixelforge.app.edit.gpu

import android.content.Context
import android.graphics.RectF
import android.opengl.GLES32
import android.opengl.Matrix
import com.pixelforge.app.edit.PhotoAdjustments
import com.pixelforge.app.edit.CropState

class AdjustmentShader(context: Context) {
    private val program: ShaderProgram? = ShaderProgram.create(
        context, "shaders/vertex.vert", "shaders/adjustment.frag"
    )
    
    private val uMVPMatrix: Int
    private val uCropMatrix: Int
    private val uTexture: Int
    private val uLut: Int
    private val uLutIntensity: Int
    
    private val adjustmentUniforms: Map<String, Int>
    
    init {
        if (program != null) {
            program.use()
            uMVPMatrix = program.getUniformLocation("uMVPMatrix")
            uCropMatrix = program.getUniformLocation("uCropMatrix")
            uTexture = program.getUniformLocation("uTexture")
            uLut = program.getUniformLocation("uLut")
            uLutIntensity = program.getUniformLocation("uLutIntensity")
            
            adjustmentUniforms = mapOf(
                "uExposure" to program.getUniformLocation("uExposure"),
                "uEv" to program.getUniformLocation("uEv"),
                "uBrilliance" to program.getUniformLocation("uBrilliance"),
                "uVignette" to program.getUniformLocation("uVignette"),
                "uNoiseReduction" to program.getUniformLocation("uNoiseReduction"),
                "uDefinition" to program.getUniformLocation("uDefinition"),
                "uSharpness" to program.getUniformLocation("uSharpness"),
                "uTint" to program.getUniformLocation("uTint"),
                "uWarmth" to program.getUniformLocation("uWarmth"),
                "uVibrance" to program.getUniformLocation("uVibrance"),
                "uBlackPoint" to program.getUniformLocation("uBlackPoint"),
                "uBrightness" to program.getUniformLocation("uBrightness"),
                "uContrast" to program.getUniformLocation("uContrast"),
                "uShadows" to program.getUniformLocation("uShadows"),
                "uHighlights" to program.getUniformLocation("uHighlights"),
            )
        } else {
            uMVPMatrix = -1
            uCropMatrix = -1
            uTexture = -1
            uLut = -1
            uLutIntensity = -1
            adjustmentUniforms = emptyMap()
        }
    }
    
    fun isValid(): Boolean = program != null
    
    fun use() {
        program?.use()
    }
    
    fun setMVPMatrix(matrix: FloatArray) {
        program?.setUniformMatrix4fv(uMVPMatrix, matrix)
    }
    
    fun setCropMatrix(matrix: FloatArray) {
        program?.setUniformMatrix3fv(uCropMatrix, matrix)
    }
    
    fun setTextureUnit(unit: Int) {
        program?.setUniform1i(uTexture, unit)
    }
    
    fun setLutTextureUnit(unit: Int) {
        program?.setUniform1i(uLut, unit)
    }
    
    fun setLutIntensity(intensity: Float) {
        program?.setUniform1f(uLutIntensity, intensity)
    }
    
    fun setAdjustments(adjustments: PhotoAdjustments) {
        val uniforms = adjustments.toShaderUniforms()
        program?.let { p ->
            uniforms.forEach { (name, value) ->
                adjustmentUniforms[name]?.let { loc ->
                    p.setUniform1f(loc, value)
                }
            }
        }
    }
    
    fun release() {
        program?.release()
    }
}

class LutShader(context: Context) {
    private val program: ShaderProgram? = ShaderProgram.create(
        context, "shaders/vertex.vert", "shaders/lut.frag"
    )
    
    private val uMVPMatrix: Int
    private val uCropMatrix: Int
    private val uTexture: Int
    private val uLut: Int
    private val uLutIntensity: Int
    
    init {
        if (program != null) {
            program.use()
            uMVPMatrix = program.getUniformLocation("uMVPMatrix")
            uCropMatrix = program.getUniformLocation("uCropMatrix")
            uTexture = program.getUniformLocation("uTexture")
            uLut = program.getUniformLocation("uLut")
            uLutIntensity = program.getUniformLocation("uLutIntensity")
        } else {
            uMVPMatrix = -1
            uCropMatrix = -1
            uTexture = -1
            uLut = -1
            uLutIntensity = -1
        }
    }
    
    fun isValid(): Boolean = program != null
    
    fun use() {
        program?.use()
    }
    
    fun setMVPMatrix(matrix: FloatArray) {
        program?.setUniformMatrix4fv(uMVPMatrix, matrix)
    }
    
    fun setCropMatrix(matrix: FloatArray) {
        program?.setUniformMatrix3fv(uCropMatrix, matrix)
    }
    
    fun setTextureUnit(unit: Int) {
        program?.setUniform1i(uTexture, unit)
    }
    
    fun setLutTextureUnit(unit: Int) {
        program?.setUniform1i(uLut, unit)
    }
    
    fun setLutIntensity(intensity: Float) {
        program?.setUniform1f(uLutIntensity, intensity)
    }
    
    fun release() {
        program?.release()
    }
}

class CropShader(context: Context) {
    private val program: ShaderProgram? = ShaderProgram.create(
        context, "shaders/vertex.vert", "shaders/crop.frag"
    )
    
    private val uMVPMatrix: Int
    private val uCropMatrix: Int
    private val uTexture: Int
    private val uCropRect: Int
    private val uRotation: Int
    private val uPerspectiveMatrix: Int
    
    init {
        if (program != null) {
            program.use()
            uMVPMatrix = program.getUniformLocation("uMVPMatrix")
            uCropMatrix = program.getUniformLocation("uCropMatrix")
            uTexture = program.getUniformLocation("uTexture")
            uCropRect = program.getUniformLocation("uCropRect")
            uRotation = program.getUniformLocation("uRotation")
            uPerspectiveMatrix = program.getUniformLocation("uPerspectiveMatrix")
        } else {
            uMVPMatrix = -1
            uCropMatrix = -1
            uTexture = -1
            uCropRect = -1
            uRotation = -1
            uPerspectiveMatrix = -1
        }
    }
    
    fun isValid(): Boolean = program != null
    
    fun use() {
        program?.use()
    }
    
    fun setMVPMatrix(matrix: FloatArray) {
        program?.setUniformMatrix4fv(uMVPMatrix, matrix)
    }
    
    fun setCropMatrix(matrix: FloatArray) {
        program?.setUniformMatrix3fv(uCropMatrix, matrix)
    }
    
    fun setTextureUnit(unit: Int) {
        program?.setUniform1i(uTexture, unit)
    }
    
    fun setCropRect(cropState: CropState) {
        val rect = cropState.rect
        program?.setUniform4f(uCropRect, rect.left, rect.top, rect.width(), rect.height())
    }
    
    fun setRotation(degrees: Float) {
        val radians = degrees * 3.14159265359f / 180f
        program?.setUniform1f(uRotation, radians)
    }
    
    fun setPerspectiveMatrix(matrix: android.graphics.Matrix?) {
        matrix?.let { m ->
            val values = FloatArray(9)
            m.getValues(values)
            program?.setUniformMatrix3fv(uPerspectiveMatrix, values)
        } ?: run {
            val identity = FloatArray(9).apply {
                this[0] = 1f; this[4] = 1f; this[8] = 1f
            }
            program?.setUniformMatrix3fv(uPerspectiveMatrix, identity)
        }
    }
    
    fun release() {
        program?.release()
    }
}

class ShaderPipeline(
    private val context: Context,
    private val quad: FullScreenQuad,
) {
    private val adjustmentShader = AdjustmentShader(context)
    private val lutShader = LutShader(context)
    private val cropShader = CropShader(context)
    
    private val mvpMatrix = FloatArray(16)
    private val cropMatrix = FloatArray(9)
    
    // Dirty flag tracking for performance optimization
    private var lastAdjustments: PhotoAdjustments? = null
    private var lastCropState: CropState? = null
    private var lastLutTexture: Texture3D? = null
    private var lastLutIntensity: Float = -1f
    private var needsRedraw = true
    
    init {
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(cropMatrix, 0)
    }
    
    private fun checkDirty(
        adjustments: PhotoAdjustments,
        cropState: CropState,
        lutTexture: Texture3D?,
        lutIntensity: Float,
    ): Boolean {
        if (needsRedraw) return true
        if (lastAdjustments != adjustments) return true
        if (lastCropState != cropState) return true
        if (lastLutTexture != lutTexture) return true
        if (lastLutIntensity != lutIntensity) return true
        return false
    }
    
    private fun updateDirtyState(
        adjustments: PhotoAdjustments,
        cropState: CropState,
        lutTexture: Texture3D?,
        lutIntensity: Float,
    ) {
        lastAdjustments = adjustments
        lastCropState = cropState
        lastLutTexture = lutTexture
        lastLutIntensity = lutIntensity
        needsRedraw = false
    }
    
    fun markDirty() {
        needsRedraw = true
    }
    
    fun renderToTexture(
        inputTexture: Texture2D,
        outputFramebuffer: Framebuffer,
        adjustments: PhotoAdjustments,
        cropState: CropState,
        lutTexture: Texture3D?,
        lutIntensity: Float,
    ) {
        if (!checkDirty(adjustments, cropState, lutTexture, lutIntensity)) {
            return // Skip render if nothing changed
        }
        
        outputFramebuffer.bind()
        GLES32.glClearColor(0f, 0f, 0f, 1f)
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
        
        inputTexture.bind(0)
        lutTexture?.bind(1)
        
        if (cropState.rect != RectF(0f, 0f, 1f, 1f) || cropState.rotationDegrees != 0f || cropState.perspectiveTransform != null) {
            cropShader.use()
            cropShader.setMVPMatrix(mvpMatrix)
            cropShader.setCropMatrix(cropMatrix)
            cropShader.setTextureUnit(0)
            cropShader.setCropRect(cropState)
            cropShader.setRotation(cropState.rotationDegrees)
            cropShader.setPerspectiveMatrix(cropState.perspectiveTransform)
            quad.draw()
        } else if (lutTexture != null && lutIntensity > 0f) {
            lutShader.use()
            lutShader.setMVPMatrix(mvpMatrix)
            lutShader.setCropMatrix(cropMatrix)
            lutShader.setTextureUnit(0)
            lutShader.setLutTextureUnit(1)
            lutShader.setLutIntensity(lutIntensity)
            quad.draw()
        } else {
            adjustmentShader.use()
            adjustmentShader.setMVPMatrix(mvpMatrix)
            adjustmentShader.setCropMatrix(cropMatrix)
            adjustmentShader.setTextureUnit(0)
            adjustmentShader.setLutTextureUnit(1)
            adjustmentShader.setLutIntensity(lutIntensity)
            adjustmentShader.setAdjustments(adjustments)
            quad.draw()
        }
        
        outputFramebuffer.unbind()
        updateDirtyState(adjustments, cropState, lutTexture, lutIntensity)
    }
    
    fun renderToScreen(
        inputTexture: Texture2D,
        adjustments: PhotoAdjustments,
        cropState: CropState,
        lutTexture: Texture3D?,
        lutIntensity: Float,
    ) {
        if (!checkDirty(adjustments, cropState, lutTexture, lutIntensity)) {
            return // Skip render if nothing changed
        }
        
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        
        inputTexture.bind(0)
        lutTexture?.bind(1)
        
        if (cropState.rect != RectF(0f, 0f, 1f, 1f) || cropState.rotationDegrees != 0f || cropState.perspectiveTransform != null) {
            cropShader.use()
            cropShader.setMVPMatrix(mvpMatrix)
            cropShader.setCropMatrix(cropMatrix)
            cropShader.setTextureUnit(0)
            cropShader.setCropRect(cropState)
            cropShader.setRotation(cropState.rotationDegrees)
            cropShader.setPerspectiveMatrix(cropState.perspectiveTransform)
            quad.draw()
        } else if (lutTexture != null && lutIntensity > 0f) {
            lutShader.use()
            lutShader.setMVPMatrix(mvpMatrix)
            lutShader.setCropMatrix(cropMatrix)
            lutShader.setTextureUnit(0)
            lutShader.setLutTextureUnit(1)
            lutShader.setLutIntensity(lutIntensity)
            quad.draw()
        } else {
            adjustmentShader.use()
            adjustmentShader.setMVPMatrix(mvpMatrix)
            adjustmentShader.setCropMatrix(cropMatrix)
            adjustmentShader.setTextureUnit(0)
            adjustmentShader.setLutTextureUnit(1)
            adjustmentShader.setLutIntensity(lutIntensity)
            adjustmentShader.setAdjustments(adjustments)
            quad.draw()
        }
        
        updateDirtyState(adjustments, cropState, lutTexture, lutIntensity)
    }
    
    fun release() {
        adjustmentShader.release()
        lutShader.release()
        cropShader.release()
        quad.release()
    }
}