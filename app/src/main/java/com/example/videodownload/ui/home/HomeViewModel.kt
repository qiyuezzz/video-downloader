package com.example.videodownload.ui.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.data.DownloadRecordCodec
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.DownloadTask
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.downloader.VideoDownloader
import com.example.videodownload.parser.BilibiliNativeParser
import com.example.videodownload.parser.ParseCoordinator
import com.example.videodownload.parser.TwitterApiParser
import com.example.videodownload.parser.YtDlpParser
import com.example.videodownload.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 主页 ViewModel
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = VideoDownloader(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val parseCoordinator = ParseCoordinator(
        specializedParsers = listOf(TwitterApiParser(), BilibiliNativeParser()),
        fallbackParser = YtDlpParser(),
    )
    private val urlNormalizer = UrlNormalizer()

    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState: StateFlow<ParseState> = _parseState

    private val _uiEvent = MutableSharedFlow<HomeEvent>()
    val uiEvent: SharedFlow<HomeEvent> = _uiEvent

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks

    private val _history = MutableStateFlow<List<DownloadHistoryItem>>(emptyList())
    val history: StateFlow<List<DownloadHistoryItem>> = _history

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl

    init {
        // 加载历史记录
        viewModelScope.launch {
            settingsDataStore.downloadHistory.collect { json ->
                if (json != null) {
                    _history.value = DownloadRecordCodec.decodeHistory(json)
                }
            }
        }

        // 恢复中断的下载任务
        viewModelScope.launch {
            restoreActiveDownloads()
        }
    }

    // ==================== 活跃任务持久化 ====================

    /**
     * 恢复上次退出时未完成的下载任务
     */
    private suspend fun restoreActiveDownloads() {
        val json = settingsDataStore.activeDownloads.first() ?: return
        val tasks = DownloadRecordCodec.decodeActiveTasks(json)
        if (tasks.isEmpty()) return

        val restored = mutableListOf<DownloadTask>()
        for (task in tasks) {
            // 检查文件是否仍然存在
            if (task.fileUri.isNotEmpty()) {
                try {
                    val fileUri = task.fileUri.toUri()
                    val file = DocumentFile.fromSingleUri(getApplication(), fileUri)
                    if (file != null && file.exists() && file.length() > 0) {
                        restored.add(task.copy(state = DownloadState.Interrupted))
                    }
                } catch (_: Exception) {
                    // 文件不可访问，跳过
                }
            }
        }

        if (restored.isNotEmpty()) {
            _downloadTasks.value = restored
        }

        // 清理已不存在的任务
        if (restored.size != tasks.size) {
            saveActiveDownloads()
        }
    }

    /**
     * 将活跃下载任务序列化为 JSON 并持久化
     */
    private suspend fun saveActiveDownloads() {
        settingsDataStore.saveActiveDownloads(
            DownloadRecordCodec.encodeActiveTasks(_downloadTasks.value)
        )
    }

    // ==================== 历史记录 ====================

    private suspend fun saveHistory() {
        settingsDataStore.saveDownloadHistory(DownloadRecordCodec.encodeHistory(_history.value))
    }

    private fun addToHistory(task: DownloadTask, successState: DownloadState.Success) {
        val newItem = DownloadHistoryItem(
            id = UUID.randomUUID().toString(),
            title = task.title,
            thumbnailUrl = task.thumbnailUrl,
            fileName = successState.fileName,
            fileUri = successState.fileUri ?: "",
            videoUrl = task.videoUrl,
            webpageUrl = task.webpageUrl,
            timestamp = System.currentTimeMillis()
        )
        _history.update { current -> listOf(newItem) + current }
        viewModelScope.launch { saveHistory() }
    }

    fun removeHistoryItem(itemId: String, deleteFile: Boolean = false) {
        removeHistoryItems(listOf(itemId), deleteFile)
    }

    fun removeHistoryItems(itemIds: List<String>, deleteFile: Boolean = false) {
        val itemsToRemove = _history.value.filter { it.id in itemIds }

        if (deleteFile) {
            viewModelScope.launch(Dispatchers.IO) {
                itemsToRemove.forEach { item ->
                    try {
                        val uri = item.fileUri.toUri()
                        androidx.documentfile.provider.DocumentFile.fromSingleUri(getApplication(), uri)?.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        _history.update { current -> current.filter { it.id !in itemIds } }
        viewModelScope.launch { saveHistory() }
    }


    /**
     * 从剪贴板读取纯文本
     */
    private fun readClipboardText(): String? {
        val clipboard =
            getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString()
        } else null
    }

    /**
     * 一键粘贴并解析
     */
    fun pasteAndParse() {
        val text = readClipboardText() ?: return
        if (urlNormalizer.extract(text) != null) {
            parseUrl(text)
        }
    }

    /**
     * 从剪贴板读取链接
     */
    fun readClipboard() {
        val text = readClipboardText()
        val extracted = if (text != null) urlNormalizer.extract(text) else null
        if (extracted != null) {
            _clipboardUrl.value = extracted
        } else {
            _clipboardUrl.value = null
        }
    }

    /**
     * 解析链接 - 优先使用专用 API (应对推特限制级/多视频)，然后回退到 yt-dlp
     */
    fun parseUrl(rawInput: String) {
        val rawUrl = urlNormalizer.extract(rawInput)
        if (rawUrl == null) {
            _parseState.value = ParseState.Error("未能从输入中识别到有效的链接")
            return
        }

        _parseState.value = ParseState.Loading

        viewModelScope.launch {
            try {
                val url = urlNormalizer.normalize(rawUrl)
                val videoInfo = parseCoordinator.parse(url)
                if (videoInfo != null) {
                    _parseState.value = ParseState.Success(videoInfo)
                    _uiEvent.emit(HomeEvent.ShowDownloadOptions)
                } else {
                    _parseState.value = ParseState.Error("解析失败，未找到可用的视频格式")
                }
            } catch (e: Exception) {
                val message = e.message ?: ""
                val friendlyMessage = when {
                    message.contains("412") -> "B站解析受限 (412)，请尝试重试或切换网络"
                    message.contains("403") -> "访问被拒绝 (403)，请检查链接或稍后再试"
                    message.contains("Incomplete YouTube ID") -> "链接格式不正确"
                    else -> "解析出错: ${e.message ?: "未知错误"}"
                }
                _parseState.value = ParseState.Error(friendlyMessage)
            }
        }
    }

    /**
     * 批量下载视频 - 增加精准重复检测
     */
    fun downloadVideos(videoInfo: VideoInfo, formats: List<VideoFormat>, force: Boolean = false) {
        if (!force) {
            // 精准检测：检查视频文件URL或原始帖子链接是否已存在
            val selectedUrls = formats.map { it.url }.toSet()
            val webpageUrl = videoInfo.webpageUrl
            val alreadyDownloaded = _history.value.any { historyItem ->
                historyItem.videoUrl in selectedUrls || // 视频文件URL匹配
                (webpageUrl.isNotEmpty() && historyItem.webpageUrl == webpageUrl) // 原始帖子链接匹配
            }

            if (alreadyDownloaded) {
                viewModelScope.launch {
                    _uiEvent.emit(HomeEvent.ShowDuplicateConfirm(videoInfo, formats))
                }
                return
            }
        }
        executeDownload(videoInfo, formats)
    }

    private fun executeDownload(videoInfo: VideoInfo, formats: List<VideoFormat>) {
        viewModelScope.launch {
            val saveUri = settingsDataStore.saveLocation.first()
            if (saveUri == null) {
                _parseState.value = ParseState.Error("请先在设置中选择保存目录")
                return@launch
            }

            for ((index, format) in formats.withIndex()) {
                val suffix = if (formats.size > 1) "_${index + 1}" else ""
                val fileName = "${videoInfo.title}$suffix"
                val taskId = UUID.randomUUID().toString()

                val newTask = DownloadTask(
                    id = taskId,
                    title = fileName,
                    thumbnailUrl = format.thumbnailUrl ?: videoInfo.thumbnailUrl,
                    state = DownloadState.Idle,
                    videoUrl = format.url,
                    webpageUrl = videoInfo.webpageUrl,
                    fileName = fileName,
                    ext = format.ext,
                    directoryUri = saveUri,
                )

                _downloadTasks.update { current -> listOf(newTask) + current }
                // 持久化新任务
                saveActiveDownloads()

                launch {
                    downloader.downloadFlow(
                        videoUrl = format.url,
                        fileName = fileName,
                        ext = format.ext,
                        directoryUri = saveUri.toUri(),
                        referer = videoInfo.webpageUrl
                    ).collect { state ->
                        _downloadTasks.update { current ->
                            current.map { task ->
                                if (task.id == taskId) {
                                    val updatedTask = task.copy(
                                        state = state,
                                        // 从 Progress/Success 中捕获文件 URI
                                        fileUri = when (state) {
                                            is DownloadState.Progress -> state.fileUri ?: task.fileUri
                                            is DownloadState.Success -> state.fileUri ?: task.fileUri
                                            else -> task.fileUri
                                        }
                                    )
                                    if (state is DownloadState.Success) {
                                        addToHistory(updatedTask, state)
                                    }
                                    updatedTask
                                } else task
                            }
                        }
                        // 每次状态变更时持久化（包含文件 URI 等信息）
                        saveActiveDownloads()
                    }
                }
            }
        }
    }

    /**
     * 恢复所有中断/失败的下载
     */
    fun resumeAllDownloads() {
        val tasksToResume = _downloadTasks.value.filter {
            it.state is DownloadState.Interrupted || it.state is DownloadState.Error
        }
        tasksToResume.forEach { resumeDownload(it.id) }
    }

    /**
     * 恢复中断的下载
     */
    fun resumeDownload(taskId: String) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return
        if (task.state !is DownloadState.Interrupted && task.state !is DownloadState.Error) return

        viewModelScope.launch {
            val directoryUri = task.directoryUri.ifEmpty {
                settingsDataStore.saveLocation.first() ?: return@launch
            }

            // 获取已下载的文件大小
            val existingFileUri = if (task.fileUri.isNotEmpty()) task.fileUri.toUri() else null
            var downloadedBytes = 0L
            if (existingFileUri != null) {
                try {
                    val file = DocumentFile.fromSingleUri(getApplication(), existingFileUri)
                    if (file != null && file.exists()) {
                        downloadedBytes = file.length()
                    }
                } catch (_: Exception) {}
            }

            // 更新状态为下载中
            _downloadTasks.update { current ->
                current.map { if (it.id == taskId) it.copy(state = DownloadState.Progress(((downloadedBytes * 100) / (if (task.totalBytes > 0) task.totalBytes else 1)).toInt().coerceAtMost(99))) else it }
            }

            launch {
                downloader.downloadFlow(
                    videoUrl = task.videoUrl,
                    fileName = task.fileName,
                    ext = task.ext,
                    directoryUri = directoryUri.toUri(),
                    referer = task.webpageUrl,
                    existingFileUri = existingFileUri,
                    alreadyDownloadedBytes = downloadedBytes,
                ).collect { state ->
                    _downloadTasks.update { current ->
                        current.map { t ->
                            if (t.id == taskId) {
                                val updated = t.copy(
                                    state = state,
                                    fileUri = if (state is DownloadState.Success) (state.fileUri ?: t.fileUri) else t.fileUri,
                                    directoryUri = directoryUri,
                                )
                                if (state is DownloadState.Success) {
                                    addToHistory(updated, state)
                                }
                                updated
                            } else t
                        }
                    }
                    saveActiveDownloads()
                }
            }
        }
    }

    /**
     * 删除未完成的下载任务及可选清理文件
     */
    fun removeIncompleteTask(taskId: String, deleteFile: Boolean = false) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return

        if (deleteFile && task.fileUri.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val uri = task.fileUri.toUri()
                    DocumentFile.fromSingleUri(getApplication(), uri)?.delete()
                } catch (_: Exception) {}
            }
        }

        _downloadTasks.update { current -> current.filter { it.id != taskId } }
        viewModelScope.launch { saveActiveDownloads() }
    }

    /**
     * 重置状态
     */
    fun resetParseState() {
        _parseState.value = ParseState.Idle
        _clipboardUrl.value = null
    }

    fun removeDownloadTask(taskId: String) {
        _downloadTasks.update { current -> current.filter { it.id != taskId } }
        viewModelScope.launch { saveActiveDownloads() }
    }

}
