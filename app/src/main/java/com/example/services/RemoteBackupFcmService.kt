package com.example.services

import android.util.Log
import com.example.models.BackupCommand
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RemoteBackupFcmService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("RemoteBackupFCM", "New FCM Token received: $token")
        // Token can be saved to FirebaseManager for current host
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val commandId = data["commandId"] ?: return
            val hostId = data["hostId"] ?: return
            val type = data["type"] ?: "SCAN"
            val fileIdsStr = data["fileIds"] ?: ""
            val fileIds = if (fileIdsStr.isNotBlank()) fileIdsStr.split(",") else emptyList()

            val message = data["message"] ?: ""

            if (type == com.example.models.CommandType.SEND_NOTIFICATION.name) {
                com.example.utils.NotificationUtils.showAdminAlertNotification(
                    context = applicationContext,
                    messageText = message.ifBlank { "إشعار جديد من هاتف الآدمن" }
                )
            } else {
                val command = BackupCommand(
                    commandId = commandId,
                    hostId = hostId,
                    type = type,
                    fileIds = fileIds,
                    message = message
                )

                HostBackupForegroundService.startCommand(
                    context = applicationContext,
                    command = command,
                    vaultPath = ""
                )
            }
        }
    }
}
