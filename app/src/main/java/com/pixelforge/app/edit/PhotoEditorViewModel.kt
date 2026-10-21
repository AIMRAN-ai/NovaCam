package com.pixelforge.app.edit

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableStateFlow
import androidx.lifecycle.viewModelScope
import android.opengl.GLES32
import com.pixelforge.app.edit.gpu.Framebuffer
import com.pixelforge.app.edit.gpu.ShaderPipeline
import com.pixelforge.app.edit.gpu.Texture2D
import com.pixelforge.app.edit.gpu.Texture3D
import com.pixelforge.app.util.BitmapPool
import com.pixelforge.app.util.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val imageLoader = ImageLoader.getInstance(context)
    private val bitmapPool = BitmapPool.getInstance()
    private val quad = FullScreenQuad()
    private val shaderPipeline = ShaderPipeline(context, quad)
    
    private var sourceTexture: Texture2D? = null
    private var workingTexture: Texture2D? = null
    private var outputFramebuffer: Framebuffer? = null
    private var currentLut: Texture3D? = null
    private var sourceBitmap: Bitmap? = null
    
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceUri: Uri? = null
    
    private val _adjustments = MutableStateFlow(PhotoAdjustments())
    val adjustments: StateFlow<PhotoAdjustments> = _adjustments.asStateFlow()
    
    private val _cropState = MutableStateFlow(CropState())
    val cropState: StateFlow<CropState> = _cropState.asStateFlow()
    
    private val _selectedPreset = MutableStateFlow<PhotoPreset?>(null)
    val selectedPreset: StateFlow<PhotoPreset?> = _selectedPreset.asStateFlow()
    
    private val _lutIntensity = MutableStateFlow(1f)
    val lutIntensity: StateFlow<Float> = _lutIntensity.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    private val _exportProgress = MutableStateFlow<Float>(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()
    
    private val undoStack = mutableListOf<PhotoAdjustments>()
    private val redoStack = mutableListOf<PhotoAdjustments>()
    private var isUndoRedo = false
    
    private val MAX_UNDO_STACK = 50
    
    enum class EditorTab { ADJUST, FILTERS, CROP }
    private val _currentTab = MutableStateFlow(EditorTab.ADJUST)
    val currentTab: StateFlow<EditorTab> = _currentTab.asStateFlow()
    
    fun setCurrentTab(tab: EditorTab) {
        _currentTab.value = tab
    }
    
    fun loadImage(uri: Uri) {
        _isLoading.value = true
        _lastError.value = null
        sourceUri = uri
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = imageLoader.loadForEditing(uri)
                if (bitmap == null || bitmap.isRecycled) {
                    throw IllegalArgumentException("Failed to load bitmap")
                }
                
                sourceWidth = bitmap.width
                sourceHeight = bitmap.height
                sourceBitmap = bitmap
                
                withContext(Dispatchers.Main) {
                    sourceTexture?.release()
                    workingTexture?.release()
                    outputFramebuffer?.release()
                    
                    sourceTexture = Texture2D.fromBitmap(bitmap)
                    workingTexture = Texture2D.create(sourceWidth, sourceHeight)
                    outputFramebuffer = Framebuffer.create(workingTexture!!)
                    
                    _adjustments.value = PhotoAdjustments()
                    _cropState.value = CropState()
                    _selectedPreset.value = null
                    _lutIntensity.value = 1f
                    undoStack.clear()
                    redoStack.clear()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _lastError.value = "Failed to load image: ${e.message}"
                    _isLoading.value = false
                }
            }
        }
    }
    
    fun updateAdjustment(key: String, value: Float) {
        if (isUndoRedo) return
        
        val current = _adjustments.value
        val newAdjustments = when (key) {
            "exposure" -> current.copy(exposure = value)
            "ev" -> current.copy(ev = value)
            "brilliance" -> current.copy(brilliance = value)
            "vignette" -> current.copy(vignette = value)
            "noiseReduction" -> current.copy(noiseReduction = value)
            "definition" -> current.copy(definition = value)
            "sharpness" -> current.copy(sharpness = value)
            "tint" -> current.copy(tint = value)
            "warmth" -> current.copy(warmth = value)
            "vibrance" -> current.copy(vibrance = value)
            "blackPoint" -> current.copy(blackPoint = value)
            "brightness" -> current.copy(brightness = value)
            "contrast" -> current.copy(contrast = value)
            "shadows" -> current.copy(shadows = value)
            "highlights" -> current.copy(highlights = value)
            else -> current
        }
        
        pushUndo(current)
        _adjustments.value = newAdjustments
        shaderPipeline.markDirty()
    }
    
    fun setAdjustments(newAdjustments: PhotoAdjustments) {
        if (isUndoRedo) return
        pushUndo(_adjustments.value)
        _adjustments.value = newAdjustments
        shaderPipeline.markDirty()
    }
    
    fun resetAdjustments() {
        pushUndo(_adjustments.value)
        _adjustments.value = PhotoAdjustments()
        shaderPipeline.markDirty()
    }
    
    fun updateCropState(newCropState: CropState) {
        if (isUndoRedo) return
        pushUndo(_adjustments.value)
        _cropState.value = newCropState
        shaderPipeline.markDirty()
    }
    
    fun setCropAspectRatio(ratio: AspectRatio?) {
        val current = _cropState.value
        _cropState.value = current.copy(aspectRatio = ratio)
        shaderPipeline.markDirty()
    }
    
    fun rotateCrop(degrees: Float) {
        val current = _cropState.value
        val newRotation = (current.rotationDegrees + degrees) % 360f
        _cropState.value = current.copy(rotationDegrees = newRotation)
        shaderPipeline.markDirty()
    }
    
    fun flipCropHorizontal() {
        val current = _cropState.value
        val matrix = Matrix()
        matrix.setScale(-1f, 1f)
        matrix.postTranslate(1f, 0f)
        _cropState.value = current.copy(perspectiveTransform = matrix)
        shaderPipeline.markDirty()
    }
    
    fun flipCropVertical() {
        val current = _cropState.value
        val matrix = Matrix()
        matrix.setScale(1f, -1f)
        matrix.postTranslate(0f, 1f)
        _cropState.value = current.copy(perspectiveTransform = matrix)
        shaderPipeline.markDirty()
    }
    
    fun applyPreset(preset: PhotoPreset) {
        pushUndo(_adjustments.value)
        _adjustments.value = preset.adjustments
        _selectedPreset.value = preset
        shaderPipeline.markDirty()
        
        if (preset.thumbnailLutPath != null) {
            loadLut(preset.thumbnailLutPath!!)
        } else {
            currentLut?.release()
            currentLut = null
        }
    }
    
    private fun loadLut(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val lut = Texture3D.fromCubeFile(context, path)
            withContext(Dispatchers.Main) {
                currentLut?.release()
                currentLut = lut
            }
        }
    }
    
    fun setLutIntensity(intensity: Float) {
        _lutIntensity.value = intensity.coerceIn(0f, 1f)
        shaderPipeline.markDirty()
    }
    
    fun clearPreset() {
        pushUndo(_adjustments.value)
        _selectedPreset.value = null
        currentLut?.release()
        currentLut = null
        shaderPipeline.markDirty()
    }
    
    private fun pushUndo(state: PhotoAdjustments) {
        undoStack.add(state)
        if (undoStack.size > MAX_UNDO_STACK) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }
    
    fun undo() {
        if (undoStack.isNotEmpty()) {
            isUndoRedo = true
            val current = _adjustments.value
            redoStack.add(current)
            val previous = undoStack.removeAt(undoStack.lastIndex)
            _adjustments.value = previous
            isUndoRedo = false
            shaderPipeline.markDirty()
        }
    }
    
    fun redo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedo = true
            val current = _adjustments.value
            undoStack.add(current)
            val next = redoStack.removeAt(redoStack.lastIndex)
            _adjustments.value = next
            isUndoRedo = false
            shaderPipeline.markDirty()
        }
    }
    
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    
    fun autoEnhance() {
        viewModelScope.launch(Dispatchers.IO) {
            val enhanced = AutoEnhance.analyzeAndEnhance(context, sourceUri!!)
            withContext(Dispatchers.Main) {
                pushUndo(_adjustments.value)
                _adjustments.value = enhanced
                _selectedPreset.value = null
                currentLut?.release()
                currentLut = null
                shaderPipeline.markDirty()
            }
        }
    }
    
    fun renderPreview(surfaceTexture: android.graphics.SurfaceTexture) {
        sourceTexture?.let { texture ->
            shaderPipeline.renderToScreen(
                texture,
                _adjustments.value,
                _cropState.value,
                currentLut,
                _lutIntensity.value
            )
        }
    }
    
    fun exportImage(): Uri? {
        return try {
            _exportProgress.value = 0.1f
            
            val bitmap = renderToBitmap()
            _exportProgress.value = 0.6f
            
            val uri = saveToMediaStore(bitmap)
            _exportProgress.value = 1f
            
            uri
        } catch (e: Exception) {
            _lastError.value = "Export failed: ${e.message}"
            null
        }
    }
    
    private fun renderToBitmap(): Bitmap {
        // Try to get bitmap from pool first
        val result = bitmapPool.get(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        
        val outputTexture = Texture2D.fromBitmap(result)
        val outputFb = Framebuffer.create(outputTexture)
        
        sourceTexture?.let { texture ->
            shaderPipeline.renderToTexture(
                texture,
                outputFb,
                _adjustments.value,
                _cropState.value,
                currentLut,
                _lutIntensity.value
            )
        }
        
        val buffer = java.nio.ByteBuffer.allocateDirect(sourceWidth * sourceHeight * 4)
            .order(java.nio.ByteOrder.nativeOrder())
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, outputFb.framebufferId)
        GLES32.glReadPixels(0, 0, sourceWidth, sourceHeight, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        result.copyPixelsFromBuffer(buffer)
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        
        outputFb.release()
        outputTexture.release()
        
        return result
    }
    
    private fun saveToMediaStore(bitmap: Bitmap): Uri {
        val resolver: ContentResolver = context.contentResolver
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = "pixelforge_edited_$timestamp.jpg"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PixelForge")
            }
        }
        
        val uri = resolver.insert(collection, contentValues)!!
        val outputStream: OutputStream = resolver.openOutputStream(uri)!!
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        outputStream.close()
        
        return uri
    }
    
    override fun onCleared() {
        super.onCleared()
        sourceTexture?.release()
        workingTexture?.release()
        outputFramebuffer?.release()
        currentLut?.release()
        shaderPipeline.release()
        quad.release()
        sourceBitmap?.let { bitmapPool.put(it) }
        sourceBitmap = null
    }
    
    companion object {
        fun generateFileName(extension: String): String {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(Date())
            return "pixelforge_$timestamp.$extension"
        }
    }
}