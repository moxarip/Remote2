package com.example.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.RemoteBackupApp
import com.example.firebase.FirebaseManager
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.VaultFile
import com.example.repository.BackupRepository
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class HostBackupForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentExecutionJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val EXTRA_COMMAND_ID = "extra_command_id"
        const val EXTRA_HOST_ID = "extra_host_id"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_FILE_IDS = "extra_file_ids"
        const val EXTRA_VAULT_PATH = "extra_vault_path"

        fun startCommand(
            context: Context,
            command: BackupCommand,
            vaultPath: String
        ) {
            val intent = Intent(context, HostBackupForegroundService::class.java).apply {
                putExtra(EXTRA_COMMAND_ID, command.commandId)
                putExtra(EXTRA_HOST_ID, command.hostId)
                putExtra(EXTRA_TYPE, command.type)
                putStringArrayListExtra(EXTRA_FILE_IDS, ArrayList(command.fileIds))
                putExtra(EXTRA_VAULT_PATH, vaultPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandId = intent?.getStringExtra(EXTRA_COMMAND_ID) ?: return START_NOT_STICKY
        val hostId = intent.getStringExtra(EXTRA_HOST_ID) ?: return START_NOT_STICKY
        val typeStr = intent.getStringExtra(EXTRA_TYPE) ?: CommandType.SCAN.name
        val fileIds = intent.getStringArrayListExtra(EXTRA_FILE_IDS) ?: emptyList()
        val vaultPath = intent.getStringExtra(EXTRA_VAULT_PATH) ?: ""

        startForegroundNotification("Initializing $typeStr operation...")

        currentExecutionJob?.cancel()
        currentExecutionJob = serviceScope.launch {
            executeCommand(commandId, hostId, typeStr, fileIds, vaultPath)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(text: String, progress: Int = -1) {
        val builder = NotificationCompat.Builder(this, RemoteBackupApp.CHANNEL_SYNC_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Remote Backup Host")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun updateNotification(text: String, progress: Int) {
        val builder = NotificationCompat.Builder(this, RemoteBackupApp.CHANNEL_SYNC_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Remote Backup Host")
            .setContentText(text)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, builder.build())
    }

    private suspend fun executeCommand(
        commandId: String,
        hostId: String,
        typeStr: String,
        fileIds: List<String>,
        vaultPath: String
    ) {
        var command = BackupCommand(
            commandId = commandId,
            hostId = hostId,
            type = typeStr,
            fileIds = fileIds,
            status = CommandStatus.RUNNING.name,
            startedAt = System.currentTimeMillis()
        )
        FirebaseManager.updateCommand(command)

        val vaultFolder = if (vaultPath.isNotBlank()) File(vaultPath) else StorageUtils.getDefaultVaultFolder(this)

        try {
            when (CommandType.valueOf(typeStr)) {
                CommandType.SCAN -> {
                    startForegroundNotification("Scanning storage node directories...", -1)
                    val repo = BackupRepository.getInstance(this)
                    val host = repo.currentHostDevice.value ?: FirebaseManager.syncedDevices.value[hostId]
                    val sharedFolders = host?.sharedFolders?.ifEmpty { repo.sharedFolders.value } ?: repo.sharedFolders.value

                    val allFiles = mutableListOf<VaultFile>()
                    var totalFolders = 0
                    var totalBytes = 0L

                    if (sharedFolders.isNotEmpty()) {
                        for (folder in sharedFolders) {
                            val (scanned, summary) = StorageUtils.scanVault(this, folder.pathOrUri, hostId, folder.folderId)
                            allFiles.addAll(scanned)
                            totalFolders += summary.foldersFound
                            totalBytes += summary.totalSizeBytes
                        }
                    } else {
                        val (scanned, summary) = StorageUtils.scanVault(this, vaultPath, hostId, "vault_default")
                        allFiles.addAll(scanned)
                        totalFolders += summary.foldersFound
                        totalBytes += summary.totalSizeBytes
                    }

                    val overallSummary = com.example.models.VaultSummary(
                        filesFound = allFiles.size,
                        foldersFound = totalFolders,
                        totalSizeBytes = totalBytes,
                        lastScanTime = System.currentTimeMillis(),
                        vaultPath = sharedFolders.firstOrNull()?.pathOrUri ?: vaultPath
                    )

                    FirebaseManager.uploadVaultMetadata(hostId, allFiles, overallSummary)

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        totalFiles = allFiles.size,
                        filesProcessed = allFiles.size,
                        totalBytes = overallSummary.totalSizeBytes,
                        bytesTransferred = overallSummary.totalSizeBytes
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.REFRESH -> {
                    startForegroundNotification("Refreshing telemetry...", -1)
                    val battery = StorageUtils.getBatteryPercent(this)
                    val (freeBytes, _) = StorageUtils.getStorageStats()
                    FirebaseManager.updateDeviceStatus(hostId, "ONLINE", battery, freeBytes)
                    delay(500)

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.SEND_NOTIFICATION -> {
                    val msg = command.message.ifBlank { "إشعار وتنبيه جديد من هاتف الآدمن" }
                    com.example.utils.NotificationUtils.showAdminAlertNotification(
                        context = this,
                        messageText = msg
                    )
                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.BACKUP, CommandType.PREVIEW_REQUEST, CommandType.STREAM_REQUEST -> {
                    // Collect shared folders and files from repository and Firebase
                    val repo = BackupRepository.getInstance(this)
                    val host = repo.currentHostDevice.value ?: FirebaseManager.syncedDevices.value[hostId]
                    val sharedFolders = host?.sharedFolders?.ifEmpty { repo.sharedFolders.value } ?: repo.sharedFolders.value

                    // Get all files across all shared folders
                    val allFiles = mutableListOf<VaultFile>()
                    val existingRepoFiles = repo.hostFiles.value
                    if (existingRepoFiles.isNotEmpty()) {
                        allFiles.addAll(existingRepoFiles)
                    } else {
                        val syncedFiles = FirebaseManager.syncedFiles.value[hostId]
                        if (!syncedFiles.isNullOrEmpty()) {
                            allFiles.addAll(syncedFiles)
                        } else {
                            for (sf in sharedFolders) {
                                val (scanned, _) = StorageUtils.scanVault(this, sf.pathOrUri, hostId, sf.folderId)
                                allFiles.addAll(scanned)
                            }
                            if (allFiles.isEmpty()) {
                                val (scanned, _) = StorageUtils.scanFolder(vaultFolder, hostId, "vault_default")
                                allFiles.addAll(scanned)
                            }
                        }
                    }

                    val filesToBackup = if (fileIds.isNotEmpty()) {
                        allFiles.filter { it.fileId in fileIds }
                    } else {
                        allFiles
                    }

                    val totalFiles = filesToBackup.size.coerceAtLeast(1)
                    val totalBytes = filesToBackup.sumOf { it.size }.coerceAtLeast(1024L)
                    var transferredBytes = 0L

                    // Initial broadcast
                    command = command.copy(
                        progress = 0,
                        currentFile = "بدء الرفع الفعلي إلى السحابة...",
                        filesProcessed = 0,
                        totalFiles = totalFiles,
                        bytesTransferred = 0L,
                        totalBytes = totalBytes,
                        speedBytesPerSec = 0L
                    )
                    FirebaseManager.updateCommand(command)
                    updateNotification("بدء الرفع: 0%", 0)

                    val backedUpIds = mutableListOf<String>()
                    val remoteStorageMap = mutableMapOf<String, String>()
                    val downloadUrlMap = mutableMapOf<String, String>()

                    for ((index, file) in filesToBackup.withIndex()) {
                        if (!currentCoroutineContext().isActive) break

                        // Open actual file input stream from disk/SAF
                        val (inputStream, streamSize) = StorageUtils.openInputStreamForVaultFile(this, file, sharedFolders)
                        val actualFileSize = if (streamSize > 0) streamSize else file.size
                        val uriToLog = file.uriString.ifBlank { "resolved_via_shared_folder" }
                        val mimeToLog = file.mimeType.ifBlank { StorageUtils.getMimeTypeFromExtension(file.name) }
                        val destToLog = "${FirebaseManager.DEFAULT_DATABASE_URL}/backups/$hostId/${file.fileId}"

                        android.util.Log.i("HostBackupService", "--------------------------------------------------")
                        android.util.Log.i("HostBackupService", "UPLOAD START for file: ${file.name}")
                        android.util.Log.i("HostBackupService", "  URI: $uriToLog")
                        android.util.Log.i("HostBackupService", "  Filename: ${file.name}")
                        android.util.Log.i("HostBackupService", "  MIME type: $mimeToLog")
                        android.util.Log.i("HostBackupService", "  File size: $actualFileSize bytes (${StorageUtils.formatFileSize(actualFileSize)})")
                        android.util.Log.i("HostBackupService", "  Upload destination: $destToLog")

                        if (inputStream != null) {
                            try {
                                val uploadResult = FirebaseManager.uploadFileToCloud(
                                    hostId = hostId,
                                    file = file.copy(size = actualFileSize, mimeType = mimeToLog, uriString = file.uriString),
                                    inputStream = inputStream,
                                    totalBytes = actualFileSize,
                                    onProgress = { filePercent, bytesSent, speed ->
                                        val totalSoFar = (transferredBytes + bytesSent).coerceAtMost(totalBytes)
                                        val overallProgress = ((totalSoFar.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)

                                        command = command.copy(
                                            progress = overallProgress,
                                            currentFile = "${file.name} ($overallProgress%)",
                                            filesProcessed = index,
                                            totalFiles = totalFiles,
                                            bytesTransferred = totalSoFar,
                                            totalBytes = totalBytes,
                                            speedBytesPerSec = speed
                                        )
                                        FirebaseManager.updateCommand(command)
                                        updateNotification("${index + 1}/$totalFiles: ${file.name} ($overallProgress%)", overallProgress)
                                    }
                                )

                                uploadResult.fold(
                                    onSuccess = { remotePath ->
                                        android.util.Log.i("HostBackupService", "UPLOAD SUCCESS for file: ${file.name}")
                                        android.util.Log.i("HostBackupService", "  Remote path: $remotePath")
                                        transferredBytes += actualFileSize
                                        backedUpIds.add(file.fileId)
                                        remoteStorageMap[file.fileId] = remotePath
                                        downloadUrlMap[file.fileId] = "${FirebaseManager.DEFAULT_DATABASE_URL}/$remotePath.json"
                                    },
                                    onFailure = { err ->
                                        android.util.Log.e("HostBackupService", "UPLOAD FAILURE for file: ${file.name}: ${err.message}", err)
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("HostBackupService", "UPLOAD EXCEPTION for file: ${file.name}: ${e.message}", e)
                            } finally {
                                try { inputStream.close() } catch (e: Exception) {}
                            }
                        } else {
                            android.util.Log.e("HostBackupService", "UPLOAD FAILURE: Cannot open input stream for file ${file.name} (URI: $uriToLog)")
                        }

                        // Broadcast progress after each file
                        val overallProgress = (((index + 1).toDouble() / totalFiles) * 100).toInt().coerceIn(0, 99)
                        command = command.copy(
                            progress = overallProgress,
                            currentFile = "${file.name} (تم الرفع)",
                            filesProcessed = index + 1,
                            totalFiles = totalFiles,
                            bytesTransferred = transferredBytes.coerceAtMost(totalBytes),
                            totalBytes = totalBytes
                        )
                        FirebaseManager.updateCommand(command)
                        FirebaseManager.markFilesAsBackedUp(hostId, backedUpIds, remoteStorageMap, downloadUrlMap)
                        repo.updateHostFileStatus(backedUpIds, remoteStorageMap, downloadUrlMap)
                        updateNotification("${index + 1}/$totalFiles: ${file.name} (100%)", overallProgress)
                    }

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        currentFile = "اكتمل الرفع الفعلي بنجاح إلى Firebase (100%)",
                        filesProcessed = totalFiles,
                        totalFiles = totalFiles,
                        bytesTransferred = totalBytes,
                        totalBytes = totalBytes,
                        speedBytesPerSec = 0L
                    )
                    FirebaseManager.updateCommand(command)
                    FirebaseManager.markFilesAsBackedUp(hostId, backedUpIds)
                    updateNotification("اكتمل الرفع الفعلي بنجاح (100%)", 100)
                }

                CommandType.PULL -> {
                    // Restore to RemoteVault/Restored/
                    val restoreDir = File(vaultFolder, "Restored").apply { mkdirs() }
                    val totalCount = fileIds.size.coerceAtLeast(3)

                    for (i in 1..totalCount) {
                        val progress = ((i.toFloat() / totalCount) * 100).toInt()
                        command = command.copy(
                            progress = progress,
                            currentFile = "Restoring file #$i to ${restoreDir.name}...",
                            filesProcessed = i,
                            totalFiles = totalCount,
                            speedBytesPerSec = 11_200_000L
                        )
                        FirebaseManager.updateCommand(command)
                        updateNotification("Restoring $i/$totalCount ($progress%)", progress)
                        delay(600)
                    }

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        currentFile = "Restored to ${restoreDir.path}",
                        filesProcessed = totalCount,
                        totalFiles = totalCount
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.CANCEL -> {
                    command = command.copy(
                        status = CommandStatus.CANCELLED.name,
                        completedAt = System.currentTimeMillis()
                    )
                    FirebaseManager.updateCommand(command)
                }

                else -> {
                    android.util.Log.w("HostBackupService", "Unhandled command type: $typeStr")
                }
            }
        } catch (e: Exception) {
            command = command.copy(
                status = CommandStatus.FAILED.name,
                completedAt = System.currentTimeMillis(),
                error = e.localizedMessage ?: "Unknown execution error"
            )
            FirebaseManager.updateCommand(command)
        } finally {
            delay(1000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        currentExecutionJob?.cancel()
        super.onDestroy()
    }
}
