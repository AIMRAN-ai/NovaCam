package com.pixelforge.app.video

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pixelforge.app.ui.MainActivity
import com.pixelforge.app.R

object NotificationHelper {

    const val CHANNEL_ID = "video_enhancement"
    const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Video enhancement",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress for on-device video super-resolution"
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun progressNotification(
        context: Context,
        progress: Int,
        tier: String,
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Enhancing video")
            .setContentText("$tier · $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setSilent(true)
    }

    fun completeNotification(
        context: Context,
        outputPath: String,
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Video enhanced")
            .setContentText("Saved to DCIM/PixelForge")
            .setAutoCancel(true)
            .setContentIntent(pending)
    }
}
