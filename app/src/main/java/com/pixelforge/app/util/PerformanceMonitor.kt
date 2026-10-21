package com.pixelforge.app.util

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Performance monitoring for tracking FPS, frame times, and memory.
 */
class PerformanceMonitor(private val scope: kotlinx.coroutines.CoroutineScope) : LifecycleObserver {
    
    private val frameTimes = mutableListOf<Long>()
    private var lastFrameTime = 0L
    private var frameCount = 0
    private val maxSamples = 120
    
    val fps = MutableStateFlow(0.0)
    val avgFrameTimeMs = MutableStateFlow(0.0)
    val minFrameTimeMs = MutableStateFlow(0.0)
    val maxFrameTimeMs = MutableStateFlow(0.0)
    val jankFrames = MutableStateFlow(0)
    
    private val jankThresholdMs = 16.67 // 60fps threshold
    
    fun onFrame() {
        val now = SystemClock.uptimeMillis()
        if (lastFrameTime > 0) {
            val frameTime = now - lastFrameTime
            frameTimes.add(frameTime)
            if (frameTimes.size > maxSamples) {
                frameTimes.removeAt(0)
            }
            
            frameCount++
            if (frameTime > jankThresholdMs) {
                jankFrames.value++
            }
            
            updateMetrics()
        }
        lastFrameTime = now
    }
    
    private fun updateMetrics() {
        if (frameTimes.isEmpty()) return
        
        val sum = frameTimes.sum()
        val avg = sum / frameTimes.size
        val min = frameTimes.minOrNull() ?: 0L
        val max = frameTimes.maxOrNull() ?: 0L
        
        fps.value = if (avg > 0) 1000.0 / avg else 0.0
        avgFrameTimeMs.value = avg.toDouble()
        minFrameTimeMs.value = min.toDouble()
        maxFrameTimeMs.value = max.toDouble()
    }
    
    fun reset() {
        frameTimes.clear()
        lastFrameTime = 0
        frameCount = 0
        jankFrames.value = 0
        fps.value = 0.0
        avgFrameTimeMs.value = 0.0
        minFrameTimeMs.value = 0.0
        maxFrameTimeMs.value = 0.0
    }
    
    fun getStats(): PerformanceStats {
        return PerformanceStats(
            fps = fps.value,
            avgFrameTimeMs = avgFrameTimeMs.value,
            minFrameTimeMs = minFrameTimeMs.value,
            maxFrameTimeMs = maxFrameTimeMs.value,
            jankFrames = jankFrames.value,
            totalFrames = frameCount,
        )
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        reset()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        // Keep stats for diagnostics
    }
    
    data class PerformanceStats(
        val fps: Double,
        val avgFrameTimeMs: Double,
        val minFrameTimeMs: Double,
        val maxFrameTimeMs: Double,
        val jankFrames: Int,
        val totalFrames: Int,
    )
}

/**
 * Simple FPS counter for UI display.
 */
class FpsCounter {
    private var lastTime = 0L
    private var frames = 0
    private var currentFps = 0.0
    
    fun tick(): Double {
        val now = SystemClock.uptimeMillis()
        frames++
        if (lastTime > 0) {
            val elapsed = now - lastTime
            if (elapsed >= 1000) {
                currentFps = frames * 1000.0 / elapsed
                frames = 0
                lastTime = now
            }
        } else {
            lastTime = now
        }
        return currentFps
    }
    
    fun getFps(): Double = currentFps
    fun reset() {
        lastTime = 0
        frames = 0
        currentFps = 0.0
    }
}