package com.pixelforge.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rxjs2.RxDataStore
import androidx.datastore.preferences.rxjs2.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Settings manager using DataStore for persistence.
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        // Keys
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CAPTURE_MODE = stringPreferencesKey("default_capture_mode")
        val DEFAULT_QUALITY_TIER = stringPreferencesKey("default_quality_tier")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val GRID_TYPE = stringPreferencesKey("grid_type")
        val AUTO_ENHANCE = booleanPreferencesKey("auto_enhance")
        val EXPORT_QUALITY = stringPreferencesKey("export_quality")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val DELEGATE_PREFERENCE = stringPreferencesKey("delegate_preference")
        val SHOW_HISTOGRAM = booleanPreferencesKey("show_histogram")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        
        // Theme modes
        val THEME_SYSTEM = "system"
        val THEME_LIGHT = "light"
        val THEME_DARK = "dark"
        
        // Grid types
        val GRID_NONE = "none"
        val GRID_THIRDS = "thirds"
        val GRID_SQUARE = "square"
        val GRID_GOLDEN = "golden"
        
        // Export quality
        val EXPORT_LOW = "low"
        val EXPORT_MEDIUM = "medium"
        val EXPORT_HIGH = "high"
        val EXPORT_MAX = "max"
        
        // Export format
        val FORMAT_JPEG = "jpeg"
        val FORMAT_HEIC = "heic"
        val FORMAT_PNG = "png"
        
        // Delegate preferences
        val DELEGATE_AUTO = "auto"
        val DELEGATE_GPU = "gpu"
        val DELEGATE_NNAPI = "nnapi"
        val DELEGATE_CPU = "cpu"
    }
    
    private val dataStore = context.preferencesDataStore("pixelforge_settings")
    
    // Reactive state flows
    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    val themeMode: Flow<String> = _themeMode.asStateFlow().distinctUntilChanged()
    
    private val _defaultCaptureMode = MutableStateFlow("BURST_YUV")
    val defaultCaptureMode: Flow<String> = _defaultCaptureMode.asStateFlow().distinctUntilChanged()
    
    private val _defaultQualityTier = MutableStateFlow("BALANCED")
    val defaultQualityTier: Flow<String> = _defaultQualityTier.asStateFlow().distinctUntilChanged()
    
    private val _defaultVideoQuality = MutableStateFlow("ENHANCED_1080P")
    val defaultVideoQuality: Flow<String> = _defaultVideoQuality.asStateFlow().distinctUntilChanged()
    
    private val _gridType = MutableStateFlow(GRID_NONE)
    val gridType: Flow<String> = _gridType.asStateFlow().distinctUntilChanged()
    
    private val _autoEnhance = MutableStateFlow(false)
    val autoEnhance: Flow<Boolean> = _autoEnhance.asStateFlow().distinctUntilChanged()
    
    private val _exportQuality = MutableStateFlow(EXPORT_HIGH)
    val exportQuality: Flow<String> = _exportQuality.asStateFlow().distinctUntilChanged()
    
    private val _exportFormat = MutableStateFlow(FORMAT_JPEG)
    val exportFormat: Flow<String> = _exportFormat.asStateFlow().distinctUntilChanged()
    
    private val _delegatePreference = MutableStateFlow(DELEGATE_AUTO)
    val delegatePreference: Flow<String> = _delegatePreference.asStateFlow().distinctUntilChanged()
    
    private val _showHistogram = MutableStateFlow(false)
    val showHistogram: Flow<Boolean> = _showHistogram.asStateFlow().distinctUntilChanged()
    
    private val _saveOriginal = MutableStateFlow(true)
    val saveOriginal: Flow<Boolean> = _saveOriginal.asStateFlow().distinctUntilChanged()
    
    private val _hapticFeedback = MutableStateFlow(true)
    val hapticFeedback: Flow<Boolean> = _hapticFeedback.asStateFlow().distinctUntilChanged()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        runBlocking {
            dataStore.data.firstOrNull()?.let { prefs ->
                _themeMode.value = prefs[THEME_MODE] ?: THEME_SYSTEM
                _defaultCaptureMode.value = prefs[DEFAULT_CAPTURE_MODE] ?: "BURST_YUV"
                _defaultQualityTier.value = prefs[DEFAULT_QUALITY_TIER] ?: "BALANCED"
                _defaultVideoQuality.value = prefs[DEFAULT_VIDEO_QUALITY] ?: "ENHANCED_1080P"
                _gridType.value = prefs[GRID_TYPE] ?: GRID_NONE
                _autoEnhance.value = prefs[AUTO_ENHANCE] ?: false
                _exportQuality.value = prefs[EXPORT_QUALITY] ?: EXPORT_HIGH
                _exportFormat.value = prefs[EXPORT_FORMAT] ?: FORMAT_JPEG
                _delegatePreference.value = prefs[DELEGATE_PREFERENCE] ?: DELEGATE_AUTO
                _showHistogram.value = prefs[SHOW_HISTOGRAM] ?: false
                _saveOriginal.value = prefs[SAVE_ORIGINAL] ?: true
                _hapticFeedback.value = prefs[HAPTIC_FEEDBACK] ?: true
            }
        }
    }
    
    private suspend fun save(key: Preferences.Key<Any>, value: Any) {
        dataStore.edit { prefs ->
            when (value) {
                is String -> prefs[key as Preferences.Key<String>] = value
                is Boolean -> prefs[key as Preferences.Key<Boolean>] = value
                is Int -> prefs[key as Preferences.Key<Int>] = value
                is Long -> prefs[key as Preferences.Key<Long>] = value
                is Float -> prefs[key as Preferences.Key<Float>] = value
            }
        }
    }
    
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        save(THEME_MODE, mode)
    }
    
    fun setDefaultCaptureMode(mode: String) {
        _defaultCaptureMode.value = mode
        save(DEFAULT_CAPTURE_MODE, mode)
    }
    
    fun setDefaultQualityTier(tier: String) {
        _defaultQualityTier.value = tier
        save(DEFAULT_QUALITY_TIER, tier)
    }
    
    fun setDefaultVideoQuality(quality: String) {
        _defaultVideoQuality.value = quality
        save(DEFAULT_VIDEO_QUALITY, quality)
    }
    
    fun setGridType(grid: String) {
        _gridType.value = grid
        save(GRID_TYPE, grid)
    }
    
    fun setAutoEnhance(enabled: Boolean) {
        _autoEnhance.value = enabled
        save(AUTO_ENHANCE, enabled)
    }
    
    fun setExportQuality(quality: String) {
        _exportQuality.value = quality
        save(EXPORT_QUALITY, quality)
    }
    
    fun setExportFormat(format: String) {
        _exportFormat.value = format
        save(EXPORT_FORMAT, format)
    }
    
    fun setDelegatePreference(delegate: String) {
        _delegatePreference.value = delegate
        save(DELEGATE_PREFERENCE, delegate)
    }
    
    fun setShowHistogram(show: Boolean) {
        _showHistogram.value = show
        save(SHOW_HISTOGRAM, show)
    }
    
    fun setSaveOriginal(save: Boolean) {
        _saveOriginal.value = save
        save(SAVE_ORIGINAL, save)
    }
    
    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
        save(HAPTIC_FEEDBACK, enabled)
    }
    
    // Synchronous getters for immediate access
    fun getThemeMode(): String = _themeMode.value
    fun getDefaultCaptureMode(): String = _defaultCaptureMode.value
    fun getDefaultQualityTier(): String = _defaultQualityTier.value
    fun getDefaultVideoQuality(): String = _defaultVideoQuality.value
    fun getGridType(): String = _gridType.value
    fun getAutoEnhance(): Boolean = _autoEnhance.value
    fun getExportQuality(): String = _exportQuality.value
    fun getExportFormat(): String = _exportFormat.value
    fun getDelegatePreference(): String = _delegatePreference.value
    fun getShowHistogram(): Boolean = _showHistogram.value
    fun getSaveOriginal(): Boolean = _saveOriginal.value
    fun getHapticFeedback(): Boolean = _hapticFeedback.value
}

/**
 * Legacy SharedPreferences-based settings (fallback).
 */
class LegacySettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pixelforge_settings", Context.MODE_PRIVATE)
    
    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
}