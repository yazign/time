package com.mystopwatch.app

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

private const val CHANNEL_ID = "running_stopwatches"

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_ID, "Запущенные секундомеры", NotificationManager.IMPORTANCE_LOW
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** Показывает по уведомлению на каждый запущенный секундомер. */
fun updateNotifications(context: Context, list: List<Stopwatch>) {
    val nm = NotificationManagerCompat.from(context)
    nm.cancelAll()
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    list.filter { it.running }.forEach { s ->
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(s.name)
            .setContentText("Секундомер запущен")
            .setWhen(s.startedAt!! - s.accumulated)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .build()
        try {
            nm.notify(s.id.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }
}
