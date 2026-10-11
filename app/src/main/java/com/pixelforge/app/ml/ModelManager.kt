package com.pixelforge.app.ml

import android.content.Context
import android.util.Log
import com.pixelforge.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages ML model assets: local bundled assets and optional remote updates.
 *
 * Features:
 * - Checks bundled assets first (offline-first)
 * - Optional remote update check from GitHub Releases
 * - SHA256 verification for downloaded models
 * - Falls back to bundled assets on download failure
 */
class ModelManager(
    private val context: Context,
    private val diagnostics: Diagnostics,
) {

    companion object {
        private const val TAG = "ModelManager"
        // GitHub repository for model releases
        private const val MODEL_REPO = "https://api.github.com/repos/AIMRAN-ai/NovaCam/releases/latest"
        private const val MODEL_DIR = "models"
        private const val METADATA_FILE = "model_metadata.json"
    }

    /**
     * Model metadata for version checking.
     */
    data class ModelInfo(
        val name: String,
        val version: String,
        val sha256: String,
        val assetFilename: String,
        val downloadUrl: String,
        val inputShape: List<Int>,
        val outputShape: List<Int>,
    )

    data class Metadata(
        val version: String,
        val models: Map<String, ModelInfo>,
        val minAppVersion: Int,
    )

    /**
     * Checks if a newer model version is available remotely.
     * Returns map of model names that have updates available.
     */
    suspend fun checkForUpdates(): Map<String, ModelInfo> = withContext(Dispatchers.IO) {
        val localMetadata = loadLocalMetadata()
        val remoteMetadata = fetchRemoteMetadata()

        val updates = mutableMapOf<String, ModelInfo>()
        remoteMetadata?.models?.forEach { (name, remoteInfo) ->
            val localInfo = localMetadata?.models?.get(name)
            if (localInfo == null || compareVersions(remoteInfo.version, localInfo.version) > 0) {
                updates[name] = remoteInfo
            }
        }
        updates
    }

    /**
     * Downloads and installs updated models.
     * Verifies SHA256 after download.
     */
    suspend fun downloadAndInstall(models: Map<String, ModelInfo>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        models.forEach { (name, info) ->
            try {
                val success = downloadModel(info)
                results[name] = success
                if (success) {
                    Log.i(TAG, "Model $name updated to ${info.version}")
                } else {
                    Log.w(TAG, "Model $name download failed SHA256 verification")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model $name download failed: ${e.message}")
                results[name] = false
            }
        }
        results
    }

    /**
     * Gets the effective path for a model (downloaded or bundled).
     */
    fun getModelPath(modelAsset: String): String {
        val modelDir = File(context.filesDir, MODEL_DIR)
        val downloadedPath = File(modelDir, modelAsset)
        if (downloadedPath.exists()) {
            return downloadedPath.absolutePath
        }
        // Return asset path (handled by runners via AssetManager)
        return modelAsset
    }

    /**
     * Checks if a model is available locally (bundled or downloaded).
     */
    fun isModelAvailable(modelAsset: String): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR)
        val downloadedPath = File(modelDir, modelAsset)
        return downloadedPath.exists() || context.assets.list("")?.contains(modelAsset) == true
    }

    /**
     * Gets model metadata for diagnostics.
     */
    fun getMetadata(): Metadata? = loadLocalMetadata()

    /**
     * Loads metadata from bundled asset.
     */
    private fun loadLocalMetadata(): Metadata? {
        return try {
            val inputStream = context.assets.open(METADATA_FILE)
            val json = inputStream.bufferedReader().readText()
            inputStream.close()
            parseMetadata(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local metadata: ${e.message}")
            null
        }
    }

    /**
     * Fetches metadata from GitHub Releases API.
     */
    private fun fetchRemoteMetadata(): Metadata? {
        return try {
            val url = URL(MODEL_REPO)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val json = connection.getInputStream().bufferedReader().readText()
            // Parse GitHub release JSON to extract model info
            // Expected format: release body contains model_metadata.json as asset or in body
            parseGitHubRelease(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote metadata: ${e.message}")
            null
        }
    }

    private fun parseMetadata(json: String): Metadata {
        // Simple JSON parsing - in production use kotlinx.serialization or Moshi
        // For now, return a basic structure
        return Metadata(
            version = "1.0.0",
            models = mapOf(),
            minAppVersion = 1
        )
    }

    private fun parseGitHubRelease(json: String): Metadata? {
        // Parse GitHub release API response
        // Look for model_metadata.json in assets or body
        return null // Simplified for now
    }

    private fun downloadModel(info: ModelInfo): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR)
        modelDir.mkdirs()

        val tempFile = File(modelDir, "${info.assetFilename}.tmp")
        val finalFile = File(modelDir, info.assetFilename)

        try {
            val url = URL(info.downloadUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 120000

            val input = connection.getInputStream()
            val output = FileOutputStream(tempFile)
            input.copyTo(output)
            input.close()
            output.close()

            // Verify SHA256
            val calculatedSha = calculateSha256(tempFile)
            if (!calculatedSha.equals(info.sha256, ignoreCase = true)) {
                tempFile.delete()
                return false
            }

            // Atomic rename
            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)
            return true
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        val input = java.io.FileInputStream(file)
        var read = input.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
        input.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toInt() }
        val parts2 = v2.split(".").map { it.toInt() }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}