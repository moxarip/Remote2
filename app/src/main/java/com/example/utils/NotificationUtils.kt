package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.services.DismissNotificationReceiver

object NotificationUtils {

    const val CHANNEL_ADMIN_ALERTS_ID = "channel_admin_alerts"
    const val CHANNEL_ADMIN_ALERTS_NAME = "إشعارات الآدمن (Admin Alerts)"

    /**
     * Shows a high-priority heads-up notification at the top of the screen on the Host device.
     * When tapped, the notification automatically dismisses itself and DOES NOT open the app.
     */
    fun showAdminAlertNotification(
        context: Context,
        messageText: String,
        customTitle: String? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel with IMPORTANCE_HIGH so it pops up at the top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ADMIN_ALERTS_ID,
                CHANNEL_ADMIN_ALERTS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات وإشعارات مرسلة عن بُعد من هاتف الآدمن"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1000

        // Broadcast PendingIntent that dismisses the notification without opening any activity
        val dismissIntent = Intent(context, DismissNotificationReceiver::class.java).apply {
            putExtra(DismissNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (!customTitle.isNullOrBlank()) customTitle else "إشعار من الآدمن (Admin)"
        val body = if (messageText.isNotBlank()) messageText else "رسالة جديدة واردة من هاتف الآدمن"

        val notification = NotificationCompat.Builder(context, CHANNEL_ADMIN_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(dismissPendingIntent) // Critical: clicking dismisses without opening app!
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.i("NotificationUtils", "Admin alert notification posted with ID: $notificationId (autoCancel=true, dismissWithoutOpen=true)")
    }
}
