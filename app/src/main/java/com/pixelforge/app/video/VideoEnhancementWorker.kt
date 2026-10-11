package com.pixelforge.app.video

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixelforge.app.PixelForgeApp
import com.pixelforge.app.diagnostics.Diagnostics
import com.pixelforge.app.ml.RifeRunner
import com.pixelforge.app.ml.SrModelRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs [VideoUpscalePipeline] in the
 * :enhance process (isolated per TRD). Survives app death;
 * expedited on API 31+ for interactive jobs.
 */
class VideoEnhancementWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val diagnostics: Diagnostics
        get() = (applicationContext as? PixelForgeApp)?.diagnostics
            ?: Diagnostics()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationHelper.createChannel(applicationContext)
        val notification = NotificationHelper.progressNotification(applicationContext, 0, "starting")
        return ForegroundInfo(NotificationHelper.NOTIFICATION_ID, notification.build())
    }

    override suspend fun doWork(): Result {
        val inputPath = inputData.getString(KEY_INPUT_PATH)
            ?: return Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)
            ?: return Result.failure()
        val tierName = inputData.getString(KEY_TIER) ?: VideoQualityTier.ENHANCED_1080P.name
        val tier = try {
            VideoQualityTier.valueOf(tierName)
        } catch (e: IllegalArgumentException) {
            VideoQualityTier.ENHANCED_1080P
        }
        val useHevc = inputData.getBoolean(KEY_USE_HEVC, false)

        val app = applicationContext as? PixelForgeApp
        val srRunner: SrModelRunner = app?.esrgan ?: return Result.failure()
        val rifeRunner: RifeRunner? = app?.rife

        val pipeline = VideoUpscalePipeline(applicationContext, diagnostics, srRunner, rifeRunner)
        val config = VideoUpscalePipeline.Config(
            inputPath = inputPath,
            outputPath = outputPath,
            tier = tier,
            useHevc = useHevc,
        )

        var lastProgress = 0
        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            // Progress polling loop: surfaces pipeline progress
            // to the foreground notification.
            while (isActive) {
                val state = diagnostics.state.value.videoState
                val progress = (state.progress * 100).toInt()
                if (progress > lastProgress) {
                    lastProgress = progress
                    setForeground(
                        ForegroundInfo(
                            NotificationHelper.NOTIFICATION_ID,
                            NotificationHelper.progressNotification(applicationContext, progress, tier.label).build()
                        )
                    )
                }
                kotlinx.coroutines.delay(500L)
            }
        }

        return try {
            val result = pipeline.run(config)
            job.cancel()
            setForeground(
                ForegroundInfo(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.completeNotification(applicationContext, result.outputPath).build()
                )
            )
            Result.success()
        } catch (e: CancellationException) {
            job.cancel()
            throw e
        } catch (e: Exception) {
            job.cancel()
            diagnostics.recordVideoError("worker: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_TIER = "tier"
        const val KEY_USE_HEVC = "use_hevc"
        const val WORK_TAG = "video_enhancement"

        /**
         * Enqueues an enhancement job. Uses expedited scheduling
         * on API 31+ (falls back to normal when quota is
         * exhausted). Constraints: charging OR battery >= 40%,
         * per TRD; user override bypasses via [force].
         */
        fun enqueue(
            context: Context,
            inputPath: String,
            outputPath: String,
            tier: VideoQualityTier,
            useHevc: Boolean = false,
            force: Boolean = false,
        ) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .apply {
                    if (!force) {
                        setRequiresCharging(true)
                    }
                }
                .build()

            val request = OneTimeWorkRequestBuilder<VideoEnhancementWorker>()
                .setConstraints(constraints)
                .setInputData(
                    androidx.work.Data.Builder()
                        .putString(KEY_INPUT_PATH, inputPath)
                        .putString(KEY_OUTPUT_PATH, outputPath)
                        .putString(KEY_TIER, tier.name)
                        .putBoolean(KEY_USE_HEVC, useHevc)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply {
                    if (Build.VERSION.SDK_INT >= 31) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_TAG,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
