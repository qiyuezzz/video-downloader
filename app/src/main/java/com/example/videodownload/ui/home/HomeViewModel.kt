package com.example.videodownload.ui.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.downloader.VideoDownloader
import com.example.videodownload.parser.TwitterApiParser
import com.example.videodownload.parser.YtDlpParser
import com.example.videodownload.util.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * 解析状态
 */
sealed class ParseState {
    data object Idle : ParseState()
    data object Loading : ParseState()
    data class Success(val videoInfo: VideoInfo) : ParseState()
    data class Error(val message: String) : ParseState()
}

/**
 * 一次性 UI 事件
 */
sealed class HomeEvent {
    data object ShowDownloadOptions : HomeEvent()
    data class ShowDuplicateConfirm(val videoInfo: VideoInfo, val formats: List<VideoFormat>) : HomeEvent()
}

data class DownloadTask(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val state: DownloadState,
    val videoUrl: String, // 视频文件URL
    val webpageUrl: String // 原始帖子链接，用于跨平台重复检测
)

data class DownloadHistoryItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val fileName: String,
    val fileUri: String,
    val videoUrl: String, // 视频文件URL
    val webpageUrl: String, // 原始帖子链接
    val timestamp: Long
)

/**
 * 主页 ViewModel
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = VideoDownloader(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val ytParser = YtDlpParser()
    private val twitterApiParser = TwitterApiParser()

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
                    _history.value = parseHistoryJson(json)
                }
            }
        }
    }

    private fun parseHistoryJson(json: String): List<DownloadHistoryItem> {
        val list = mutableListOf<DownloadHistoryItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(DownloadHistoryItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    thumbnailUrl = obj.optString("thumbnailUrl", "").ifEmpty { null },
                    fileName = obj.getString("fileName"),
                    fileUri = obj.getString("fileUri"),
                    videoUrl = obj.optString("videoUrl", ""),
                    webpageUrl = obj.optString("webpageUrl", ""),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private suspend fun saveHistory() {
        val array = JSONArray()
        _history.value.take(50).forEach { item -> // 只保留最近 50 条
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL)
                put("fileName", item.fileName)
                put("fileUri", item.fileUri)
                put("videoUrl", item.videoUrl)
                put("webpageUrl", item.webpageUrl)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        settingsDataStore.saveDownloadHistory(array.toString())
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
                        val uri = Uri.parse(item.fileUri)
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
        if (extractUrl(text) != null) {
            parseUrl(text)
        }
    }

    /**
     * 从剪贴板读取链接
     */
    fun readClipboard() {
        val text = readClipboardText()
        val extracted = if (text != null) extractUrl(text) else null
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
        val rawUrl = extractUrl(rawInput)
        if (rawUrl == null) {
            _parseState.value = ParseState.Error("未能从输入中识别到有效的链接")
            return
        }

        _parseState.value = ParseState.Loading

        viewModelScope.launch {
            try {
                val url = cleanUrl(rawUrl)
                // 1. 如果是 Twitter/X 链接，优先尝试专门的 API
                if (isXUrl(url)) {
                    val twitterInfo = twitterApiParser.parse(url)
                    if (twitterInfo != null && twitterInfo.formats.isNotEmpty()) {
                        _parseState.value = ParseState.Success(twitterInfo)
                        _uiEvent.emit(HomeEvent.ShowDownloadOptions)
                        return@launch
                    }
                }

                // 2. 使用 yt-dlp 解析 (通用或作为回退)
                val videoInfo = ytParser.parse(url)
                if (videoInfo != null && videoInfo.formats.isNotEmpty()) {
                    _parseState.value = ParseState.Success(videoInfo)
                    _uiEvent.emit(HomeEvent.ShowDownloadOptions)
                } else {
                    _parseState.value = ParseState.Error("解析失败，未找到可用的视频格式")
                }
            } catch (e: Exception) {
                _parseState.value = ParseState.Error("解析出错: ${e.message ?: "未知错误"}")
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
                    webpageUrl = videoInfo.webpageUrl
                )
                
                _downloadTasks.update { current -> listOf(newTask) + current }

                launch {
                    downloader.downloadFlow(
                        videoUrl = format.url,
                        fileName = fileName,
                        ext = format.ext,
                        directoryUri = Uri.parse(saveUri),
                        referer = videoInfo.webpageUrl
                    ).collect { state ->
                        _downloadTasks.update { current ->
                            current.map { task ->
                                if (task.id == taskId) {
                                    val updatedTask = task.copy(state = state)
                                    if (state is DownloadState.Success) {
                                        addToHistory(updatedTask, state)
                                    }
                                    updatedTask
                                } else task
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 重置状态
     */
    fun resetParseState() {
        _parseState.value = ParseState.Idle
        _clipboardUrl.value = null
    }

    fun removeDownloadTask(taskId: String) {
        _downloadTasks.value = _downloadTasks.value.filter { it.id != taskId }
    }

    /**
     * 简单的链接检测并提取 - 适配带标题的分享文案 (如 B站)
     */
    private fun extractUrl(text: String): String? {
        val pattern = Pattern.compile("""https?://[\w\-_]+(\.[\w\-_]+)+[\w\-\.,@?^=%&:/~+#]*""")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun isXUrl(url: String): Boolean {
        return url.contains("x.com") || url.contains("twitter.com")
    }

    companion object {
        /** B站视频/Bangumi ID 提取正则 */
        private val BILI_ID_REGEX = Regex(
            """bilibili\.com/(?:video/|bangumi/play/)(BV[\w]+|av\d+|ep\d+|ss\d+)""",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * 链接清洗：去除追踪参数、统一格式，提高 yt-dlp 识别率
     */
    private suspend fun cleanUrl(url: String): String {
        var result = url.trim()

        // B站 b23.tv 短链接先解析
        if (result.contains("b23.tv")) {
            val resolved = resolveB23Url(result)
            if (resolved != null) return resolved
        }

        // B站: 提取 BV/av/ep/ss 号，构造干净的 www 链接
        val biliMatch = BILI_ID_REGEX.find(result)
        if (biliMatch != null) {
            val id = biliMatch.groupValues[1]
            return "${NetworkConstants.BILIBILI_BASE_URL}/video/$id"
        }

        // B站兜底: 去除查询参数，统一子域名
        if (result.contains("bilibili.com")) {
            return result.substringBefore("?").replace("m.bilibili.com", "www.bilibili.com")
        }

        // Instagram/X: 去除 ? 后的追踪参数
        if (result.contains("instagram.com") || isXUrl(result)) {
            return result.substringBefore("?")
        }

        return result
    }

    /** 解析 b23.tv 短链接，返回干净的长链接 */
    private suspend fun resolveB23Url(shortUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(false)
                .build()
            val request = okhttp3.Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            val location = response.header("Location") ?: return@withContext null
            val match = BILI_ID_REGEX.find(location) ?: return@withContext null
            "${NetworkConstants.BILIBILI_BASE_URL}/video/${match.groupValues[1]}"
        } catch (e: Exception) {
            null
        }
    }
}
