package com.example.repository

import android.content.Context
import android.os.Build
import com.example.firebase.FirebaseManager
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.DeviceRole
import com.example.models.HostDevice
import com.example.models.PairingCodeData
import com.example.models.SharedFolder
import com.example.models.UserSession
import com.example.models.VaultFile
import com.example.models.VaultSummary
import com.example.services.HostBackupForegroundService
import com.example.utils.StorageUtils
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.random.Random

class BackupRepository(private val context: Context) {

    private val repoScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val PREFS_NAME = "remote_backup_prefs"
        private const val KEY_DEVICE_ID = "local_device_id"
        private const val KEY_SAVED_ROLE = "saved_device_role"
        private const val KEY_SAVED_HOST_USER_ID = "saved_host_user_id"
        private const val KEY_SAVED_HOST_EMAIL = "saved_host_email"
        private const val KEY_SAVED_HOST_DISPLAY_NAME = "saved_host_display_name"
        private const val KEY_SAVED_VAULT_PATH = "saved_vault_path"
        private const val KEY_SAVED_SHARED_FOLDERS = "saved_shared_folders"
        private const val KEY_ADMIN_LAST_EMAIL = "admin_last_email"

        @Volatile
        private var INSTANCE: BackupRepository? = null

        fun getInstance(context: Context): BackupRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Current device identification
    val localDeviceId: String = run {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
            id = "android_$randomSuffix"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        id
    }

    val localDeviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Current Session
    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    // Host specific state
    private val _currentHostDevice = MutableStateFlow<HostDevice?>(null)
    val currentHostDevice: StateFlow<HostDevice?> = _currentHostDevice.asStateFlow()

    private val _sharedFolders = MutableStateFlow<List<SharedFolder>>(emptyList())
    val sharedFolders: StateFlow<List<SharedFolder>> = _sharedFolders.asStateFlow()

    private val _activePairingCode = MutableStateFlow<PairingCodeData?>(null)
    val activePairingCode: StateFlow<PairingCodeData?> = _activePairingCode.asStateFlow()

    private val _hostFiles = MutableStateFlow<List<VaultFile>>(emptyList())
    val hostFiles: StateFlow<List<VaultFile>> = _hostFiles.asStateFlow()

    private val _vaultSummary = MutableStateFlow<VaultSummary?>(null)
    val vaultSummary: StateFlow<VaultSummary?> = _vaultSummary.asStateFlow()

    private val _selectedVaultPath = MutableStateFlow<String>("")
    val selectedVaultPath: StateFlow<String> = _selectedVaultPath.asStateFlow()

    private var heartbeatJob: Job? = null

    // Admin specific state: combined list of paired hosts
    // 1. Automatically matches any Host registered with the SAME ACCOUNT (userId / email)
    // 2. Also includes any Host linked via 6-digit Pairing Code
    val pairedHosts: StateFlow<List<HostDevice>> = combine(
        FirebaseManager.syncedDevices,
        FirebaseManager.adminPairedHosts,
        _currentUser
    ) { devices, adminMap, user ->
        val adminId = user?.userId ?: ""
        val userEmail = user?.email?.lowercase()?.trim() ?: ""
        val hostIds = adminMap[adminId] ?: emptySet()

        devices.values.filter { dev ->
            val devEmail = dev.email.lowercase().trim()
            dev.role == DeviceRole.HOST.name && (
                (userEmail.isNotBlank() && devEmail.isNotBlank() && devEmail == userEmail) ||
                (user != null && user.userId.isNotBlank() && dev.userId == user.userId) ||
                (dev.deviceId in hostIds) ||
                (adminId.isNotBlank() && dev.userId == adminId)
            )
        }
    }.stateIn(repoScope, SharingStarted.Lazily, emptyList())

    init {
        FirebaseManager.init(context)
        _sharedFolders.value = loadSavedSharedFolders()
        val defaultDir = StorageUtils.getDefaultVaultFolder(context)
        val savedVault = prefs.getString(KEY_SAVED_VAULT_PATH, null)
        _selectedVaultPath.value = if (!savedVault.isNullOrBlank()) savedVault else defaultDir.absolutePath
    }

    fun loadSavedSharedFolders(): List<SharedFolder> {
        val jsonStr = prefs.getString(KEY_SAVED_SHARED_FOLDERS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<SharedFolder>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SharedFolder(
                            folderId = obj.optString("folderId", "f_$i"),
                            name = obj.optString("name", "Shared Folder"),
                            pathOrUri = obj.optString("pathOrUri", ""),
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                            fileCount = obj.optInt("fileCount", 0),
                            totalSizeBytes = obj.optLong("totalSizeBytes", 0L),
                            lastScan = obj.optLong("lastScan", 0L),
                            lastModified = obj.optLong("lastModified", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultDir = StorageUtils.getDefaultVaultFolder(context)
        val initialList = listOf(
            SharedFolder(
                folderId = "folder_default",
                name = "المجلد الافتراضي (RemoteVault)",
                pathOrUri = defaultDir.absolutePath,
                addedAt = System.currentTimeMillis()
            )
        )
        saveSharedFoldersInternal(initialList)
        return initialList
    }

    private fun saveSharedFoldersInternal(folders: List<SharedFolder>) {
        _sharedFolders.value = folders
        try {
            val arr = JSONArray()
            for (f in folders) {
                val obj = JSONObject().apply {
                    put("folderId", f.folderId)
                    put("name", f.name)
                    put("pathOrUri", f.pathOrUri)
                    put("addedAt", f.addedAt)
                    put("fileCount", f.fileCount)
                    put("totalSizeBytes", f.totalSizeBytes)
                    put("lastScan", f.lastScan)
                    put("lastModified", f.lastModified)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_SHARED_FOLDERS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addSharedFolder(pathOrUri: String, customName: String? = null): SharedFolder {
        val displayName = if (!customName.isNullOrBlank()) customName else StorageUtils.getFolderDisplayName(context, pathOrUri)
        val folderId = "fld_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val newFolder = SharedFolder(
            folderId = folderId,
            name = displayName,
            pathOrUri = pathOrUri,
            addedAt = System.currentTimeMillis()
        )
        val currentList = _sharedFolders.value.toMutableList()
        // If current list only contains the placeholder default vault, replace it with the first user-authorized SAF directory
        if (currentList.size == 1 && currentList[0].folderId == "folder_default") {
            currentList.clear()
        }
        currentList.add(newFolder)
        saveSharedFoldersInternal(currentList)
        scanLocalVault()
        return newFolder
    }

    fun removeSharedFolder(folderId: String) {
        val currentList = _sharedFolders.value.toMutableList()
        currentList.removeAll { it.folderId == folderId }
        saveSharedFoldersInternal(currentList)
        scanLocalVault()
    }

    fun getSavedRole(): DeviceRole {
        val roleStr = prefs.getString(KEY_SAVED_ROLE, null) ?: return DeviceRole.UNSET
        return try {
            DeviceRole.valueOf(roleStr)
        } catch (e: Exception) {
            DeviceRole.UNSET
        }
    }

    fun hasSavedHostSession(): Boolean {
        val role = getSavedRole()
        val userId = prefs.getString(KEY_SAVED_HOST_USER_ID, null)
        return role == DeviceRole.HOST && !userId.isNullOrBlank()
    }

    fun getAdminRememberedEmail(): String {
        return prefs.getString(KEY_ADMIN_LAST_EMAIL, "") ?: ""
    }

    fun autoStartHostSession() {
        val hostUserId = prefs.getString(KEY_SAVED_HOST_USER_ID, null) ?: return
        val hostEmail = prefs.getString(KEY_SAVED_HOST_EMAIL, "host@remotebackup.internal") ?: "host@remotebackup.internal"
        val displayName = prefs.getString(KEY_SAVED_HOST_DISPLAY_NAME, "Host Phone") ?: "Host Phone"
        val savedVault = prefs.getString(KEY_SAVED_VAULT_PATH, null)
        if (!savedVault.isNullOrBlank()) {
            _selectedVaultPath.value = savedVault
        }

        val session = UserSession(
            userId = hostUserId,
            email = hostEmail,
            displayName = displayName,
            role = DeviceRole.HOST,
            selectedRole = DeviceRole.HOST
        )
        _currentUser.value = session

        val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
        val host = HostDevice(
            deviceId = localDeviceId,
            userId = hostUserId,
            email = hostEmail,
            name = localDeviceName,
            role = DeviceRole.HOST.name,
            status = "ONLINE",
            lastSeen = System.currentTimeMillis(),
            vaultId = "vault_default",
            vaultPath = _selectedVaultPath.value,
            batteryPercent = StorageUtils.getBatteryPercent(context),
            storageFreeBytes = freeBytes,
            storageTotalBytes = totalBytes,
            sharedFolders = _sharedFolders.value
        )
        _currentHostDevice.value = host
        FirebaseManager.registerOrUpdateDevice(host)

        scanLocalVault()
        startHostKeepAliveHeartbeat()
        startHostCommandListener()
    }

    private var commandListenerJob: Job? = null

    fun startHostCommandListener() {
        commandListenerJob?.cancel()
        commandListenerJob = repoScope.launch {
            FirebaseManager.syncedCommands.collect { map ->
                val host = _currentHostDevice.value ?: return@collect
                val myCommands = map[host.deviceId] ?: emptyList()
                val pendingCmd = myCommands.firstOrNull { it.status == CommandStatus.PENDING.name }
                if (pendingCmd != null) {
                    android.util.Log.d("BackupRepository", "Host detected pending command: ${pendingCmd.commandId} (${pendingCmd.type})")
                    HostBackupForegroundService.startCommand(
                        context = context,
                        command = pendingCmd,
                        vaultPath = _selectedVaultPath.value
                    )
                }
            }
        }
    }

    fun startHostKeepAliveHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = repoScope.launch {
            while (isActive) {
                val host = _currentHostDevice.value
                if (host != null) {
                    val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
                    val battery = StorageUtils.getBatteryPercent(context)
                    val updated = host.copy(
                        status = "ONLINE",
                        lastSeen = System.currentTimeMillis(),
                        batteryPercent = battery,
                        storageFreeBytes = freeBytes,
                        storageTotalBytes = totalBytes
                    )
                    _currentHostDevice.value = updated
                    FirebaseManager.updateDeviceStatus(host.deviceId, "ONLINE", battery, freeBytes)
                }
                delay(20_000) // Update every 20 seconds to guarantee live status
            }
        }
    }

    suspend fun login(email: String, pass: String): Result<UserSession> {
        val result = FirebaseManager.login(email, pass)
        result.onSuccess { session ->
            _currentUser.value = session
            prefs.edit().putString(KEY_ADMIN_LAST_EMAIL, email).apply()
        }
        return result
    }

    suspend fun register(email: String, pass: String): Result<UserSession> {
        val result = FirebaseManager.register(email, pass)
        result.onSuccess { session ->
            _currentUser.value = session
            prefs.edit().putString(KEY_ADMIN_LAST_EMAIL, email).apply()
        }
        return result
    }

    fun logout() {
        FirebaseManager.logout()
        heartbeatJob?.cancel()
        _currentUser.value = null
        _currentHostDevice.value = null
        _activePairingCode.value = null

        // Clear saved host session if explicitly logged out
        prefs.edit()
            .remove(KEY_SAVED_ROLE)
            .remove(KEY_SAVED_HOST_USER_ID)
            .remove(KEY_SAVED_HOST_EMAIL)
            .apply()
    }

    suspend fun setDeviceRole(role: DeviceRole) {
        val user = _currentUser.value ?: return
        _currentUser.value = user.copy(selectedRole = role, role = role)

        FirebaseManager.saveRolePreference(
            userId = user.userId,
            deviceId = localDeviceId,
            role = role
        )

        if (role == DeviceRole.HOST) {
            // Save persistent host state: NEVER ask for login or tampering again!
            prefs.edit()
                .putString(KEY_SAVED_ROLE, DeviceRole.HOST.name)
                .putString(KEY_SAVED_HOST_USER_ID, user.userId)
                .putString(KEY_SAVED_HOST_EMAIL, user.email)
                .putString(KEY_SAVED_HOST_DISPLAY_NAME, user.displayName)
                .putString(KEY_SAVED_VAULT_PATH, _selectedVaultPath.value)
                .apply()

            val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
            val host = HostDevice(
                deviceId = localDeviceId,
                userId = user.userId,
                email = user.email,
                name = localDeviceName,
                role = DeviceRole.HOST.name,
                status = "ONLINE",
                lastSeen = System.currentTimeMillis(),
                vaultId = "vault_default",
                vaultPath = _selectedVaultPath.value,
                batteryPercent = StorageUtils.getBatteryPercent(context),
                storageFreeBytes = freeBytes,
                storageTotalBytes = totalBytes,
                sharedFolders = _sharedFolders.value
            )
            _currentHostDevice.value = host
            FirebaseManager.registerOrUpdateDevice(host)

            scanLocalVault()
            startHostKeepAliveHeartbeat()
        } else {
            // Admin role: requires login each time for security
            prefs.edit()
                .putString(KEY_SAVED_ROLE, DeviceRole.ADMIN.name)
                .putString(KEY_ADMIN_LAST_EMAIL, user.email)
                .remove(KEY_SAVED_HOST_USER_ID)
                .apply()

            heartbeatJob?.cancel()

            val adminDevice = HostDevice(
                deviceId = localDeviceId,
                userId = user.userId,
                name = localDeviceName,
                role = DeviceRole.ADMIN.name,
                status = "ONLINE",
                lastSeen = System.currentTimeMillis()
            )
            FirebaseManager.registerOrUpdateDevice(adminDevice)
        }
    }

    fun setVaultPath(path: String) {
        _selectedVaultPath.value = path
        prefs.edit().putString(KEY_SAVED_VAULT_PATH, path).apply()
        val host = _currentHostDevice.value
        if (host != null) {
            val updated = host.copy(vaultPath = path)
            _currentHostDevice.value = updated
            FirebaseManager.registerOrUpdateDevice(updated)
        }
    }

    fun scanLocalVault() {
        val host = _currentHostDevice.value ?: HostDevice(
            deviceId = localDeviceId,
            userId = _currentUser.value?.userId ?: "user_default",
            name = localDeviceName,
            role = DeviceRole.HOST.name,
            status = "ONLINE"
        ).also { _currentHostDevice.value = it }

        val folders = if (_sharedFolders.value.isNotEmpty()) {
            _sharedFolders.value
        } else {
            val defaultList = mutableListOf<SharedFolder>()
            val def = StorageUtils.getDefaultVaultFolder(context)
            defaultList.add(
                SharedFolder(
                    folderId = "folder_default",
                    name = "المجلد الافتراضي (RemoteVault)",
                    pathOrUri = def.absolutePath,
                    addedAt = System.currentTimeMillis()
                )
            )
            if (_selectedVaultPath.value.isNotBlank()) {
                defaultList.add(
                    SharedFolder(
                        folderId = "folder_custom",
                        name = StorageUtils.getFolderDisplayName(context, _selectedVaultPath.value),
                        pathOrUri = _selectedVaultPath.value,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            defaultList
        }

        val allFiles = mutableListOf<VaultFile>()
        val updatedFolders = mutableListOf<SharedFolder>()
        var totalFolderCount = 0

        for (folder in folders) {
            val (files, summary) = StorageUtils.scanVault(context, folder.pathOrUri, host.deviceId, folder.folderId)
            allFiles.addAll(files)
            totalFolderCount += summary.foldersFound

            val latestMod = files.maxOfOrNull { it.lastModified } ?: summary.lastScanTime
            updatedFolders.add(
                folder.copy(
                    fileCount = summary.filesFound,
                    totalSizeBytes = summary.totalSizeBytes,
                    lastScan = summary.lastScanTime,
                    lastModified = latestMod
                )
            )
        }

        saveSharedFoldersInternal(updatedFolders)

        val totalSizeBytes = allFiles.sumOf { it.size }
        val overallSummary = VaultSummary(
            filesFound = allFiles.size,
            foldersFound = totalFolderCount,
            totalSizeBytes = totalSizeBytes,
            lastScanTime = System.currentTimeMillis(),
            vaultPath = folders.firstOrNull()?.pathOrUri ?: ""
        )

        _hostFiles.value = allFiles
        _vaultSummary.value = overallSummary

        val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
        val updatedHost = host.copy(
            fileCount = allFiles.size,
            folderCount = totalFolderCount,
            vaultSizeBytes = totalSizeBytes,
            lastScan = overallSummary.lastScanTime,
            storageFreeBytes = freeBytes,
            storageTotalBytes = totalBytes,
            batteryPercent = StorageUtils.getBatteryPercent(context),
            lastSeen = System.currentTimeMillis(),
            vaultPath = overallSummary.vaultPath,
            sharedFolders = updatedFolders
        )
        _currentHostDevice.value = updatedHost
        FirebaseManager.uploadVaultMetadata(host.deviceId, allFiles, overallSummary)
        FirebaseManager.registerOrUpdateDevice(updatedHost)
    }

    fun generatePairingCode(): PairingCodeData {
        val host = _currentHostDevice.value
            ?: HostDevice(
                deviceId = localDeviceId,
                userId = _currentUser.value?.userId ?: "user_default",
                name = localDeviceName
            )
        val codeNum = Random.nextInt(100_000, 999_999).toString()
        val pairing = PairingCodeData(
            code = codeNum,
            hostId = host.deviceId,
            hostName = host.name,
            createdBy = host.userId,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (10 * 60 * 1000)
        )
        FirebaseManager.createPairingCode(pairing)
        _activePairingCode.value = pairing
        return pairing
    }

    suspend fun claimPairingCode(code: String): Result<HostDevice> {
        val adminId = _currentUser.value?.userId ?: "admin_user"
        return FirebaseManager.claimPairingCode(code.trim(), adminId)
    }

    fun sendCommand(
        hostId: String,
        type: CommandType,
        fileIds: List<String> = emptyList()
    ): String {
        val adminId = _currentUser.value?.userId ?: "admin_user"
        val cmdId = "cmd_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        val command = BackupCommand(
            commandId = cmdId,
            type = type.name,
            status = CommandStatus.PENDING.name,
            hostId = hostId,
            adminId = adminId,
            fileIds = fileIds,
            createdAt = System.currentTimeMillis()
        )

        FirebaseManager.createCommand(command)

        if (hostId == localDeviceId || _currentHostDevice.value?.deviceId == hostId) {
            HostBackupForegroundService.startCommand(
                context = context,
                command = command,
                vaultPath = _selectedVaultPath.value
            )
        }

        return cmdId
    }

    fun cancelCommand(hostId: String, commandId: String) {
        val cmd = BackupCommand(
            commandId = commandId,
            hostId = hostId,
            type = CommandType.CANCEL.name,
            status = CommandStatus.CANCELLED.name,
            completedAt = System.currentTimeMillis()
        )
        FirebaseManager.updateCommand(cmd)
    }

    fun getCommandsForHost(hostId: String): StateFlow<List<BackupCommand>> {
        return FirebaseManager.syncedCommands.combine(
            MutableStateFlow(hostId)
        ) { map, id ->
            map[id] ?: emptyList()
        }.stateIn(repoScope, SharingStarted.Lazily, emptyList())
    }

    fun getFilesForHost(hostId: String): StateFlow<List<VaultFile>> {
        return FirebaseManager.syncedFiles.combine(
            MutableStateFlow(hostId)
        ) { map, id ->
            map[id] ?: emptyList()
        }.stateIn(repoScope, SharingStarted.Lazily, emptyList())
    }

    fun updateHostFileStatus(
        backedUpIds: List<String>,
        remoteStorageMap: Map<String, String> = emptyMap(),
        downloadUrlMap: Map<String, String> = emptyMap()
    ) {
        val current = _hostFiles.value
        val idSet = backedUpIds.toSet()
        val updated = current.map { f ->
            if (f.fileId in idSet) {
                f.copy(
                    isBackedUp = true,
                    remoteStoragePath = remoteStorageMap[f.fileId] ?: f.remoteStoragePath,
                    downloadUrl = downloadUrlMap[f.fileId] ?: f.downloadUrl
                )
            } else {
                f
            }
        }
        _hostFiles.value = updated
    }

    fun uploadFiles(fileIds: List<String>) {
        val host = _currentHostDevice.value ?: return
        sendCommand(host.deviceId, CommandType.BACKUP, fileIds)
    }
}
