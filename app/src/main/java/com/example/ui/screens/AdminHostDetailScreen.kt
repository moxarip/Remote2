package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.HostDevice
import com.example.models.SharedFolder
import com.example.models.VaultFile
import com.example.ui.components.BatteryBadge
import com.example.ui.components.CommandProgressCard
import com.example.ui.components.FileCategoryIcon
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberPending
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHostDetailScreen(
    host: HostDevice,
    files: List<VaultFile>,
    selectedFileIds: Set<String>,
    activeCommand: BackupCommand?,
    selectedCategory: String,
    searchQuery: String,
    downloadedFiles: List<File> = emptyList(),
    onBack: () -> Unit,
    onSendCommand: (CommandType) -> Unit,
    onCancelCommand: (String) -> Unit,
    onToggleFileSelect: (String) -> Unit,
    onSelectAllFiles: (List<VaultFile>) -> Unit,
    onClearSelection: () -> Unit,
    onCategoryChanged: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefreshFiles: () -> Unit = {},
    onDownloadFile: (VaultFile) -> Unit = {},
    onDownloadAllUploaded: (List<VaultFile>) -> Unit = {},
    onOpenFile: (File) -> Unit = {},
    onShareFile: (File) -> Unit = {},
    onDeleteDownloadedFile: (File) -> Unit = {}
) {
    // Current folder hierarchy path within the selected shared folder
    var currentRelativePath by remember { mutableStateOf("") }
    // Selected shared folder ID (null means all folders)
    var selectedSharedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // System Back Handler: navigate up subfolder first, or exit screen
    BackHandler {
        if (currentRelativePath.isNotEmpty()) {
            val slashIdx = currentRelativePath.lastIndexOf('/')
            currentRelativePath = if (slashIdx >= 0) currentRelativePath.substring(0, slashIdx) else ""
        } else if (selectedSharedFolderId != null) {
            selectedSharedFolderId = null
        } else {
            onBack()
        }
    }

    val categories = listOf("All", "Photos", "Videos", "Documents", "Other")

    // The shared folders in the exact order of addition ("يظهرون في آدمين بترتيب اضافتهم")
    val orderedSharedFolders = remember(host) {
        if (host.sharedFolders.isNotEmpty()) {
            host.sharedFolders
        } else if (host.vaultPath.isNotBlank()) {
            listOf(
                SharedFolder(
                    folderId = "vault_default",
                    name = if (host.vaultPath.contains("whatsapp", ignoreCase = true)) "واتساب (WhatsApp)" else "المجلد المصرح به",
                    pathOrUri = host.vaultPath,
                    fileCount = host.fileCount
                )
            )
        } else {
            emptyList()
        }
    }

    // Files filtered by selected shared folder (if any)
    val folderFilteredFiles = remember(files, selectedSharedFolderId, orderedSharedFolders) {
        if (selectedSharedFolderId == null) {
            files
        } else {
            val selectedFolder = orderedSharedFolders.firstOrNull { it.folderId == selectedSharedFolderId }
            val folderId = selectedFolder?.folderId ?: selectedSharedFolderId ?: ""
            val folderName = selectedFolder?.name?.trim() ?: ""
            val folderPath = selectedFolder?.pathOrUri?.trim() ?: ""

            val filtered = files.filter { file ->
                file.folderId == folderId ||
                file.vaultId == folderId ||
                file.vaultId == folderPath ||
                (folderId.contains("default", ignoreCase = true) && (file.vaultId.isBlank() || file.vaultId.contains("default", ignoreCase = true))) ||
                (folderName.isNotBlank() && file.relativePath.replace('\\', '/').startsWith("$folderName/", ignoreCase = true)) ||
                (folderName.isNotBlank() && file.relativePath.contains(folderName, ignoreCase = true))
            }

            if (filtered.isNotEmpty()) {
                filtered
            } else if (orderedSharedFolders.size <= 1) {
                files
            } else {
                val fallback = files.filter { f ->
                    (folderName.isNotBlank() && f.relativePath.contains(folderName, ignoreCase = true)) ||
                    (folderPath.isNotBlank() && f.relativePath.contains(folderPath.substringAfterLast('/'), ignoreCase = true))
                }
                if (fallback.isNotEmpty()) fallback else files
            }
        }
    }

    // Category and search filtering
    val matchingFiles = folderFilteredFiles.filter { file ->
        val matchesCategory = if (selectedCategory == "All") true else file.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = if (searchQuery.isBlank()) true else file.name.contains(searchQuery, ignoreCase = true) || file.relativePath.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    // Hierarchical partitioning: keep files inside their subfolders ("ما يوجد وسط الملف اتركه داخل الملف و ما هو خارج يبقى خارج")
    // and sort everything by last modified descending ("و اعرضهم بتاريخ اخر تعديل اولا")
    val (subfolders, directFiles) = remember(matchingFiles, currentRelativePath) {
        if (searchQuery.isNotBlank()) {
            // When user searches a query, show matching files directly with their full paths
            Pair(emptyList(), matchingFiles.sortedByDescending { it.lastModified })
        } else {
            StorageUtils.getItemsForPath(matchingFiles, currentRelativePath)
        }
    }

    // Uploaded / Backed up files list
    val uploadedFiles = files.filter { it.isBackedUp }.sortedByDescending { it.lastModified }
    val filteredUploadedFiles = uploadedFiles.filter { file ->
        val matchesCategory = if (selectedCategory == "All") true else file.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = if (searchQuery.isBlank()) true else file.name.contains(searchQuery, ignoreCase = true) || file.relativePath.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    val downloadedFileNames = remember(downloadedFiles) {
        downloadedFiles.map { it.name }.toSet()
    }

    val selectedFilesCount = selectedFileIds.size
    val selectedFilesBytes = files.filter { it.fileId in selectedFileIds }.sumOf { it.size }

    Scaffold(
        containerColor = SlateDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentRelativePath.isNotEmpty()) {
                        val slashIdx = currentRelativePath.lastIndexOf('/')
                        currentRelativePath = if (slashIdx >= 0) currentRelativePath.substring(0, slashIdx) else ""
                    } else if (selectedSharedFolderId != null) {
                        selectedSharedFolderId = null
                    } else {
                        onBack()
                    }
                }, modifier = Modifier.testTag("host_detail_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOST: ${host.name}",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(status = host.status)
                        Spacer(modifier = Modifier.width(6.dp))
                        BatteryBadge(percent = host.batteryPercent)
                    }
                }

                IconButton(
                    onClick = onRefreshFiles,
                    modifier = Modifier.testTag("admin_refresh_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Files",
                        tint = ElectricBlue
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = selectedTabIndex == 0 && selectedFilesCount > 0) {
                Surface(
                    color = SlateCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedFilesCount ملفات محددة للرفع",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = StorageUtils.formatFileSize(selectedFilesBytes),
                                color = ElectricBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onClearSelection,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text("إلغاء", color = TextSecondary, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.BACKUP) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("backup_selected_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SlateDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("رفع للسحابة", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // HOST STATS HEADER CARD
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
                                    text = "${host.fileCount}",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("المجلدات المصرحة", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = "${orderedSharedFolders.size.coerceAtLeast(1)}",
                                    color = CyanAccent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("المرفوعة للسحابة", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = "${uploadedFiles.size}",
                                    color = EmeraldOnline,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4 QUICK COMMAND BUTTONS: [ REFRESH ] [ SCAN VAULT ] [ BACKUP ] [ PULL ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSendCommand(CommandType.REFRESH) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_refresh_button")
                            ) {
                                Text("REFRESH", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.SCAN) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(40.dp)
                                    .testTag("admin_scan_vault_button")
                            ) {
                                Text("فحص الهوست", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.BACKUP) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(40.dp)
                                    .testTag("admin_backup_all_button")
                            ) {
                                Text("رفع الكل", color = SlateDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.PULL) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_pull_button")
                            ) {
                                Text("مزامنة", color = EmeraldOnline, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ACTIVE COMMAND LIVE PROGRESS BANNER (0 TO 100 PROGRESS LINE)
            if (activeCommand != null) {
                item {
                    CommandProgressCard(
                        command = activeCommand,
                        onCancel = { onCancelCommand(activeCommand.commandId) },
                        onViewUploaded = {
                            selectedTabIndex = 1
                        },
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }

            // 3 NAVIGATION TABS
            item {
                Surface(
                    color = SlateCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = ElectricBlue,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = ElectricBlue
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = {
                                Text(
                                    text = "ملفات الهوست (${files.size})",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == 0) ElectricBlue else TextSecondary
                                )
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = {
                                Text(
                                    text = "المرفوعة (${uploadedFiles.size})",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == 1) EmeraldOnline else TextSecondary
                                )
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = {
                                Text(
                                    text = "المحملة (${downloadedFiles.size})",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == 2) CyanAccent else TextSecondary
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // --- TAB CONTENT ---
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: HIERARCHICAL FOLDERS & FILES EXPLORER

                    // 1. ORDERED SHARED FOLDERS SELECTOR (User Requirement: "يظهرون في آدمين بترتيب اضافتهم و يمكن الدخول لكل واحد فيهم بشكل منفصل")
                    if (orderedSharedFolders.isNotEmpty()) {
                        item {
                            Text(
                                text = "المجلدات المصرح بها (بترتيب الإضافة):",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedSharedFolderId == null,
                                        onClick = {
                                            selectedSharedFolderId = null
                                            currentRelativePath = ""
                                        },
                                        label = { Text("جميع المجلدات (${files.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                                            selectedLabelColor = ElectricBlue,
                                            containerColor = SlateCard,
                                            labelColor = TextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selectedSharedFolderId == null,
                                            borderColor = if (selectedSharedFolderId == null) ElectricBlue else SlateBorder
                                        )
                                    )
                                }

                                items(orderedSharedFolders) { sf ->
                                    val isSelected = selectedSharedFolderId == sf.folderId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedSharedFolderId = sf.folderId
                                            currentRelativePath = ""
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (isSelected) ElectricBlue else CyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "${sf.name} (${sf.fileCount})",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                                            selectedLabelColor = ElectricBlue,
                                            containerColor = SlateCard,
                                            labelColor = TextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) ElectricBlue else SlateBorder
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // SEARCH BAR
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChanged,
                            placeholder = { Text("بحث عن اسم الملف أو المسار...", color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("file_search_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // CATEGORY FILTER PILLS
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { onCategoryChanged(cat) },
                                    label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricBlue,
                                        containerColor = SlateCard,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedCategory == cat,
                                        borderColor = if (selectedCategory == cat) ElectricBlue else SlateBorder
                                    ),
                                    modifier = Modifier.testTag("filter_chip_$cat")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 2. BREADCRUMBS & DIRECTORY PATH BAR (User Requirement: keep files inside their folders)
                    item {
                        Surface(
                            color = SlateCardElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (currentRelativePath.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                val slashIdx = currentRelativePath.lastIndexOf('/')
                                                currentRelativePath = if (slashIdx >= 0) currentRelativePath.substring(0, slashIdx) else ""
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back Folder",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Root Folder",
                                            tint = ElectricBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    val activeFolderName = orderedSharedFolders.firstOrNull { it.folderId == selectedSharedFolderId }?.name ?: "الرئيسي"
                                    Text(
                                        text = if (currentRelativePath.isEmpty()) "$activeFolderName/" else "$activeFolderName/$currentRelativePath/",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                TextButton(
                                    onClick = { onSelectAllFiles(matchingFiles) },
                                    modifier = Modifier.testTag("select_all_files_button")
                                ) {
                                    Text("تحديد الكل", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. SUBDIRECTORIES INSIDE CURRENT FOLDER (NOT flattened!)
                    if (searchQuery.isBlank() && subfolders.isNotEmpty()) {
                        item {
                            Text(
                                text = "المجلدات الفرعية (${subfolders.size}) - مرتبة حسب تاريخ التعديل:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        items(subfolders, key = { "subfolder_${it.relativePath}" }) { sub ->
                            AdminSubfolderItemCard(
                                folder = sub,
                                onClick = {
                                    currentRelativePath = sub.relativePath
                                },
                                onSelectAllInFolder = {
                                    val filesInFolder = matchingFiles.filter { it.relativePath.startsWith("${sub.relativePath}/") || it.relativePath == sub.relativePath }
                                    onSelectAllFiles(filesInFolder)
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    // 4. DIRECT FILES IN CURRENT FOLDER (sorted by lastModified descending)
                    if (directFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = if (searchQuery.isNotBlank()) "نتائج البحث (${directFiles.size}):" else "الملفات في هذا المجلد (${directFiles.size}) - الأحدث أولاً:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                        }

                        items(directFiles, key = { it.fileId }) { file ->
                            val isSelected = selectedFileIds.contains(file.fileId)
                            val isDownloaded = downloadedFileNames.contains(file.name)
                            VaultFileRowItem(
                                file = file,
                                isSelected = isSelected,
                                isDownloaded = isDownloaded,
                                onToggle = { onToggleFileSelect(file.fileId) },
                                onQuickDownload = { onDownloadFile(file) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    } else if (subfolders.isEmpty()) {
                        if (matchingFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "الملفات (${matchingFiles.size}):",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                )
                            }
                            items(matchingFiles, key = { it.fileId }) { file ->
                                val isSelected = selectedFileIds.contains(file.fileId)
                                val isDownloaded = downloadedFileNames.contains(file.name)
                                VaultFileRowItem(
                                    file = file,
                                    isSelected = isSelected,
                                    isDownloaded = isDownloaded,
                                    onToggle = { onToggleFileSelect(file.fileId) },
                                    onQuickDownload = { onDownloadFile(file) },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        } else {
                            item {
                                Surface(
                                    color = SlateCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Text(
                                            text = "هذا المجلد لا يحتوي على أي ملفات مطابقة",
                                            color = TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: UPLOADED / BACKED UP FILES
                    item {
                        Surface(
                            color = EmeraldOnline.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = EmeraldOnline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "الملفات المرفوعة للسحابة",
                                            color = EmeraldOnline,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${uploadedFiles.size} ملفات تم رفعها بنجاح مرتبة بالأحدث",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                if (uploadedFiles.isNotEmpty()) {
                                    Button(
                                        onClick = { onDownloadAllUploaded(uploadedFiles) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldOnline),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = SlateDark, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تنزيل الكل", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (filteredUploadedFiles.isEmpty()) {
                        item {
                            Surface(
                                color = SlateCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "لم يتم رفع أي ملفات للسحابة بعد",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "حدد ملفات من تبويب 'كل ملفات الهوست' ثم اضغط 'رفع للسحابة' لتظهر لك هنا مع إمكانية تنزيلها لهاتفك.",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { selectedTabIndex = 0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("اختر ملفات لرفعها الآن", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredUploadedFiles, key = { it.fileId }) { file ->
                            val isDownloaded = downloadedFileNames.contains(file.name)
                            UploadedFileCard(
                                file = file,
                                isDownloaded = isDownloaded,
                                onDownload = { onDownloadFile(file) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                2 -> {
                    // TAB 2: DOWNLOADED ON THIS ADMIN DEVICE
                    item {
                        Surface(
                            color = CyanAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "مخزن تنزيلات الآدمن",
                                            color = CyanAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${downloadedFiles.size} ملفات محفوظة في Download/RemoteBackup/",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (downloadedFiles.isEmpty()) {
                        item {
                            Surface(
                                color = SlateCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "لا توجد ملفات محملة على هاتف الآدمن بعد",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "انتقل إلى تبويب 'المرفوعة للسحابة' واضغط على زر 'تنزيل' لحفظ أي ملف على هاتفك.",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { selectedTabIndex = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("عرض الملفات المرفوعة للتنزيل", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        items(downloadedFiles, key = { it.absolutePath }) { file ->
                            DownloadedFileRow(
                                file = file,
                                onOpen = { onOpenFile(file) },
                                onShare = { onShareFile(file) },
                                onDelete = { onDeleteDownloadedFile(file) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun AdminSubfolderItemCard(
    folder: StorageUtils.FolderItem,
    onClick: () -> Unit,
    onSelectAllInFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberPending.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = AmberPending,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${folder.name}/",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.fileCount} ملفات • ${StorageUtils.formatFileSize(folder.totalSizeBytes)} • ${StorageUtils.formatDate(folder.lastModified)}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSelectAllInFolder,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("تحديد الكل", color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Folder",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun VaultFileRowItem(
    file: VaultFile,
    isSelected: Boolean,
    isDownloaded: Boolean = false,
    onToggle: () -> Unit,
    onQuickDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) SlateCardElevated else SlateCard,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ElectricBlue.copy(alpha = 0.6f) else SlateBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("file_row_${file.fileId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricBlue,
                    checkmarkColor = SlateDark,
                    uncheckedColor = TextMuted
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            FileCategoryIcon(category = file.category)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${StorageUtils.formatFileSize(file.size)} • ${StorageUtils.formatDate(file.lastModified)}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (file.isBackedUp) {
                    Text(
                        text = "مرفوع للسحابة ✓",
                        color = EmeraldOnline,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isDownloaded) {
                    Text(
                        text = "محمل على هاتفك ⬇",
                        color = CyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun UploadedFileCard(
    file: VaultFile,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileCategoryIcon(category = file.category)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${StorageUtils.formatFileSize(file.size)} • ${StorageUtils.formatDate(file.lastModified)}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• في السحابة ✓",
                        color = EmeraldOnline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isDownloaded) {
                Surface(
                    color = CyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تم التنزيل",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download",
                        tint = SlateDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تنزيل", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DownloadedFileRow(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${StorageUtils.formatFileSize(file.length())} • ${StorageUtils.formatDate(file.lastModified())}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: Open, Share, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.4f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseError, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", color = RoseError, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة", color = TextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = SlateDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فتح الملف", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
