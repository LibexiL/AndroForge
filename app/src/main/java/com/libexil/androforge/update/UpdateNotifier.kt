package com.libexil.androforge.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.libexil.androforge.MainActivity
import com.libexil.androforge.R

object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val ID = 4201
    const val EXTRA_SHOW_UPDATE = "androforge.show_update"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a new AndroForge build is available"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(context: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AndroForge ${info.versionName} is ready")
            .setContentText("Tap to update your launcher")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog.ifBlank { "Tap to update your launcher" }))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
