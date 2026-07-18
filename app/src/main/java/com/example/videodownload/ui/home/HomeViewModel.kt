package com.example.videodownload.ui.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.videodownload.data.DownloadRecordCodec
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.DownloadTask
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.downloader.VideoDownloadWorker
import com.example.videodownload.downloader.DownloadProgress
import com.example.videodownload.parser.BilibiliNativeParser
import com.example.videodownload.parser.InstagramAnonymousParser
import com.example.videodownload.parser.ParseCoordinator
import com.example.videodownload.parser.TwitterApiParser
import com.example.videodownload.parser.YtDlpParser
import com.example.videodownload.util.UrlNormalizer
import com.example.videodownload.util.VideoPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 主页 ViewModel
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val workObservers = mutableMapOf<String, Job>()
    private val settingsDataStore = SettingsDataStore(application)
    private val parseCoordinator = ParseCoordinator(
        specializedParsers = listOf(
            TwitterApiParser(),
            BilibiliNativeParser(),
            InstagramAnonymousParser(),
        ),
        fallbackParser = YtDlpParser(application),
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

    val historyLayout: StateFlow<Int> = settingsDataStore.historyLayout.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsDataStore.HISTORY_LAYOUT_LIST,
    )

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl

    init {
        viewModelScope.launch {
            settingsDataStore.downloadHistory.collectLatest { json ->
                _history.value = json?.let(DownloadRecordCodec::decodeHistory).orEmpty()
            }
        }
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

        _downloadTasks.value = tasks.map { it.copy(state = DownloadState.Interrupted) }
        tasks.forEach { task ->
            if (task.workId.isNotEmpty()) {
                observeWork(task.id, task.workId)
            }
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
        val completedUri = successState.fileUri.orEmpty()
        if (completedUri.isNotEmpty() && _history.value.any { it.fileUri == completedUri }) return
        val newItem = DownloadHistoryItem(
            id = UUID.randomUUID().toString(),
            title = task.title,
            thumbnailUrl = task.thumbnailUrl,
            fileName = successState.fileName,
            fileUri = successState.fileUri ?: "",
            videoUrl = task.videoUrl,
            webpageUrl = task.webpageUrl,
            timestamp = System.currentTimeMillis(),
            platform = VideoPlatform.folderName(task.webpageUrl),
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
                        Log.w(TAG, "删除历史文件失败", e)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: ""
                val friendlyMessage = when {
                    message.contains("412") -> "B站解析受限 (412)，请尝试重试或切换网络"
                    message.contains("403") -> "访问被拒绝 (403)，请检查链接或稍后再试"
                    message.contains("Incomplete YouTube ID") -> "链接格式不正确"
                    message.contains("No video could be found in this tweet", ignoreCase = true) ->
                        "该推文未返回可下载视频，可能是引用推文、受限内容或链接已失效"
                    message.contains("Instagram sent an empty media response", ignoreCase = true) ->
                        "Instagram 未返回公开视频，请确认该内容无需登录即可访问"
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
                    totalBytes = format.filesize ?: 0,
                )

                _downloadTasks.update { current -> listOf(newTask) + current }
                enqueueDownload(newTask)
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
                } catch (e: Exception) {
                    Log.w(TAG, "读取断点文件大小失败", e)
                }
            }

            val resumableTask = task.copy(
                directoryUri = directoryUri,
                state = DownloadState.Progress(
                    percent = if (task.totalBytes > 0) {
                        DownloadProgress.percent(downloadedBytes, task.totalBytes)
                    } else {
                        -1
                    },
                    fileUri = task.fileUri.ifEmpty { null },
                    downloadedBytes = downloadedBytes,
                    totalBytes = task.totalBytes,
                ),
            )
            _downloadTasks.update { current ->
                current.map { if (it.id == taskId) resumableTask else it }
            }
            enqueueDownload(resumableTask, downloadedBytes)
        }
    }

    fun setHistoryLayout(layout: Int) {
        viewModelScope.launch {
            settingsDataStore.setHistoryLayout(layout)
        }
    }

    private suspend fun enqueueDownload(task: DownloadTask, downloadedBytes: Long = 0) {
        val input = VideoDownloadWorker.DownloadInput(
            videoUrl = task.videoUrl,
            fileName = task.fileName,
            ext = task.ext,
            directoryUri = task.directoryUri,
            platformFolder = VideoPlatform.folderName(task.webpageUrl),
            referer = task.webpageUrl,
            existingFileUri = task.fileUri.takeIf { it.isNotEmpty() && downloadedBytes > 0 },
            downloadedBytes = downloadedBytes,
            totalBytes = task.totalBytes,
        )
        val request = try {
            OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(input.toData())
                .addTag("video-download-${task.id}")
                .build()
        } catch (e: IllegalStateException) {
            _downloadTasks.update { current ->
                current.map {
                    if (it.id == task.id) it.copy(state = DownloadState.Error("下载参数过大，无法创建后台任务"))
                    else it
                }
            }
            saveActiveDownloads()
            Log.e(TAG, "创建后台下载任务失败", e)
            return
        }
        val workId = request.id.toString()
        _downloadTasks.update { current ->
            current.map { if (it.id == task.id) it.copy(workId = workId) else it }
        }
        saveActiveDownloads()
        try {
            workManager.enqueue(request)
        } catch (e: IllegalStateException) {
            _downloadTasks.update { current ->
                current.map {
                    if (it.id == task.id) it.copy(state = DownloadState.Error("无法提交后台下载任务"))
                    else it
                }
            }
            saveActiveDownloads()
            Log.e(TAG, "提交后台下载任务失败", e)
            return
        }
        observeWork(task.id, workId)
    }

    private fun observeWork(taskId: String, workId: String) {
        workObservers.remove(taskId)?.cancel()
        val id = runCatching { UUID.fromString(workId) }.getOrNull() ?: return
        workObservers[taskId] = viewModelScope.launch {
            try {
                var lastSnapshot: String? = null
                while (true) {
                    val info = withContext(Dispatchers.IO) {
                        workManager.getWorkInfoById(id).get()
                    } ?: break
                    val snapshot = workSnapshot(info)
                    if (snapshot != lastSnapshot) {
                        lastSnapshot = snapshot
                        applyWorkInfo(taskId, info)
                    }
                    if (info.state.isFinished) break
                    delay(WORK_STATUS_POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "读取后台下载状态失败", e)
                _downloadTasks.update { current ->
                    current.map {
                        if (it.id == taskId) it.copy(state = DownloadState.Error("无法读取后台下载状态"))
                        else it
                    }
                }
                saveActiveDownloads()
            } finally {
                if (workObservers[taskId] === coroutineContext[Job]) {
                    workObservers.remove(taskId)
                }
            }
        }
    }

    private fun workSnapshot(info: WorkInfo): String = buildString {
        append(info.state.name)
        append('|').append(info.progress.getInt(VideoDownloadWorker.KEY_PERCENT, -1))
        append('|').append(info.progress.getString(VideoDownloadWorker.KEY_FILE_URI).orEmpty())
        append('|').append(info.progress.getLong(VideoDownloadWorker.KEY_DOWNLOADED_BYTES, 0))
        append('|').append(info.progress.getLong(VideoDownloadWorker.KEY_TOTAL_BYTES, 0))
        append('|').append(info.outputData.getString(VideoDownloadWorker.KEY_FILE_URI).orEmpty())
        append('|').append(info.outputData.getString(VideoDownloadWorker.KEY_ERROR).orEmpty())
    }

    private suspend fun applyWorkInfo(taskId: String, info: WorkInfo) {
        val task = _downloadTasks.value.firstOrNull { it.id == taskId } ?: return
        val updated = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> task.copy(state = DownloadState.Idle)
            WorkInfo.State.RUNNING -> {
                val fileUri = info.progress.getString(VideoDownloadWorker.KEY_FILE_URI)
                    .orEmpty().ifEmpty { task.fileUri }
                val downloadedBytes = info.progress.getLong(VideoDownloadWorker.KEY_DOWNLOADED_BYTES, 0)
                val totalBytes = info.progress.getLong(VideoDownloadWorker.KEY_TOTAL_BYTES, task.totalBytes)
                task.copy(
                    state = DownloadState.Progress(
                        info.progress.getInt(VideoDownloadWorker.KEY_PERCENT, -1),
                        fileUri.ifEmpty { null },
                        downloadedBytes,
                        totalBytes,
                    ),
                    fileUri = fileUri,
                    totalBytes = totalBytes,
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                val success = DownloadState.Success(
                    info.outputData.getString(VideoDownloadWorker.KEY_FILE_NAME) ?: task.fileName,
                    info.outputData.getString(VideoDownloadWorker.KEY_FILE_URI),
                )
                val finished = task.copy(
                    state = success,
                    fileUri = success.fileUri ?: task.fileUri,
                )
                if (task.state !is DownloadState.Success) addToHistory(finished, success)
                finished
            }
            WorkInfo.State.FAILED -> task.copy(
                state = DownloadState.Error(
                    info.outputData.getString(VideoDownloadWorker.KEY_ERROR) ?: "下载失败"
                )
            )
            WorkInfo.State.CANCELLED -> task.copy(state = DownloadState.Interrupted)
        }
        _downloadTasks.update { current -> current.map { if (it.id == taskId) updated else it } }
        saveActiveDownloads()
    }

    /**
     * 删除未完成的下载任务及可选清理文件
     */
    fun removeIncompleteTask(taskId: String, deleteFile: Boolean = false) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return

        val cancellation = task.workId.takeIf(String::isNotEmpty)?.let { workId ->
            runCatching { UUID.fromString(workId) }.getOrNull()?.let(workManager::cancelWorkById)
        }
        workObservers.remove(taskId)?.cancel()

        if (deleteFile && task.fileUri.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    cancellation?.result?.get()
                    val uri = task.fileUri.toUri()
                    DocumentFile.fromSingleUri(getApplication(), uri)?.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "删除未完成文件失败", e)
                }
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
        removeIncompleteTask(taskId)
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val WORK_STATUS_POLL_INTERVAL_MS = 500L
    }

}
