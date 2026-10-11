package com.pixelforge.app.ml

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audio time-stretch wrapper.
 *
 * Uses the Sonic library (https://github.com/besherh/sonic-android) when available
 * for high-quality time-stretching. Falls back to a no-op passthrough when
 * the library is not present (e.g., in test environments or if JitPack unavailable).
 *
 * Sonic is a pure Java implementation of the WSOLA algorithm, providing good
 * quality for speech and music at various stretch ratios without JNI dependencies.
 */
class AudioTimeStretch {

    private var sonicInstance: Any? = null
    private var sonicAvailable = false

    companion object {
        private const val TAG = "AudioTimeStretch"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
    }

    init {
        // Try to load Sonic via reflection to avoid build-time dependency
        try {
            val sonicClass = Class.forName("com.besherh.sonic.Sonic")
            val constructor = sonicClass.getConstructor(Int::class.java, Int::class.java)
            sonicInstance = constructor.newInstance(SAMPLE_RATE, CHANNELS)
            sonicAvailable = true
            Log.i(TAG, "Sonic library loaded successfully")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Sonic library not found - audio time-stretch will be disabled")
            sonicAvailable = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sonic: ${e.message}")
            sonicAvailable = false
        }
    }

    /**
     * Checks if time-stretch functionality is available.
     */
    fun isAvailable(): Boolean = sonicAvailable

    /**
     * Time-stretches PCM audio data.
     *
     * @param inputPcm 16-bit PCM audio data (interleaved stereo if CHANNELS=2)
     * @param stretchRatio >1.0 slows down audio, <1.0 speeds up
     * @return Stretched PCM data, or original if Sonic unavailable
     */
    suspend fun stretch(inputPcm: ShortArray, stretchRatio: Float): ShortArray = withContext(Dispatchers.Default) {
        if (!sonicAvailable || stretchRatio <= 0f || Math.abs(stretchRatio - 1f) < 0.001f) {
            return@withContext inputPcm
        }

        val sonic = sonicInstance!!
        try {
            // Set speed (1.0 = normal, >1 = faster, <1 = slower)
            // For slow-motion video, we want audio to be slower, so speed = 1/stretchRatio
            val speed = 1f / stretchRatio
            sonic.javaClass.getMethod("setSpeed", Float::class.java).invoke(sonic, speed)

            // Feed input
            val writeSamples = sonic.javaClass.getMethod("writeSamples", ShortArray::class.java, Int::class.java)
            writeSamples.invoke(sonic, inputPcm, inputPcm.size)

            // Flush to get remaining samples
            val flushMethod = sonic.javaClass.getMethod("flushStream")
            flushMethod.invoke(sonic)

            // Read output
            val availableSamples = sonic.javaClass.getMethod("samplesAvailable").invoke(sonic) as Int
            val outputPcm = ShortArray(availableSamples)
            val readSamples = sonic.javaClass.getMethod("readSamples", ShortArray::class.java, Int::class.java)
            val readCount = readSamples.invoke(sonic, outputPcm, availableSamples) as Int

            if (readCount < availableSamples) {
                outputPcm.copyOf(readCount)
            } else {
                outputPcm
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sonic stretch failed: ${e.message}")
            inputPcm // Fallback to original
        }
    }

    /**
     * Releases native resources.
     */
    fun release() {
        if (sonicAvailable && sonicInstance != null) {
            try {
                sonicInstance!!.javaClass.getMethod("destroy").invoke(sonicInstance)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy Sonic: ${e.message}")
            }
            sonicInstance = null
            sonicAvailable = false
        }
    }
}