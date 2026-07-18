package com.example.videodownload.ui.settings

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.data.DownloadRecordCodec
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.parser.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * yt-dlp 更新状态
 */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Updating : UpdateState()
    data class Success(val version: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed class HistoryRestoreState {
    data object Idle : HistoryRestoreState()
    data object Scanning : HistoryRestoreState()
    data class Success(val restoredCount: Int) : HistoryRestoreState()
    data class Error(val message: String) : HistoryRestoreState()
}

/**
 * 设置页 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val saveLocation = settingsDataStore.saveLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val saveLocationName = settingsDataStore.saveLocation
        .map { uri ->
            if (uri == null) return@map null
            withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(getApplication(), uri.toUri())?.name
                    ?: uri.toUri().lastPathSegment
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferredQuality = settingsDataStore.preferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_BEST)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _historyRestoreState = MutableStateFlow<HistoryRestoreState>(HistoryRestoreState.Idle)
    val historyRestoreState: StateFlow<HistoryRestoreState> = _historyRestoreState

    /**
     * 更新保存目录
     */
    fun setSaveLocation(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setSaveLocation(uri)
            _historyRestoreState.value = HistoryRestoreState.Scanning
            _historyRestoreState.value = try {
                HistoryRestoreState.Success(restoreHistoryFromDirectory(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "扫描下载目录失败", e)
                HistoryRestoreState.Error(e.message ?: "无法读取所选目录")
            }
        }
    }

    private suspend fun restoreHistoryFromDirectory(uri: String): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(getApplication(), uri.toUri())
            ?: throw IOException("无法访问所选目录")
        if (!root.exists() || !root.isDirectory) throw IOException("所选目录不可用")

        val existing = settingsDataStore.downloadHistory.first()
            ?.let(DownloadRecordCodec::decodeHistory)
            .orEmpty()
        val existingUris = existing.mapTo(mutableSetOf(), DownloadHistoryItem::fileUri)
        val restored = mutableListOf<DownloadHistoryItem>()

        root.listFiles().forEach { child ->
            if (child.isDirectory) {
                val platform = child.name?.takeIf { it in PLATFORM_FOLDERS } ?: OTHER_PLATFORM
                child.listFiles().forEach { file ->
                    file.toHistoryItem(platform, existingUris)?.let(restored::add)
                }
            } else {
                child.toHistoryItem(OTHER_PLATFORM, existingUris)?.let(restored::add)
            }
        }

        val merged = (existing + restored)
            .distinctBy(DownloadHistoryItem::fileUri)
            .sortedByDescending(DownloadHistoryItem::timestamp)
        settingsDataStore.saveDownloadHistory(DownloadRecordCodec.encodeHistory(merged))
        restored.size
    }

    private fun DocumentFile.toHistoryItem(
        platform: String,
        knownUris: MutableSet<String>,
    ): DownloadHistoryItem? {
        if (!isFile || !isVideoFile() || length() <= 0) return null
        val uriString = uri.toString()
        if (!knownUris.add(uriString)) return null
        val name = name?.takeIf(String::isNotBlank) ?: return null
        return DownloadHistoryItem(
            id = UUID.randomUUID().toString(),
            title = name.substringBeforeLast('.').ifBlank { name },
            thumbnailUrl = null,
            fileName = name,
            fileUri = uriString,
            videoUrl = "",
            webpageUrl = "",
            timestamp = lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
            platform = platform,
        )
    }

    private fun DocumentFile.isVideoFile(): Boolean {
        return isRecoverableVideoFile(name, type)
    }

    /**
     * 更新首选画质
     */
    fun setPreferredQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setPreferredQuality(quality)
        }
    }

    /**
     * 手动更新 yt-dlp
     */
    fun updateYoutubeDl() {
        if (_updateState.value is UpdateState.Updating) return
        _updateState.value = UpdateState.Updating

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    YtDlpEngine.ensureInitialized(getApplication())
                    YoutubeDL.getInstance().updateYoutubeDL(
                        getApplication(),
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }
                Log.d("SettingsViewModel", "yt-dlp updated, status: $result")
                _updateState.value = UpdateState.Success("更新成功 (状态: $result)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "yt-dlp update failed", e)
                _updateState.value = UpdateState.Error(detailedMessage(e))
            }
        }
    }

    private fun detailedMessage(error: Throwable): String {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()
        return messages.joinToString("：").ifBlank { error.javaClass.simpleName }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val OTHER_PLATFORM = "其他"
        val PLATFORM_FOLDERS = setOf("Bilibili", "X", "Instagram", "YouTube", OTHER_PLATFORM)
    }

}

internal fun isRecoverableVideoFile(name: String?, mimeType: String?): Boolean {
    if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
    val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return extension in RECOVERABLE_VIDEO_EXTENSIONS
}

private val RECOVERABLE_VIDEO_EXTENSIONS =
    setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "flv", "ts")
