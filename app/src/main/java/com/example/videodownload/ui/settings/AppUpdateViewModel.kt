package com.example.videodownload.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.R
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.util.AppUpdateChecker
import com.example.videodownload.util.NetworkClients
import com.example.videodownload.util.NetworkConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 应用版本更新状态机。
 *
 * 与 [UpdateState]（yt-dlp 引擎）保持一致的范式：Idle 为初始态，错误信息通过 [Error.message] 承载。
 */
sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data object UpToDate : AppUpdateState()
    data class UpdateAvailable(
        val version: String,
        val apkUrl: String,
        val releaseNotes: String,
        val htmlUrl: String,
    ) : AppUpdateState()
    data class Downloading(val progress: Int) : AppUpdateState()
    data class ReadyToInstall(val apkUri: String) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

/**
 * 应用版本更新 ViewModel。
 *
 * 在应用启动时自动检查（24 小时频率限制），用户可在设置页手动触发。
 * 发现新版本后可下载 APK 到 `cacheDir/updates/` 并调起系统安装器。
 */
class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val updateChecker = AppUpdateChecker()

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState

    private val _currentVersion = MutableStateFlow<String?>(null)
    val currentVersion: StateFlow<String?> = _currentVersion

    init {
        refreshCurrentVersion()
        checkForUpdates(force = false)
    }

    /** 读取当前已安装的应用版本号。 */
    private fun refreshCurrentVersion() {
        val versionName = runCatching {
            val packageInfo = getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
            packageInfo.versionName
        }.getOrNull()
        _currentVersion.value = versionName
    }

    /**
     * 检查 GitHub 最新发布。
     *
     * @param force true 时忽略 24 小时频率限制，用于用户手动触发。
     */
    fun checkForUpdates(force: Boolean) {
        if (_appUpdateState.value is AppUpdateState.Checking) return
        _appUpdateState.value = AppUpdateState.Checking

        viewModelScope.launch {
            try {
                // 非强制检查时，校验距上次检查是否已过 24 小时
                if (!force) {
                    val lastCheck = settingsDataStore.lastUpdateCheckTime.first()
                    if (lastCheck > 0 && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                        _appUpdateState.value = AppUpdateState.Idle
                        return@launch
                    }
                }

                val release = withContext(Dispatchers.IO) {
                    updateChecker.fetchLatestRelease()
                }

                if (release == null) {
                    _appUpdateState.value = AppUpdateState.Error(
                        getApplication<Application>().getString(R.string.settings_app_update_failed)
                    )
                    return@launch
                }

                settingsDataStore.setLastUpdateCheckTime(System.currentTimeMillis())

                val current = _currentVersion.value
                val hasNewVersion = current == null ||
                    updateChecker.compareVersions(release.versionName, current) > 0

                _appUpdateState.value = if (hasNewVersion) {
                    AppUpdateState.UpdateAvailable(
                        version = release.versionName,
                        apkUrl = release.apkUrl ?: "",
                        releaseNotes = release.releaseNotes,
                        htmlUrl = release.htmlUrl,
                    )
                } else {
                    AppUpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "应用更新检查失败", e)
                _appUpdateState.value = AppUpdateState.Error(detailedMessage(e))
            }
        }
    }

    /**
     * 下载新版本 APK 到缓存目录。仅在 [AppUpdateState.UpdateAvailable] 且含 APK 资源时可调。
     */
    fun downloadUpdate() {
        val state = _appUpdateState.value
        if (state !is AppUpdateState.UpdateAvailable) return
        if (state.apkUrl.isBlank()) {
            _appUpdateState.value = AppUpdateState.Error(
                getApplication<Application>().getString(R.string.settings_app_update_no_apk)
            )
            return
        }
        if (_appUpdateState.value is AppUpdateState.Downloading) return

        _appUpdateState.value = AppUpdateState.Downloading(0)
        viewModelScope.launch {
            try {
                val apkUri = withContext(Dispatchers.IO) {
                    downloadApk(state.apkUrl, state.version)
                }
                _appUpdateState.value = AppUpdateState.ReadyToInstall(apkUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "下载更新 APK 失败", e)
                _appUpdateState.value = AppUpdateState.Error(detailedMessage(e))
            }
        }
    }

    /**
     * 调起系统安装器。仅在 [AppUpdateState.ReadyToInstall] 时可调。
     */
    fun installUpdate() {
        val state = _appUpdateState.value
        if (state !is AppUpdateState.ReadyToInstall) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(state.apkUri), MIME_TYPE_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法启动安装器", e)
            _appUpdateState.value = AppUpdateState.Error(
                getApplication<Application>().getString(R.string.settings_app_update_install_failed)
            )
        }
    }

    /** 重置到 Idle 态，便于用户重新检查。 */
    fun dismissUpdate() {
        _appUpdateState.value = AppUpdateState.Idle
    }

    /**
     * 流式下载 APK 到 [updatesDir]，返回 FileProvider 可共享的 content URI。
     * 下载前清理旧版本 APK 避免占用缓存。
     */
    private suspend fun downloadApk(apkUrl: String, version: String): String =
        withContext(Dispatchers.IO) {
            val updatesDir = File(getApplication<Application>().cacheDir, UPDATES_DIR_NAME)
            if (!updatesDir.exists()) updatesDir.mkdirs()
            // 清理旧 APK
            updatesDir.listFiles()?.forEach { it.delete() }
            val apkFile = File(updatesDir, "VideoDownloader_${version}.apk")

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", NetworkConstants.USER_AGENT)
                .build()
            NetworkClients.standard.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("响应体为空")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        var lastReportedPercent = -1
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    _appUpdateState.value = AppUpdateState.Downloading(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                apkFile,
            ).toString()
        }

    private fun detailedMessage(error: Throwable): String {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()
        return messages.joinToString("：").ifBlank { error.javaClass.simpleName }
    }

    private companion object {
        const val TAG = "AppUpdateViewModel"
        const val UPDATES_DIR_NAME = "updates"
        const val MIME_TYPE_APK = "application/vnd.android.package-archive"
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 小时
    }
}
