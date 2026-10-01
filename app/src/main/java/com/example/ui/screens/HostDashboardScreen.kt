package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BackupCommand
import com.example.models.HostDevice
import com.example.models.PairingCodeData
import com.example.models.SharedFolder
import com.example.models.VaultFile
import com.example.models.VaultSummary
import com.example.ui.components.BatteryBadge
import com.example.ui.components.CommandProgressCard
import com.example.ui.components.FileCategoryIcon
import com.example.ui.components.PairingCodeCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StorageProgressBar
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SlateDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.StorageUtils

@Composable
fun HostDashboardScreen(
    hostDevice: HostDevice?,
    vaultSummary: VaultSummary?,
    selectedVaultPath: String,
    activePairingCode: PairingCodeData?,
    commands: List<BackupCommand>,
    sharedFolders: List<SharedFolder> = emptyList(),
    hostFiles: List<VaultFile> = emptyList(),
    isScanning: Boolean,
    indexingProgress: com.example.models.IndexingProgress = com.example.models.IndexingProgress(),
    onScanVault: () -> Unit,
    onSelectVaultPath: (String) -> Unit,
    onAddSharedFolder: (String) -> Unit = {},
    onRemoveSharedFolder: (String) -> Unit = {},
    onGeneratePairingCode: () -> Unit,
    onCancelCommand: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    onUploadFiles: (List<VaultFile>) -> Unit = {}
) {
    val context = LocalContext.current
    var showPairingDialog by remember { mutableStateOf(false) }
    var folderToBrowse by remember { mutableStateOf<SharedFolder?>(null) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onAddSharedFolder(uri.toString())
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onAddSharedFolder(uri.toString())
        }
    }

    Scaffold(
        containerColor = SlateDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "REMOTE HOST",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = hostDevice?.status ?: "ONLINE")
                    }
                    Text(
                        text = hostDevice?.name ?: "Android Host Device",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryBadge(percent = hostDevice?.batteryPercent ?: 90)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.testTag("host_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Persistent '+' Button: Always stays visible so the user can keep adding folders!
            ExtendedFloatingActionButton(
                onClick = { dirPickerLauncher.launch(null) },
                containerColor = ElectricBlue,
                contentColor = SlateDark,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Folder") },
                text = { Text("إضافة مجلد جديد +", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("host_add_folder_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // LIVE INDEXING PROGRESS BANNER (NON-BLOCKING)
            if (indexingProgress.isIndexing) {
                item {
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("storage_node_indexing_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = ElectricBlue,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Indexing Storage Node...",
                                    color = ElectricBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (indexingProgress.message.isNotBlank()) indexingProgress.message else "Found ${indexingProgress.indexedCount} files...",
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // UNATTENDED HOME HOST BANNER
            item {
                Surface(
                    color = EmeraldOnline.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldOnline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "UNATTENDED HOME HOST ACTIVE",
                                color = EmeraldOnline,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "هذا الهاتف يعمل في وضع الهوست المنزلي. يمكنك إعطاء صلاحيات لأكثر من مجلد عبر علامة (+) وستبقى ظاهرة دائماً لإضافة المزيد.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // STORAGE NODE SETUP CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PERSONAL STORAGE NODE",
                                color = ElectricBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تحويل الهاتف القديم إلى عقدة تخزين شخصية. امنح الوصول للمجلدات المطلوبة عبر SAF مرة واحدة (مثل DCIM، Download، WhatsApp، أو Android/media) لتمكين هاتف الآدمن من تصفحها وطلب الملفات عند الحاجة دون رفع تلقائي.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { dirPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("enable_storage_node_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = SlateDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (sharedFolders.isEmpty()) "Enable Storage Node" else "إضافة مجلد (+)",
                                    color = SlateDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("host_add_single_file_button")
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ملف منفرد",
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // MULTI-FOLDER SECTION (User Requirement: add folder 1, '+' stays visible, add folder 2, '+' stays visible...)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "المجلدات المصرح بها (${sharedFolders.size})",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المجلدات التي يمكن لهاتف الآدمن الوصول لها",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            // Persistent '+' button in section header
                            Button(
                                onClick = { dirPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("host_add_folder_header_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Folder", tint = SlateDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة مجلد +", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (sharedFolders.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "لم يتم إضافة أي مجلدات بعد إلى عقدة التخزين.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { dirPickerLauncher.launch(null) },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enable Storage Node (اختر مجلد)", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sharedFolders.forEachIndexed { index, folder ->
                                    SharedFolderHostItem(
                                        index = index + 1,
                                        folder = folder,
                                        onBrowse = { folderToBrowse = folder },
                                        onRemove = { onRemoveSharedFolder(folder.folderId) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Persistent '+' button inside list so user can keep adding folder 2, folder 3...
                                Surface(
                                    color = SlateCardElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { dirPickerLauncher.launch(null) }
                                        .testTag("host_add_another_folder_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Another Folder", tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "إضافة مجلد آخر (+)",
                                            color = ElectricBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // SCAN VAULT & PAIRING ACTIONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onScanVault,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("scan_vault_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = SlateDark,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = SlateDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "فحص وتحديث المجلدات",
                                color = SlateDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onGeneratePairingCode()
                            showPairingDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                            .testTag("pair_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Pairing Code",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "رمز الاقتران",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // STORAGE STATS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("إجمالي الملفات", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = "${vaultSummary?.filesFound ?: hostDevice?.fileCount ?: 0}",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("المجلدات المفحوصة", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = "${vaultSummary?.foldersFound ?: hostDevice?.folderCount ?: 0}",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("الحجم الإجمالي", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = StorageUtils.formatFileSize(vaultSummary?.totalSizeBytes ?: hostDevice?.vaultSizeBytes ?: 0L),
                                    color = ElectricBlue,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        StorageProgressBar(
                            freeBytes = hostDevice?.storageFreeBytes ?: 18_500_000_000L,
                            totalBytes = hostDevice?.storageTotalBytes ?: 64_000_000_000L,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ACTIVE COMMANDS PROGRESS
            val activeCmds = commands.filter { it.status == "RUNNING" || it.status == "PENDING" }
            if (activeCmds.isNotEmpty()) {
                item {
                    Text(
                        text = "العمليات الجارية الآن",
                        color = ElectricBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(activeCmds, key = { it.commandId }) { cmd ->
                    CommandProgressCard(
                        command = cmd,
                        onCancel = { onCancelCommand(cmd.commandId) },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // PAIRING CODE DIALOG
    if (showPairingDialog && activePairingCode != null) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            containerColor = SlateCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "رمز اقتران الآدمن",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "أدخل هذا الرمز المكون من 6 أرقام على هاتف الآدمن للربط الفوري:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    PairingCodeCard(
                        code = activePairingCode.code,
                        expiresAt = activePairingCode.expiresAt
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPairingDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تم", color = SlateDark, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // BROWSE FOLDER HIERARCHY DIALOG FOR HOST
    if (folderToBrowse != null) {
        val currentFolder = folderToBrowse!!
        val folderFiles = hostFiles.filter {
            it.vaultId == currentFolder.folderId ||
            it.folderId == currentFolder.folderId ||
            it.vaultId == currentFolder.pathOrUri ||
            it.folderId == currentFolder.pathOrUri ||
            (currentFolder.pathOrUri.isNotBlank() && it.uriString.contains(currentFolder.pathOrUri)) ||
            (currentFolder.name.isNotBlank() && it.relativePath.replace('\\', '/').startsWith("${currentFolder.name}/", ignoreCase = true)) ||
            (currentFolder.name.isNotBlank() && it.relativePath.contains(currentFolder.name, ignoreCase = true)) ||
            (currentFolder.folderId == "folder_default" && (it.vaultId.isBlank() || it.vaultId == "folder_default"))
        }.ifEmpty {
            if (sharedFolders.size <= 1) hostFiles else {
                val fallback = hostFiles.filter {
                    (currentFolder.name.isNotBlank() && it.relativePath.contains(currentFolder.name, ignoreCase = true))
                }
                if (fallback.isNotEmpty()) fallback else hostFiles
            }
        }
        HostFolderExplorerDialog(
            folder = currentFolder,
            files = folderFiles,
            onDismiss = { folderToBrowse = null },
            onUploadFiles = onUploadFiles
        )
    }
}

@Composable
fun SharedFolderHostItem(
    index: Int,
    folder: SharedFolder,
    onBrowse: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$index. ${folder.name}",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.fileCount} ملفات • ${StorageUtils.formatFileSize(folder.totalSizeBytes)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBrowse,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("استعراض", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = RoseError.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HostFolderExplorerDialog(
    folder: SharedFolder,
    files: List<VaultFile>,
    onDismiss: () -> Unit,
    onUploadFiles: (List<VaultFile>) -> Unit = {}
) {
    var currentSubPath by remember { mutableStateOf("") }
    var selectedFileIds by remember { mutableStateOf(setOf<String>()) }
    var viewMode by remember { mutableStateOf("folders") } // "folders" or "all_files"
    var searchQuery by remember { mutableStateOf("") }

    val (subfolders, directFiles) = remember(files, currentSubPath) {
        StorageUtils.getItemsForPath(files, currentSubPath)
    }

    val displayFiles = remember(files, searchQuery, viewMode, directFiles) {
        if (viewMode == "all_files" || searchQuery.isNotBlank()) {
            if (searchQuery.isBlank()) files.sortedByDescending { it.lastModified }
            else files.filter { it.name.contains(searchQuery, ignoreCase = true) || it.relativePath.contains(searchQuery, ignoreCase = true) }.sortedByDescending { it.lastModified }
        } else {
            directFiles
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${files.size} ملفات متوفرة في هذا المجلد",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // View Mode Switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = viewMode == "folders",
                        onClick = { viewMode = "folders" },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("تصفح بالمجلدات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricBlue,
                            containerColor = SlateCardElevated,
                            labelColor = TextSecondary
                        )
                    )

                    FilterChip(
                        selected = viewMode == "all_files",
                        onClick = { viewMode = "all_files" },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("جميع الملفات (${files.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricBlue,
                            containerColor = SlateCardElevated,
                            labelColor = TextSecondary
                        )
                    )
                }

                // Search Bar
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن اسم الملف...", fontSize = 11.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )

                // Breadcrumb path & Back (only in folder mode when inside a subfolder)
                if (viewMode == "folders" && currentSubPath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val idx = currentSubPath.lastIndexOf('/')
                            currentSubPath = if (idx >= 0) currentSubPath.substring(0, idx) else ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "رجوع ($currentSubPath)",
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                // If in folder mode: Show Subdirectories first
                if (viewMode == "folders" && searchQuery.isBlank() && subfolders.isNotEmpty()) {
                    item {
                        Text(
                            text = "المجلدات الفرعية (${subfolders.size}):",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    items(subfolders) { sub ->
                        Surface(
                            color = SlateCardElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable {
                                    currentSubPath = sub.relativePath
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sub.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${sub.fileCount} ملفات • ${StorageUtils.formatDate(sub.lastModified)}",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // If in folder mode and direct files is empty but subfolders exist: Show clear helper card
                if (viewMode == "folders" && searchQuery.isBlank() && directFiles.isEmpty() && subfolders.isNotEmpty()) {
                    item {
                        Surface(
                            color = ElectricBlue.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "الملفات موجودة داخل المجلدات الفرعية أعلاه (${files.size} ملف إجمالاً).",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { viewMode = "all_files" }
                                ) {
                                    Text("عرض جميع الملفات (${files.size}) مباشرة هنا", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Header for files
                val filesTitle = if (viewMode == "all_files") {
                    if (searchQuery.isNotBlank()) "نتائج البحث (${displayFiles.size}):" else "جميع الملفات (${displayFiles.size}) - الأحدث أولاً:"
                } else {
                    if (searchQuery.isNotBlank()) "نتائج البحث (${displayFiles.size}):" else "الملفات في هذا المجلد (${displayFiles.size}):"
                }

                if (displayFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = filesTitle,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }

                    items(displayFiles) { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFileIds.contains(f.fileId),
                                onCheckedChange = { isChecked ->
                                    selectedFileIds = if (isChecked) {
                                        selectedFileIds + f.fileId
                                    } else {
                                        selectedFileIds - f.fileId
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = ElectricBlue),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FileCategoryIcon(category = f.category, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                val subtitle = if (viewMode == "all_files" && f.relativePath.contains('/')) {
                                    "${f.relativePath.substringBeforeLast('/')} • ${StorageUtils.formatFileSize(f.size)}"
                                } else {
                                    "${StorageUtils.formatFileSize(f.size)} • ${StorageUtils.formatDate(f.lastModified)}"
                                }
                                Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            if (f.isBackedUp) {
                                Surface(
                                    color = EmeraldOnline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldOnline,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("مرفوع", color = EmeraldOnline, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Button(
                                onClick = { onUploadFiles(listOf(f)) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("upload_file_${f.fileId}")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = SlateDark, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(if (f.isBackedUp) "إعادة رفع" else "رفع", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                } else if (subfolders.isEmpty() || viewMode == "all_files") {
                    item {
                        Surface(
                            color = SlateCardElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "لا توجد ملفات تطابق بحثك" else "لا توجد ملفات في هذا المسار",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                if (viewMode != "all_files" && files.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewMode = "all_files" }) {
                                        Text("عرض جميع ملفات المجلد (${files.size})", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedFileIds.isNotEmpty()) {
                Button(
                    onClick = {
                        val toUpload = files.filter { it.fileId in selectedFileIds }
                        onUploadFiles(toUpload)
                        selectedFileIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("host_upload_selected_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SlateDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رفع المحدد (${selectedFileIds.size})", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إغلاق", color = SlateDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (selectedFileIds.isNotEmpty()) {
                TextButton(onClick = { selectedFileIds = emptySet() }) {
                    Text("إلغاء التحديد", color = TextSecondary)
                }
            }
        }
    )
}
