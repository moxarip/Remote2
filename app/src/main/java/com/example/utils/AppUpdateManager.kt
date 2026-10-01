package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.firebase.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String = "",
    val sizeBytes: Long = 0L,
    val isNewer: Boolean = false
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_GITHUB_REPO = "github_repo"
    const val DEFAULT_GITHUB_REPO = "bybymoxarip/Remote2"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    // -1 = idle, 0..100 = downloading %, 101 = downloaded & ready to install, -2 = error
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun getSavedGitHubRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
    }

    fun saveGitHubRepo(context: Context, repo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
        _downloadProgress.value = -1
    }

    /**
     * Checks both GitHub Releases API and Firebase RTDB for a newer version than current app.
     */
    fun checkForUpdates(context: Context, forceManualPrompt: Boolean = false) {
        if (_isChecking.value) return
        scope.launch {
            _isChecking.value = true
            _statusMessage.value = "جاري التحقق من التحديثات عبر GitHub..."
            try {
                val currentVersionName = BuildConfig.VERSION_NAME
                val currentVersionCode = BuildConfig.VERSION_CODE
                val repo = getSavedGitHubRepo(context)

                var foundUpdate: AppUpdateInfo? = null

                // 1. Check GitHub Releases API
                try {
                    val cleanRepo = repo.removePrefix("https://github.com/").removeSuffix(".git").trim('/')
                    val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
                    val req = Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "RemoteBackup-AndroidApp")
                        .build()

                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                            val releaseName = json.optString("name", "تحديث جديد")
                            val bodyNotes = json.optString("body", "تحسينات عامة وإصلاحات في الأداء ومزامنة الملفات.")
                            val publishedAt = json.optString("published_at", "")

                            var apkUrl = ""
                            var apkSize = 0L

                            val assets = json.optJSONArray("assets")
                            if (assets != null) {
                                for (i in 0 until assets.length()) {
                                    val asset = assets.optJSONObject(i) ?: continue
                                    val assetName = asset.optString("name", "")
                                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                                        apkUrl = asset.optString("browser_download_url", "")
                                        apkSize = asset.optLong("size", 0L)
                                        break
                                    }
                                }
                            }

                            if (apkUrl.isBlank()) {
                                // Default direct fallback URL for GitHub Releases
                                apkUrl = "https://github.com/$cleanRepo/releases/latest/download/app-debug.apk"
                            }

                            val isNewer = isVersionNewer(tagName, currentVersionName)
                            if (isNewer || forceManualPrompt) {
                                foundUpdate = AppUpdateInfo(
                                    versionName = if (tagName.isNotBlank()) tagName else releaseName,
                                    versionCode = currentVersionCode + 1,
                                    downloadUrl = apkUrl,
                                    releaseNotes = bodyNotes,
                                    publishedAt = publishedAt,
                                    sizeBytes = apkSize,
                                    isNewer = isNewer
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GitHub check failed: ${e.message}")
                }

                // 2. Check Firebase RTDB fallback if GitHub didn't return an update
                if (foundUpdate == null) {
                    try {
                        val rtdbUrl = "${FirebaseManager.DEFAULT_DATABASE_URL}/app_version.json"
                        val req = Request.Builder().url(rtdbUrl).build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body != "null") {
                                val json = JSONObject(body)
                                val vName = json.optString("versionName", "")
                                val vCode = json.optInt("versionCode", currentVersionCode)
                                val dlUrl = json.optString("downloadUrl", "")
                                val notes = json.optString("releaseNotes", "تحسينات عامة وإصلاحات.")
                                val isNewer = vCode > currentVersionCode || isVersionNewer(vName, currentVersionName)
                                if (isNewer && dlUrl.isNotBlank()) {
                                    foundUpdate = AppUpdateInfo(
                                        versionName = vName,
                                        versionCode = vCode,
                                        downloadUrl = dlUrl,
                                        releaseNotes = notes,
                                        isNewer = true
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase version check failed: ${e.message}")
                    }
                }

                _updateInfo.value = foundUpdate
                if (foundUpdate != null) {
                    _statusMessage.value = "يتوفر تحديث جديد: الإصدار ${foundUpdate.versionName}"
                } else {
                    _statusMessage.value = "التطبيق محدث إلى آخر إصدار ($currentVersionName)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}")
                _statusMessage.value = "تعذر التحقق من التحديثات حالياً."
            } finally {
                _isChecking.value = false
            }
        }
    }

    /**
     * Downloads APK with progress percentage and triggers the system package installer.
     */
    fun downloadAndInstallUpdate(context: Context, update: AppUpdateInfo) {
        if (_downloadProgress.value in 0..100) return
        scope.launch {
            try {
                _downloadProgress.value = 0
                _statusMessage.value = "جاري الاتصال وتنزيل التحديث..."

                val req = Request.Builder()
                    .url(update.downloadUrl)
                    .header("User-Agent", "RemoteBackup-AndroidApp")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    _downloadProgress.value = -2
                    _statusMessage.value = "فشل التنزيل من الخادم (${resp.code})"
                    return@launch
                }

                val body = resp.body ?: run {
                    _downloadProgress.value = -2
                    _statusMessage.value = "ملف التحديث غير متاح للتنزيل"
                    return@launch
                }

                val contentLength = body.contentLength()
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val apkFile = File(targetDir, "RemoteBackup_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0L) {
                        val percent = ((totalBytesRead * 100) / contentLength).toInt()
                        _downloadProgress.value = percent
                    } else {
                        _downloadProgress.value = 50
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (apkFile.exists() && apkFile.length() > 100_000L) {
                    _downloadProgress.value = 101
                    _statusMessage.value = "اكتمل التنزيل بنجاح! جاري فتح معالج التثبيت..."
                    withContext(Dispatchers.Main) {
                        launchInstallIntent(context, apkFile)
                    }
                } else {
                    _downloadProgress.value = -2
                    _statusMessage.value = "الملف المنزّل غير صالح، يرجى المحاولة لاحقاً."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                _downloadProgress.value = -2
                _statusMessage.value = "خطأ أثناء تنزيل التحديث: ${e.message}"
            }
        }
    }

    /**
     * Installs already downloaded APK file if present.
     */
    fun installDownloadedApk(context: Context) {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val apkFile = File(targetDir, "RemoteBackup_update.apk")
        if (apkFile.exists() && apkFile.length() > 100_000L) {
            launchInstallIntent(context, apkFile)
        } else {
            _statusMessage.value = "ملف التحديث غير موجود، يرجى إعادة التنزيل."
        }
    }

    /**
     * Checks if a valid downloaded update APK already exists on disk.
     */
    fun hasDownloadedApk(context: Context): Boolean {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val apkFile = File(targetDir, "RemoteBackup_update.apk")
        return apkFile.exists() && apkFile.length() > 100_000L
    }

    /**
     * Launches the Android Package Installer via FileProvider.
     */
    fun launchInstallIntent(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                _statusMessage.value = "تعذر العثور على ملف التحديث."
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    _statusMessage.value = "يرجى السماح بتثبيت التطبيقات من هذا المصدر للمتابعة."
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Explicitly grant read uri permissions to potential installer activities
            val resInfoList = context.packageManager.queryIntentActivities(installIntent, 0)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}")
            _statusMessage.value = "تعذر فتح معالج التثبيت: ${e.message}"
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.0.3" vs "1.0.2").
     */
    private fun isVersionNewer(newVer: String, currentVer: String): Boolean {
        if (newVer.isBlank() || currentVer.isBlank()) return false
        val cleanNew = newVer.trim().removePrefix("v")
        val cleanCur = currentVer.trim().removePrefix("v")
        if (cleanNew == cleanCur) return false

        val newParts = cleanNew.split('.').mapNotNull { it.toIntOrNull() }
        val curParts = cleanCur.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(newParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val n = newParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }
}
