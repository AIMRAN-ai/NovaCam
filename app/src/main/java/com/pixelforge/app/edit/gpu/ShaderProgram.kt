package com.pixelforge.app.edit.gpu

import android.content.Context
import android.opengl.GLES32
import android.opengl.GLUtils
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ShaderProgram(
    val programId: Int,
    val vertexShaderId: Int,
    val fragmentShaderId: Int,
) {
    companion object {
        fun create(context: Context, vertexAsset: String, fragmentAsset: String): ShaderProgram? {
            val vertexSource = readShaderSource(context, vertexAsset)
            val fragmentSource = readShaderSource(context, fragmentAsset)
            if (vertexSource == null || fragmentSource == null) return null
            
            val vertexShader = compileShader(GLES32.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource)
            if (vertexShader == 0 || fragmentShader == 0) return null
            
            val program = linkProgram(vertexShader, fragmentShader)
            if (program == 0) return null
            
            return ShaderProgram(program, vertexShader, fragmentShader)
        }
        
        private fun readShaderSource(context: Context, assetPath: String): String? {
            return try {
                val inputStream = context.assets.open(assetPath)
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } catch (e: Exception) {
                Log.e("ShaderProgram", "Failed to read shader: $assetPath", e)
                null
            }
        }
        
        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES32.glCreateShader(type)
            GLES32.glShaderSource(shader, source)
            GLES32.glCompileShader(shader)
            val status = IntArray(1)
            GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("ShaderProgram", "Shader compile error: ${GLES32.glGetShaderInfoLog(shader)}")
                GLES32.glDeleteShader(shader)
                return 0
            }
            return shader
        }
        
        private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
            val program = GLES32.glCreateProgram()
            GLES32.glAttachShader(program, vertexShader)
            GLES32.glAttachShader(program, fragmentShader)
            GLES32.glLinkProgram(program)
            val status = IntArray(1)
            GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("ShaderProgram", "Program link error: ${GLES32.glGetProgramInfoLog(program)}")
                GLES32.glDeleteProgram(program)
                return 0
            }
            return program
        }
    }
    
    fun use() {
        GLES32.glUseProgram(programId)
    }
    
    fun getUniformLocation(name: String): Int {
        return GLES32.glGetUniformLocation(programId, name)
    }
    
    fun getAttribLocation(name: String): Int {
        return GLES32.glGetAttribLocation(programId, name)
    }
    
    fun setUniform1f(location: Int, value: Float) {
        GLES32.glUniform1f(location, value)
    }
    
    fun setUniform1i(location: Int, value: Int) {
        GLES32.glUniform1i(location, value)
    }
    
    fun setUniformMatrix4fv(location: Int, matrix: FloatArray) {
        GLES32.glUniformMatrix4fv(location, 1, false, matrix, 0)
    }
    
    fun setUniformMatrix3fv(location: Int, matrix: FloatArray) {
        GLES32.glUniformMatrix3fv(location, 1, false, matrix, 0)
    }
    
    fun setUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) {
        GLES32.glUniform4f(location, x, y, z, w)
    }
    
    fun release() {
        GLES32.glDeleteProgram(programId)
        GLES32.glDeleteShader(vertexShaderId)
        GLES32.glDeleteShader(fragmentShaderId)
    }
}

class Texture2D(
    val textureId: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun create(width: Int, height: Int, format: Int = GLES32.GL_RGBA): Texture2D {
            val textures = IntArray(1)
            GLES32.glGenTextures(1, textures, 0)
            val textureId = textures[0]
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
            GLES32.glTexImage2D(GLES32.GL_TEXTURE_2D, 0, format, width, height, 0, format, GLES32.GL_UNSIGNED_BYTE, null)
            return Texture2D(textureId, width, height)
        }
        
        fun fromBitmap(bitmap: android.graphics.Bitmap): Texture2D {
            val textures = IntArray(1)
            GLES32.glGenTextures(1, textures, 0)
            val textureId = textures[0]
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES32.GL_TEXTURE_2D, 0, bitmap, 0)
            return Texture2D(textureId, bitmap.width, bitmap.height)
        }
    }
    
    fun bind(textureUnit: Int = 0) {
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0 + textureUnit)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
    }
    
    fun release() {
        GLES32.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}

class Texture3D(
    val textureId: Int,
    val size: Int,
) {
    companion object {
        fun fromCubeFile(context: Context, assetPath: String, size: Int = 32): Texture3D? {
            return try {
                val inputStream = context.assets.open(assetPath)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                reader.close()
                
                val data = mutableListOf<Float>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("LUT_3D_SIZE")) continue
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        data.add(parts[0].toFloat())
                        data.add(parts[1].toFloat())
                        data.add(parts[2].toFloat())
                    }
                }
                
                val expectedSize = size * size * size * 3
                if (data.size != expectedSize) {
                    Log.w("Texture3D", "LUT data size mismatch: got ${data.size}, expected $expectedSize")
                }
                
                val floatArray = FloatArray(data.size) { data[it] }
                val floatBuffer = ByteBuffer.allocateDirect(floatArray.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply { put(floatArray); position(0) }
                val textures = IntArray(1)
                GLES32.glGenTextures(1, textures, 0)
                val textureId = textures[0]
                GLES32.glBindTexture(GLES32.GL_TEXTURE_3D, textureId)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_WRAP_R, GLES32.GL_CLAMP_TO_EDGE)
                GLES32.glTexImage3D(GLES32.GL_TEXTURE_3D, 0, GLES32.GL_RGB, size, size, size, 0, GLES32.GL_RGB, GLES32.GL_FLOAT, floatBuffer)
                
                Texture3D(textureId, size)
            } catch (e: Exception) {
                Log.e("Texture3D", "Failed to load LUT: $assetPath", e)
                null
            }
        }
    }
    
    fun bind(textureUnit: Int = 0) {
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0 + textureUnit)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_3D, textureId)
    }
    
    fun release() {
        GLES32.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}

class Framebuffer(
    val framebufferId: Int,
    val texture: Texture2D,
) {
    companion object {
        fun create(texture: Texture2D): Framebuffer {
            val fbos = IntArray(1)
            GLES32.glGenFramebuffers(1, fbos, 0)
            val framebufferId = fbos[0]
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, framebufferId)
            GLES32.glFramebufferTexture2D(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0, GLES32.GL_TEXTURE_2D, texture.textureId, 0)
            val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
            if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
                throw IllegalStateException("Framebuffer incomplete: $status")
            }
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
            return Framebuffer(framebufferId, texture)
        }
    }
    
    fun bind() {
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, framebufferId)
        GLES32.glViewport(0, 0, texture.width, texture.height)
    }
    
    fun unbind() {
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
    }
    
    fun release() {
        GLES32.glDeleteFramebuffers(1, intArrayOf(framebufferId), 0)
        texture.release()
    }
}

class FullScreenQuad {
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private val vertexCount = 6
    
    init {
        GLES32.glGenVertexArrays(1, vao, 0)
        GLES32.glGenBuffers(1, vbo, 0)
        
        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
            -1f,  1f, 0f, 1f,
             1f, -1f, 1f, 0f,
             1f,  1f, 1f, 1f,
        )
        
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }
        
        GLES32.glBindVertexArray(vao[0])
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo[0])
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES32.GL_STATIC_DRAW)
        GLES32.glEnableVertexAttribArray(0)
        GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, 16, 0)
        GLES32.glEnableVertexAttribArray(1)
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, 16, 8)
        GLES32.glBindVertexArray(0)
    }
    
    fun draw() {
        GLES32.glBindVertexArray(vao[0])
        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, vertexCount)
        GLES32.glBindVertexArray(0)
    }
    
    fun release() {
        GLES32.glDeleteVertexArrays(1, vao, 0)
        GLES32.glDeleteBuffers(1, vbo, 0)
    }
}