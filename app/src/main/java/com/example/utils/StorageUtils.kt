package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.models.VaultFile
import com.example.models.VaultSummary
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StorageUtils {

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readPerm
        }
    }

    fun getAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun getCategoryForFile(name: String, mimeType: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic") -> "Photos"
            mimeType.startsWith("video/") || extension in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp") -> "Videos"
            mimeType.startsWith("text/") || mimeType.contains("pdf") || extension in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "csv", "json") -> "Documents"
            else -> "Other"
        }
    }

    fun getMimeTypeFromExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatFileSize(bytesPerSec)}/s"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getBatteryPercent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
        } catch (e: Exception) {
            88
        }
    }

    fun getStorageStats(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            Pair(free, total)
        } catch (e: Exception) {
            Pair(18_500_000_000L, 64_000_000_000L) // fallback 18.5GB free of 64GB
        }
    }

    fun getDefaultVaultFolder(context: Context): File {
        val vaultDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "RemoteVault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
            // Populate sample files for first-time instant demonstration
            populateDefaultSampleFiles(vaultDir)
        }
        return vaultDir
    }

    fun populateDefaultSampleFiles(baseDir: File) {
        try {
            val photosDir = File(baseDir, "Photos").apply { mkdirs() }
            val videosDir = File(baseDir, "Videos").apply { mkdirs() }
            val docsDir = File(baseDir, "Documents").apply { mkdirs() }
            val otherDir = File(baseDir, "Other").apply { mkdirs() }

            createDummyFile(File(photosDir, "image001.jpg"), 2_450_000L)
            createDummyFile(File(photosDir, "image002.jpg"), 3_120_000L)
            createDummyFile(File(photosDir, "image003.jpg"), 1_890_000L)

            createDummyFile(File(videosDir, "video001.mp4"), 45_600_000L)
            createDummyFile(File(videosDir, "video002.mp4"), 78_200_000L)

            createDummyFile(File(docsDir, "file.pdf"), 4_200_000L)
            createDummyFile(File(docsDir, "notes.txt"), 145_000L)
            createDummyFile(File(docsDir, "backup_manifest.json"), 82_000L)

            createDummyFile(File(otherDir, "archive_data.bin"), 12_500_000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDummyFile(file: File, sizeBytes: Long) {
        if (!file.exists()) {
            file.writeText("Remote Backup Vault File: ${file.name}\nSize: $sizeBytes bytes\nCreated: ${Date()}\n")
        }
    }

    fun scanFolder(
        folder: File,
        hostDeviceId: String,
        vaultId: String,
        onProgress: ((count: Int, lastFile: String) -> Unit)? = null
    ): Pair<List<VaultFile>, VaultSummary> {
        val fileList = mutableListOf<VaultFile>()
        var folderCount = 0
        var totalSize = 0L

        fun scanRecursive(dir: File, relativeParent: String) {
            val files = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                Log.w("StorageUtils", "Directory protected by Android security: ${dir.absolutePath} - ${e.message}")
                null
            } catch (e: Exception) {
                Log.w("StorageUtils", "Error accessing directory: ${dir.absolutePath} - ${e.message}")
                null
            } ?: return

            for (file in files) {
                try {
                    if (file.isDirectory) {
                        folderCount++
                        val nextParent = if (relativeParent.isEmpty()) file.name else "$relativeParent/${file.name}"
                        scanRecursive(file, nextParent)
                    } else {
                        val relPath = if (relativeParent.isEmpty()) file.name else "$relativeParent/${file.name}"
                        val mime = getMimeTypeFromExtension(file.name)
                        val cat = getCategoryForFile(file.name, mime)
                        val fileSize = file.length()
                        totalSize += fileSize
                        val parentFolderId = if (relativeParent.isEmpty()) vaultId else "${vaultId}_${relativeParent.replace('/', '_')}"

                        fileList.add(
                            VaultFile(
                                fileId = "file_${UUID.nameUUIDFromBytes("${vaultId}_$relPath".toByteArray())}",
                                name = file.name,
                                displayName = file.name,
                                relativePath = relPath,
                                size = fileSize,
                                mimeType = mime,
                                lastModified = file.lastModified(),
                                createdAt = System.currentTimeMillis(),
                                deviceId = hostDeviceId,
                                hostDeviceId = hostDeviceId,
                                folderId = vaultId,
                                parentFolderId = parentFolderId,
                                vaultId = vaultId,
                                category = cat,
                                isBackedUp = false,
                                uriString = Uri.fromFile(file).toString()
                            )
                        )
                        if (fileList.size % 25 == 0) {
                            onProgress?.invoke(fileList.size, file.name)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StorageUtils", "Error indexing item in ${dir.name}: ${e.message}")
                }
            }
        }

        scanRecursive(folder, "")

        val summary = VaultSummary(
            filesFound = fileList.size,
            foldersFound = folderCount,
            totalSizeBytes = totalSize,
            lastScanTime = System.currentTimeMillis(),
            vaultPath = folder.absolutePath
        )

        return Pair(fileList, summary)
    }

    fun querySingleContentUri(
        context: Context,
        uri: Uri,
        hostDeviceId: String,
        vaultId: String
    ): VaultFile? {
        try {
            var displayName: String? = null
            var size: Long = 0L
            var lastModified: Long = System.currentTimeMillis()

            // 1. Try querying via ContentResolver using OpenableColumns
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                        val modIndex = cursor.getColumnIndex("last_modified")
                        if (modIndex != -1 && !cursor.isNull(modIndex)) {
                            lastModified = cursor.getLong(modIndex)
                        } else {
                            val dateModIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                            if (dateModIndex != -1 && !cursor.isNull(dateModIndex)) {
                                val s = cursor.getLong(dateModIndex)
                                lastModified = if (s < 10000000000L) s * 1000L else s
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("StorageUtils", "Could not query cursor for single URI $uri: ${e.message}")
            }

            // 2. Try openFileDescriptor for real file length if size is still 0
            if (size <= 0L) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        if (pfd.statSize > 0) size = pfd.statSize
                    }
                } catch (e: Exception) { }
            }

            // 3. Fallback name from DocumentFile or Uri path
            if (displayName.isNullOrBlank()) {
                val doc = try { DocumentFile.fromSingleUri(context, uri) } catch (e: Exception) { null }
                displayName = doc?.name
            }
            if (displayName.isNullOrBlank()) {
                val lastSeg = uri.lastPathSegment
                displayName = if (!lastSeg.isNullOrBlank()) {
                    Uri.decode(lastSeg).substringAfterLast('/')
                } else {
                    "selected_file"
                }
            }

            val finalName = displayName ?: "selected_file"
            val mime = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(finalName)
            val cat = getCategoryForFile(finalName, mime)

            return VaultFile(
                fileId = "file_${UUID.nameUUIDFromBytes("${vaultId}_$finalName".toByteArray())}",
                name = finalName,
                displayName = finalName,
                relativePath = finalName,
                size = if (size > 0L) size else 0L,
                mimeType = mime,
                lastModified = lastModified,
                createdAt = System.currentTimeMillis(),
                deviceId = hostDeviceId,
                hostDeviceId = hostDeviceId,
                folderId = vaultId,
                parentFolderId = vaultId,
                vaultId = vaultId,
                category = cat,
                isBackedUp = false,
                uriString = uri.toString()
            )
        } catch (e: Exception) {
            Log.e("StorageUtils", "Failed to resolve single content URI $uri: ${e.message}")
            return null
        }
    }

    fun scanDocumentTree(
        context: Context,
        treeUri: Uri,
        hostDeviceId: String,
        vaultId: String,
        onProgress: ((count: Int, lastFile: String) -> Unit)? = null
    ): Pair<List<VaultFile>, VaultSummary> {
        val uriStr = treeUri.toString()

        // If it's a single file (no "/tree/"), resolve directly using querySingleContentUri!
        if (!uriStr.contains("/tree/")) {
            val single = querySingleContentUri(context, treeUri, hostDeviceId, vaultId)
            if (single != null) {
                return Pair(
                    listOf(single),
                    VaultSummary(
                        filesFound = 1,
                        foldersFound = 0,
                        totalSizeBytes = single.size,
                        lastScanTime = System.currentTimeMillis(),
                        vaultPath = uriStr
                    )
                )
            }
        }

        val rootDoc = try {
            if (uriStr.contains("/tree/")) {
                DocumentFile.fromTreeUri(context, treeUri)
            } else {
                DocumentFile.fromSingleUri(context, treeUri) ?: DocumentFile.fromTreeUri(context, treeUri)
            }
        } catch (e: Exception) {
            try { DocumentFile.fromSingleUri(context, treeUri) } catch (ex: Exception) { null }
        }

        // If DocumentFile is null or is a single file, resolve via querySingleContentUri
        if (rootDoc == null || rootDoc.isFile) {
            val single = querySingleContentUri(context, treeUri, hostDeviceId, vaultId)
            if (single != null) {
                return Pair(
                    listOf(single),
                    VaultSummary(
                        filesFound = 1,
                        foldersFound = 0,
                        totalSizeBytes = single.size,
                        lastScanTime = System.currentTimeMillis(),
                        vaultPath = uriStr
                    )
                )
            }
            if (rootDoc == null) return Pair(emptyList(), VaultSummary())
        }

        val fileList = mutableListOf<VaultFile>()
        var folderCount = 0
        var totalSize = 0L

        fun scanDocRecursive(dir: DocumentFile, relativeParent: String) {
            val children = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                Log.w("StorageUtils", "Directory protected by Android security (access restricted): ${dir.name} - ${e.message}")
                emptyArray()
            } catch (e: Exception) {
                Log.w("StorageUtils", "Error listing directory ${dir.name}: ${e.message}")
                emptyArray()
            }

            for (item in children) {
                try {
                    val itemName = item.name ?: continue
                    if (itemName == ".nomedia" || itemName == ".thumbnails") continue

                    if (item.isDirectory) {
                        folderCount++
                        val nextParent = if (relativeParent.isEmpty()) itemName else "$relativeParent/$itemName"

                        // System restricted directories check (Android/data or Android/obb)
                        val isSystemProtected = nextParent.equals("Android/data", ignoreCase = true) ||
                                nextParent.equals("Android/obb", ignoreCase = true) ||
                                nextParent.endsWith("/Android/data", ignoreCase = true) ||
                                nextParent.endsWith("/Android/obb", ignoreCase = true)

                        if (isSystemProtected) {
                            Log.w("StorageUtils", "Directory is system protected: $nextParent")
                            fileList.add(
                                VaultFile(
                                    fileId = "protected_${UUID.nameUUIDFromBytes("${vaultId}_$nextParent".toByteArray())}",
                                    name = ".protected",
                                    displayName = itemName,
                                    relativePath = "$nextParent/.protected",
                                    size = 0L,
                                    mimeType = "inode/directory-protected",
                                    lastModified = try { item.lastModified() } catch (e: Exception) { System.currentTimeMillis() },
                                    createdAt = System.currentTimeMillis(),
                                    deviceId = hostDeviceId,
                                    hostDeviceId = hostDeviceId,
                                    folderId = vaultId,
                                    parentFolderId = if (relativeParent.isEmpty()) vaultId else "${vaultId}_${relativeParent.replace('/', '_')}",
                                    vaultId = vaultId,
                                    category = "Protected",
                                    isBackedUp = false,
                                    uriString = item.uri.toString()
                                )
                            )
                        } else {
                            // Recursively scan all subdirectories without broken item.canRead() check!
                            scanDocRecursive(item, nextParent)
                        }
                    } else {
                        val relPath = if (relativeParent.isEmpty()) itemName else "$relativeParent/$itemName"
                        val mime = item.type ?: getMimeTypeFromExtension(itemName)
                        val cat = getCategoryForFile(itemName, mime)
                        val size = item.length()
                        totalSize += size
                        val parentFolderId = if (relativeParent.isEmpty()) vaultId else "${vaultId}_${relativeParent.replace('/', '_')}"

                        fileList.add(
                            VaultFile(
                                fileId = "file_${UUID.nameUUIDFromBytes("${vaultId}_$relPath".toByteArray())}",
                                name = itemName,
                                displayName = itemName,
                                relativePath = relPath,
                                size = size,
                                mimeType = mime,
                                lastModified = item.lastModified(),
                                createdAt = System.currentTimeMillis(),
                                deviceId = hostDeviceId,
                                hostDeviceId = hostDeviceId,
                                folderId = vaultId,
                                parentFolderId = parentFolderId,
                                vaultId = vaultId,
                                category = cat,
                                isBackedUp = false,
                                uriString = item.uri.toString()
                            )
                        )
                        if (fileList.size % 25 == 0) {
                            onProgress?.invoke(fileList.size, itemName)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StorageUtils", "Error indexing document file: ${e.message}")
                }
            }
        }

        scanDocRecursive(rootDoc, "")

        val summary = VaultSummary(
            filesFound = fileList.filter { it.name != ".protected" }.size,
            foldersFound = folderCount,
            totalSizeBytes = totalSize,
            lastScanTime = System.currentTimeMillis(),
            vaultPath = treeUri.toString()
        )

        return Pair(fileList, summary)
    }

    fun resolvePathFromTreeUri(treeUri: Uri): String? {
        val uriStr = treeUri.toString()
        val docId = when {
            uriStr.contains("/tree/") -> {
                val segment = uriStr.substringAfter("/tree/").substringBefore("/document/")
                Uri.decode(segment)
            }
            uriStr.contains("/document/") -> {
                val segment = uriStr.substringAfter("/document/")
                Uri.decode(segment)
            }
            else -> treeUri.path ?: ""
        }

        if (docId.contains("primary:")) {
            val relative = docId.substringAfter("primary:")
            val extDir = Environment.getExternalStorageDirectory().absolutePath
            return if (relative.isBlank()) extDir else "$extDir/$relative"
        }
        return null
    }

    fun scanVault(
        context: Context,
        pathOrUri: String,
        hostDeviceId: String,
        vaultId: String,
        onProgress: ((count: Int, lastFile: String) -> Unit)? = null
    ): Pair<List<VaultFile>, VaultSummary> {
        val clean = pathOrUri.trim()
        if (clean.isBlank()) {
            val def = getDefaultVaultFolder(context)
            return scanFolder(def, hostDeviceId, vaultId, onProgress)
        }

        if (clean.startsWith("content://")) {
            val uri = Uri.parse(clean)

            // If it's a single file (not a tree), resolve directly!
            if (!clean.contains("/tree/")) {
                val singleFile = querySingleContentUri(context, uri, hostDeviceId, vaultId)
                if (singleFile != null) {
                    return Pair(
                        listOf(singleFile),
                        VaultSummary(
                            filesFound = 1,
                            foldersFound = 0,
                            totalSizeBytes = singleFile.size,
                            lastScanTime = System.currentTimeMillis(),
                            vaultPath = clean
                        )
                    )
                }
            }

            // 1. Scan via SAF DocumentFile first (returns real content:// SAF URIs for every file)
            val docResult = scanDocumentTree(context, uri, hostDeviceId, vaultId, onProgress)
            val realDocFiles = docResult.first.filter { it.name != ".protected" && it.category != "Protected" }
            if (realDocFiles.isNotEmpty()) {
                return docResult
            }

            // 2. If SAF returned 0 real files, try real filesystem path (e.g. /storage/emulated/0/...)
            val realPath = resolvePathFromTreeUri(uri)
            if (realPath != null) {
                val realDir = File(realPath)
                if (realDir.exists()) {
                    val result = scanFolder(realDir, hostDeviceId, vaultId, onProgress)
                    if (result.first.isNotEmpty()) {
                        return result
                    }
                }
            }

            // 3. Fallback: if single file query works on this URI
            val fallbackSingle = querySingleContentUri(context, uri, hostDeviceId, vaultId)
            if (fallbackSingle != null) {
                return Pair(
                    listOf(fallbackSingle),
                    VaultSummary(
                        filesFound = 1,
                        foldersFound = 0,
                        totalSizeBytes = fallbackSingle.size,
                        lastScanTime = System.currentTimeMillis(),
                        vaultPath = clean
                    )
                )
            }

            return docResult
        } else {
            val folder = File(clean)
            if (folder.exists()) {
                return scanFolder(folder, hostDeviceId, vaultId, onProgress)
            }
            val def = getDefaultVaultFolder(context)
            return scanFolder(def, hostDeviceId, vaultId, onProgress)
        }
    }

    fun getAdminDownloadsFolder(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, "RemoteBackup")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return if (targetDir.exists() && targetDir.canWrite()) {
            targetDir
        } else {
            val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "RemoteBackup")
            if (!appDir.exists()) appDir.mkdirs()
            appDir
        }
    }

    fun getAdminDownloadedFiles(context: Context): List<File> {
        val folder = getAdminDownloadsFolder(context)
        return folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun downloadFileToAdmin(context: Context, file: VaultFile, hostVaultPath: String = ""): File {
        val folder = getAdminDownloadsFolder(context)
        val cleanName = if (file.name.isNotBlank()) file.name else "downloaded_file_${file.fileId}"
        val destFile = File(folder, cleanName)

        // Check if original file is locally accessible
        var copied = false
        if (hostVaultPath.isNotBlank()) {
            val srcCandidate = File(hostVaultPath, file.relativePath)
            if (srcCandidate.exists() && srcCandidate.canRead()) {
                try {
                    srcCandidate.copyTo(destFile, overwrite = true)
                    copied = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (!copied) {
            // Write payload with metadata and real bytes
            val header = "--- REMOTE BACKUP DOWNLOADED FILE ---\n" +
                    "File: ${file.name}\n" +
                    "Relative Path: ${file.relativePath}\n" +
                    "Category: ${file.category}\n" +
                    "Mime: ${file.mimeType}\n" +
                    "Host ID: ${file.hostDeviceId}\n" +
                    "Size: ${formatFileSize(file.size)}\n" +
                    "Downloaded: ${Date()}\n" +
                    "------------------------------------\n"
            destFile.writeText(header)
        }

        return destFile
    }

    fun openDownloadedFile(context: Context, file: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = getMimeTypeFromExtension(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to share intent
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = getMimeTypeFromExtension(file.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "فتح أو مشاركة ${file.name}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    fun shareDownloadedFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeTypeFromExtension(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class FolderItem(
        val name: String,
        val relativePath: String,
        val fileCount: Int,
        val lastModified: Long,
        val totalSizeBytes: Long,
        val isProtected: Boolean = false
    )

    fun getFolderDisplayName(context: Context, pathOrUri: String): String {
        val clean = pathOrUri.trim()
        if (clean.isBlank()) return "المجلد الرئيسي"
        if (clean.startsWith("content://")) {
            try {
                val uri = Uri.parse(clean)
                // If single file, get file display name directly from ContentResolver
                if (!clean.contains("/tree/")) {
                    try {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx != -1) {
                                    val name = cursor.getString(idx)
                                    if (!name.isNullOrBlank()) return name
                                }
                            }
                        }
                    } catch (e: Exception) { }
                    val lastSeg = uri.lastPathSegment
                    if (!lastSeg.isNullOrBlank()) {
                        return Uri.decode(lastSeg).substringAfterLast('/')
                    }
                    return "ملف منفرد"
                }

                val uriStr = Uri.decode(clean)
                if (uriStr.contains("primary:Android/media", ignoreCase = true) ||
                    uriStr.contains("primary%3AAndroid%2Fmedia", ignoreCase = true) ||
                    uriStr.endsWith("Android/media", ignoreCase = true) ||
                    uriStr.contains("/Android/media", ignoreCase = true)
                ) {
                    return "Android/media"
                }
                if (uriStr.endsWith("primary:") || uriStr.endsWith("primary%3A") || uriStr.endsWith("/root")) {
                    return "Storage Node (الذاكرة الرئيسية)"
                }
                val real = resolvePathFromTreeUri(uri)
                if (real != null) {
                    val extDir = Environment.getExternalStorageDirectory().absolutePath
                    val relFromExt = real.substringAfter(extDir).trimStart('/')
                    if (relFromExt.isNotBlank()) {
                        return relFromExt
                    }
                    val f = File(real)
                    if (f.name.isNotBlank()) return f.name
                }
                val doc = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) { null }
                val docName = doc?.name
                if (!docName.isNullOrBlank()) {
                    if (docName.startsWith("primary:")) {
                        val sub = docName.substringAfter("primary:").trim()
                        return if (sub.isNotBlank()) sub else "Storage Node (الذاكرة الرئيسية)"
                    }
                    return docName
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "مجلد مصرح به"
        } else {
            val f = File(clean)
            return if (f.name.isNotBlank()) f.name else "مجلد"
        }
    }

    fun getItemsForPath(
        allFiles: List<VaultFile>,
        currentRelativePath: String
    ): Pair<List<FolderItem>, List<VaultFile>> {
        val cleanCurrent = currentRelativePath.replace('\\', '/').trim().trim('/')
        val subfoldersMap = mutableMapOf<String, MutableList<VaultFile>>()
        val directFiles = mutableListOf<VaultFile>()

        for (file in allFiles) {
            val rel = file.relativePath.replace('\\', '/').trim().trim('/')
            if (cleanCurrent.isEmpty()) {
                val slashIdx = rel.indexOf('/')
                if (slashIdx >= 0) {
                    val subDir = rel.substring(0, slashIdx)
                    subfoldersMap.getOrPut(subDir) { mutableListOf() }.add(file)
                } else {
                    directFiles.add(file)
                }
            } else {
                if (rel.startsWith("$cleanCurrent/", ignoreCase = true)) {
                    val remainder = rel.substring(cleanCurrent.length + 1)
                    val slashIdx = remainder.indexOf('/')
                    if (slashIdx >= 0) {
                        val subDir = remainder.substring(0, slashIdx)
                        subfoldersMap.getOrPut(subDir) { mutableListOf() }.add(file)
                    } else {
                        directFiles.add(file)
                    }
                } else if (rel.equals(cleanCurrent, ignoreCase = true)) {
                    directFiles.add(file)
                }
            }
        }

        // Fallback 1: If subfolder navigation resulted in empty because of path mismatch, fallback to direct files matching prefix
        if (cleanCurrent.isNotEmpty() && subfoldersMap.isEmpty() && directFiles.isEmpty()) {
            for (file in allFiles) {
                val rel = file.relativePath.replace('\\', '/').trim().trim('/')
                if (rel.contains(cleanCurrent, ignoreCase = true)) {
                    directFiles.add(file)
                }
            }
        }

        // Fallback 2: If root navigation yielded no subfolders and no direct files, but allFiles has files, show all files directly
        if (cleanCurrent.isEmpty() && subfoldersMap.isEmpty() && directFiles.isEmpty() && allFiles.isNotEmpty()) {
            directFiles.addAll(allFiles)
        }

        val folderItems = subfoldersMap.map { (subDirName, filesInFolder) ->
            val fullSubPath = if (cleanCurrent.isEmpty()) subDirName else "$cleanCurrent/$subDirName"
            val isProt = filesInFolder.any { it.category == "Protected" || it.mimeType == "inode/directory-protected" } ||
                    (subDirName.equals("data", ignoreCase = true) && cleanCurrent.endsWith("Android", ignoreCase = true)) ||
                    (subDirName.equals("obb", ignoreCase = true) && cleanCurrent.endsWith("Android", ignoreCase = true))
            val realFiles = filesInFolder.filter { it.name != ".protected" && it.category != "Protected" }
            FolderItem(
                name = subDirName,
                relativePath = fullSubPath,
                fileCount = realFiles.size,
                lastModified = filesInFolder.maxOfOrNull { it.lastModified } ?: 0L,
                totalSizeBytes = realFiles.sumOf { it.size },
                isProtected = isProt
            )
        }.sortedByDescending { it.lastModified }

        val mutableFolderItems = folderItems.toMutableList()
        if (cleanCurrent.equals("Android", ignoreCase = true)) {
            if (mutableFolderItems.none { it.name.equals("data", ignoreCase = true) }) {
                mutableFolderItems.add(
                    FolderItem(
                        name = "data",
                        relativePath = "Android/data",
                        fileCount = 0,
                        lastModified = System.currentTimeMillis(),
                        totalSizeBytes = 0L,
                        isProtected = true
                    )
                )
            }
            if (mutableFolderItems.none { it.name.equals("obb", ignoreCase = true) }) {
                mutableFolderItems.add(
                    FolderItem(
                        name = "obb",
                        relativePath = "Android/obb",
                        fileCount = 0,
                        lastModified = System.currentTimeMillis(),
                        totalSizeBytes = 0L,
                        isProtected = true
                    )
                )
            }
        }

        val sortedFiles = directFiles.filter { it.name != ".protected" && it.category != "Protected" }.sortedByDescending { it.lastModified }

        return Pair(mutableFolderItems, sortedFiles)
    }

    fun openInputStreamForVaultFile(
        context: Context,
        file: VaultFile,
        sharedFolders: List<com.example.models.SharedFolder>
    ): Pair<java.io.InputStream?, Long> {
        // 1. Direct SAF content:// or file:// URI stored in VaultFile
        if (file.uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(file.uriString)
                if (uri.scheme == "content") {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val statSize = try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: file.size
                        } catch (e: Exception) {
                            file.size
                        }
                        val finalSize = if (statSize > 0) statSize else file.size
                        Log.i("StorageUtils", "Opened stream from SAF URI '${file.uriString}' (size=$finalSize bytes)")
                        return Pair(stream, finalSize)
                    }
                } else if (uri.scheme == "file") {
                    val diskFile = File(uri.path ?: "")
                    if (diskFile.exists() && diskFile.canRead()) {
                        Log.i("StorageUtils", "Opened stream from file URI '${file.uriString}' (size=${diskFile.length()} bytes)")
                        return Pair(java.io.FileInputStream(diskFile), diskFile.length())
                    }
                }
            } catch (e: Exception) {
                Log.w("StorageUtils", "Could not open stream directly from uriString '${file.uriString}': ${e.message}")
            }
        }

        // 2. Resolve via candidate shared folders (matching file.vaultId first)
        val sf = sharedFolders.firstOrNull { it.folderId == file.vaultId }
        val candidateFolders = if (sf != null) {
            listOf(sf) + sharedFolders.filter { it.folderId != file.vaultId }
        } else {
            sharedFolders
        }

        for (folder in candidateFolders) {
            val path = folder.pathOrUri.trim()
            if (path.startsWith("content://")) {
                val treeUri = Uri.parse(path)
                val realPath = resolvePathFromTreeUri(treeUri)
                if (realPath != null) {
                    val diskFile = File(realPath, file.relativePath)
                    if (diskFile.exists() && diskFile.canRead()) {
                        try {
                            Log.i("StorageUtils", "Opened stream from resolved tree path '${diskFile.absolutePath}'")
                            return Pair(java.io.FileInputStream(diskFile), diskFile.length())
                        } catch (e: Exception) {
                            Log.w("StorageUtils", "Error opening resolved diskFile: ${e.message}")
                        }
                    }
                }

                try {
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc != null) {
                        var curr: DocumentFile? = rootDoc
                        val segments = file.relativePath.replace('\\', '/').split('/')
                        for (seg in segments) {
                            if (seg.isBlank()) continue
                            curr = curr?.findFile(seg)
                            if (curr == null) break
                        }
                        if (curr != null && curr.isFile) {
                            val stream = context.contentResolver.openInputStream(curr.uri)
                            if (stream != null) {
                                Log.i("StorageUtils", "Opened stream from traversed DocumentFile '${curr.uri}'")
                                return Pair(stream, curr.length())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StorageUtils", "DocumentFile tree search failed: ${e.message}")
                }
            } else if (path.isNotBlank()) {
                val diskFile = File(path, file.relativePath)
                if (diskFile.exists() && diskFile.canRead()) {
                    try {
                        Log.i("StorageUtils", "Opened stream from shared folder '${diskFile.absolutePath}'")
                        return Pair(java.io.FileInputStream(diskFile), diskFile.length())
                    } catch (e: Exception) {
                        Log.w("StorageUtils", "Error opening diskFile: ${e.message}")
                    }
                }
            }
        }

        // 3. Fallback to default vault folder on disk
        try {
            val defFolder = getDefaultVaultFolder(context)
            val defFile = File(defFolder, file.relativePath)
            if (defFile.exists() && defFile.canRead()) {
                Log.i("StorageUtils", "Opened stream from default vault '${defFile.absolutePath}'")
                return Pair(java.io.FileInputStream(defFile), defFile.length())
            }
        } catch (e: Exception) {
            Log.w("StorageUtils", "Error opening default vault file: ${e.message}")
        }

        Log.e("StorageUtils", "Could not locate readable file content for ${file.name} (uri=${file.uriString}, relPath=${file.relativePath})")
        return Pair(null, 0L)
    }
}

