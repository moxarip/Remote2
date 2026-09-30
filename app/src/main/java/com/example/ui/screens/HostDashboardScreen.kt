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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
    onScanVault: () -> Unit,
    onSelectVaultPath: (String) -> Unit,
    onAddSharedFolder: (String) -> Unit = {},
    onRemoveSharedFolder: (String) -> Unit = {},
    onGeneratePairingCode: () -> Unit,
    onCancelCommand: (String) -> Unit,
    onNavigateSettings: () -> Unit
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

            // Storage Permission Alert if needed
            if (!StorageUtils.hasStoragePermission(context)) {
                item {
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إذن قراءة جميع الملفات والمجلدات",
                                    color = ElectricBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "لقراءة الملفات ومحتويات المجلدات (مثل الصور والمستندات) دون قيود، يرجى تفعيل إذن الوصول للملفات.",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(StorageUtils.getAllFilesAccessIntent(context))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Text(
                                    text = "تفعيل الإذن بالكامل (All Files Access)",
                                    color = SlateDark,
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
                            Text(
                                text = "لم يتم إضافة أي مجلدات بعد. اضغط على (+) لإضافة مجلد من الهاتف.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
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
        val folderFiles = hostFiles.filter { it.vaultId == currentFolder.folderId }
        HostFolderExplorerDialog(
            folder = currentFolder,
            files = folderFiles,
            onDismiss = { folderToBrowse = null }
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
    onDismiss: () -> Unit
) {
    var currentSubPath by remember { mutableStateOf("") }
    val (subfolders, directFiles) = remember(files, currentSubPath) {
        StorageUtils.getItemsForPath(files, currentSubPath)
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
                    Text(
                        text = folder.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                // Breadcrumb path & Back
                if (currentSubPath.isNotEmpty()) {
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
                // Subdirectories (folders)
                if (subfolders.isNotEmpty()) {
                    item {
                        Text(
                            text = "المجلدات الفرعية (${subfolders.size})",
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
                                Icon(Icons.Default.Folder, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
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

                // Direct files in this directory (sorted by lastModified descending)
                item {
                    Text(
                        text = "الملفات (${directFiles.size})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                }

                if (directFiles.isEmpty() && subfolders.isEmpty()) {
                    item {
                        Text(
                            text = "هذا المجلد فارغ",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(directFiles) { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileCategoryIcon(category = f.category, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                Text(
                                    "${StorageUtils.formatFileSize(f.size)} • ${StorageUtils.formatDate(f.lastModified)}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إغلاق", color = SlateDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}
