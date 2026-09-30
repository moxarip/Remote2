package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.repository.BackupRepository
import com.example.utils.StorageUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "semailbibi42@gmail.com",
    val password: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BackupRepository.getInstance(application)

    // Auth
    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    val currentUser: StateFlow<UserSession?> = repository.currentUser

    // Host screen state
    val currentHostDevice: StateFlow<HostDevice?> = repository.currentHostDevice
    val hostFiles: StateFlow<List<VaultFile>> = repository.hostFiles
    val vaultSummary: StateFlow<VaultSummary?> = repository.vaultSummary
    val activePairingCode: StateFlow<PairingCodeData?> = repository.activePairingCode
    val selectedVaultPath: StateFlow<String> = repository.selectedVaultPath
    val hostSharedFolders: StateFlow<List<SharedFolder>> = repository.sharedFolders
    val indexingProgress: StateFlow<com.example.models.IndexingProgress> = repository.indexingProgress

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSavingRole = MutableStateFlow(false)
    val isSavingRole: StateFlow<Boolean> = _isSavingRole.asStateFlow()

    // Admin screen state
    val pairedHosts: StateFlow<List<HostDevice>> = repository.pairedHosts

    private val _adminDownloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val adminDownloadedFiles: StateFlow<List<File>> = _adminDownloadedFiles.asStateFlow()

    private val _selectedHost = MutableStateFlow<HostDevice?>(null)
    val selectedHost: StateFlow<HostDevice?> = _selectedHost.asStateFlow()

    private val _adminSelectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val adminSelectedFileIds: StateFlow<Set<String>> = _adminSelectedFileIds.asStateFlow()

    private val _fileFilterCategory = MutableStateFlow("All")
    val fileFilterCategory: StateFlow<String> = _fileFilterCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pairingInputCode = MutableStateFlow("")
    val pairingInputCode: StateFlow<String> = _pairingInputCode.asStateFlow()

    private val _pairingDialogVisible = MutableStateFlow(false)
    val pairingDialogVisible: StateFlow<Boolean> = _pairingDialogVisible.asStateFlow()

    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Host commands live tracking
    val hostCommands: StateFlow<List<BackupCommand>> = combine(
        currentHostDevice,
        FirebaseManager.syncedCommands
    ) { host, cmdMap ->
        val id = host?.deviceId ?: repository.localDeviceId
        cmdMap[id] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin selected host files
    val selectedHostFiles: StateFlow<List<VaultFile>> = combine(
        _selectedHost,
        FirebaseManager.syncedFiles
    ) { host, filesMap ->
        if (host == null) emptyList() else filesMap[host.deviceId] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin active command for currently selected host
    val selectedHostActiveCommand: StateFlow<BackupCommand?> = combine(
        _selectedHost,
        FirebaseManager.syncedCommands
    ) { host, cmdMap ->
        if (host == null) null
        else cmdMap[host.deviceId]?.firstOrNull {
            it.status == CommandStatus.RUNNING.name || it.status == CommandStatus.PENDING.name
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        val rememberedEmail = repository.getAdminRememberedEmail()
        if (rememberedEmail.isNotBlank()) {
            _authUiState.value = _authUiState.value.copy(email = rememberedEmail)
        }
    }

    fun restoreHostSession() {
        repository.autoStartHostSession()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            com.example.firebase.FirebaseManager.refreshDevicesFromCloud()
        }
    }

    fun onEmailChanged(email: String) {
        _authUiState.value = _authUiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(pass: String) {
        _authUiState.value = _authUiState.value.copy(password = pass, errorMessage = null)
    }

    fun toggleAuthMode() {
        val current = _authUiState.value
        _authUiState.value = current.copy(isRegisterMode = !current.isRegisterMode, errorMessage = null)
    }

    fun authenticate() {
        val state = _authUiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _authUiState.value = state.copy(errorMessage = "Email is required & password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _authUiState.value = state.copy(isLoading = true, errorMessage = null)
            val result = if (state.isRegisterMode) {
                repository.register(state.email.trim(), state.password.trim())
            } else {
                repository.login(state.email.trim(), state.password.trim())
            }

            result.fold(
                onSuccess = {
                    _authUiState.value = _authUiState.value.copy(isLoading = false, errorMessage = null)
                },
                onFailure = { error ->
                    _authUiState.value = _authUiState.value.copy(isLoading = false, errorMessage = error.message ?: "Authentication failed")
                }
            )
        }
    }

    fun setRole(role: DeviceRole, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSavingRole.value = true
            repository.setDeviceRole(role)
            _isSavingRole.value = false
            _snackbarMessage.value = "Role ${role.name} saved to Firebase RTDB"
            onComplete()
        }
    }

    fun logout() {
        repository.logout()
        _selectedHost.value = null
        _adminSelectedFileIds.value = emptySet()
    }

    fun scanHostVault() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.scanLocalVault()
            _isScanning.value = false
            _snackbarMessage.value = "Vault scan completed successfully"
        }
    }

    fun setVaultPath(path: String) {
        repository.setVaultPath(path)
        scanHostVault()
    }

    fun addSharedFolder(pathOrUri: String) {
        viewModelScope.launch {
            _isScanning.value = true
            val added = repository.addSharedFolder(pathOrUri)
            _isScanning.value = false
            _snackbarMessage.value = "تمت إضافة المجلد: ${added.name}"
        }
    }

    fun removeSharedFolder(folderId: String) {
        viewModelScope.launch {
            _isScanning.value = true
            repository.removeSharedFolder(folderId)
            _isScanning.value = false
            _snackbarMessage.value = "تمت إزالة المجلد من قائمة المشاركة"
        }
    }

    fun generatePairingCode() {
        repository.generatePairingCode()
    }

    fun selectHost(host: HostDevice) {
        _selectedHost.value = host
        _adminSelectedFileIds.value = emptySet()
        refreshSelectedHostFiles()
    }

    fun refreshSelectedHostFiles() {
        val host = _selectedHost.value ?: return
        viewModelScope.launch {
            _isScanning.value = true
            FirebaseManager.fetchHostFiles(host.deviceId)
            _isScanning.value = false
        }
    }

    fun uploadFiles(files: List<VaultFile>) {
        if (files.isEmpty()) return
        val host = currentHostDevice.value ?: return
        val fileIds = files.map { it.fileId }
        repository.uploadFiles(fileIds)
        _snackbarMessage.value = "بدء رفع ${files.size} ملفات إلى السحابة..."
    }

    fun uploadFile(file: VaultFile) {
        uploadFiles(listOf(file))
    }

    fun clearSelectedHost() {
        _selectedHost.value = null
        _adminSelectedFileIds.value = emptySet()
    }

    fun onPairingInputCodeChanged(code: String) {
        if (code.length <= 6 && code.all { it.isDigit() }) {
            _pairingInputCode.value = code
            _pairingError.value = null
        }
    }

    fun openAddHostDialog() {
        _pairingInputCode.value = ""
        _pairingError.value = null
        _pairingDialogVisible.value = true
    }

    fun closeAddHostDialog() {
        _pairingDialogVisible.value = false
        _pairingError.value = null
    }

    fun submitPairingCode() {
        val code = _pairingInputCode.value.trim()
        if (code.length != 6) {
            _pairingError.value = "Please enter a valid 6-digit code"
            return
        }

        viewModelScope.launch {
            val result = repository.claimPairingCode(code)
            result.fold(
                onSuccess = { host ->
                    _pairingDialogVisible.value = false
                    _selectedHost.value = host
                    _snackbarMessage.value = "Paired successfully with ${host.name}"
                },
                onFailure = { error ->
                    _pairingError.value = error.message ?: "Failed to pair host"
                }
            )
        }
    }

    fun toggleFileSelection(fileId: String) {
        val current = _adminSelectedFileIds.value.toMutableSet()
        if (current.contains(fileId)) {
            current.remove(fileId)
        } else {
            current.add(fileId)
        }
        _adminSelectedFileIds.value = current
    }

    fun selectAllFiles(files: List<VaultFile>) {
        _adminSelectedFileIds.value = files.map { it.fileId }.toSet()
    }

    fun clearFileSelection() {
        _adminSelectedFileIds.value = emptySet()
    }

    fun setFilterCategory(category: String) {
        _fileFilterCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendAdminCommand(type: CommandType) {
        val host = _selectedHost.value ?: return
        val fileIds = _adminSelectedFileIds.value.toList()
        repository.sendCommand(host.deviceId, type, fileIds)
        _snackbarMessage.value = "Command ${type.name} sent to ${host.name}"
    }

    fun cancelAdminCommand(commandId: String) {
        val host = _selectedHost.value ?: return
        repository.cancelCommand(host.deviceId, commandId)
        _snackbarMessage.value = "Command cancelled"
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun refreshDownloadedFiles() {
        _adminDownloadedFiles.value = StorageUtils.getAdminDownloadedFiles(getApplication())
    }

    fun downloadFileToAdmin(file: VaultFile) {
        val host = _selectedHost.value
        val hostId = host?.deviceId ?: file.hostDeviceId
        val hostPath = host?.vaultPath ?: ""
        viewModelScope.launch {
            if (!file.isBackedUp && file.downloadUrl.isBlank()) {
                _snackbarMessage.value = "طلب الملف ${file.name} من عقدة التخزين..."
                repository.sendCommand(hostId, CommandType.BACKUP, listOf(file.fileId))
                return@launch
            }
            _snackbarMessage.value = "جاري تنزيل ${file.name} من Firebase..."
            val result = FirebaseManager.downloadFileFromCloud(getApplication(), hostId, file)
            result.fold(
                onSuccess = { downloadedFile ->
                    refreshDownloadedFiles()
                    _snackbarMessage.value = "تم تنزيل ${downloadedFile.name} بنجاح من السحابة (${StorageUtils.formatFileSize(downloadedFile.length())})"
                },
                onFailure = { err ->
                    // Check if file is physically available on local disk before falling back
                    if (hostPath.isNotBlank()) {
                        val localCandidate = File(hostPath, file.relativePath)
                        if (localCandidate.exists() && localCandidate.canRead()) {
                            StorageUtils.downloadFileToAdmin(getApplication(), file, hostPath)
                            refreshDownloadedFiles()
                            _snackbarMessage.value = "تم نسخ ${file.name} من القرص المحلي بنجاح"
                            return@launch
                        }
                    }
                    _snackbarMessage.value = "فشل تنزيل ${file.name} من السحابة: ${err.message}"
                }
            )
        }
    }

    fun requestFileUpload(file: VaultFile) {
        val host = _selectedHost.value
        val hostId = host?.deviceId ?: file.hostDeviceId
        viewModelScope.launch {
            _snackbarMessage.value = "جاري إرسال أمر رفع ${file.name} إلى عقدة التخزين..."
            repository.sendCommand(hostId, CommandType.BACKUP, listOf(file.fileId))
        }
    }

    fun requestFilePreview(file: VaultFile) {
        val host = _selectedHost.value
        val hostId = host?.deviceId ?: file.hostDeviceId
        viewModelScope.launch {
            if (file.isBackedUp || file.downloadUrl.isNotBlank()) {
                _snackbarMessage.value = "الملف متاح للمعاينة المباشرة"
            } else {
                _snackbarMessage.value = "طلب تجهيز معاينة ${file.name} من عقدة التخزين..."
                repository.sendCommand(hostId, CommandType.PREVIEW_REQUEST, listOf(file.fileId))
            }
        }
    }

    fun downloadAllUploadedFilesToAdmin(files: List<VaultFile>) {
        val host = _selectedHost.value
        val hostId = host?.deviceId ?: ""
        val hostPath = host?.vaultPath ?: ""
        viewModelScope.launch {
            _snackbarMessage.value = "جاري تنزيل ${files.size} ملفات من السحابة..."
            var count = 0
            for (f in files) {
                val result = FirebaseManager.downloadFileFromCloud(getApplication(), hostId, f)
                if (result.isFailure) {
                    StorageUtils.downloadFileToAdmin(getApplication(), f, hostPath)
                }
                count++
            }
            refreshDownloadedFiles()
            _snackbarMessage.value = "اكتمل تنزيل $count ملفات بنجاح إلى مجلد التنزيلات"
        }
    }

    fun openDownloadedFile(file: File) {
        val success = StorageUtils.openDownloadedFile(getApplication(), file)
        if (!success) {
            _snackbarMessage.value = "تم حفظ الملف في التنزيلات: ${file.name}"
        }
    }

    fun shareDownloadedFile(file: File) {
        StorageUtils.shareDownloadedFile(getApplication(), file)
    }

    fun deleteDownloadedFile(file: File) {
        try {
            if (file.exists()) file.delete()
            refreshDownloadedFiles()
            _snackbarMessage.value = "تم حذف ${file.name} من هاتف الآدمن"
        } catch (e: Exception) {
            _snackbarMessage.value = "فشل الحذف: ${e.message}"
        }
    }
}
