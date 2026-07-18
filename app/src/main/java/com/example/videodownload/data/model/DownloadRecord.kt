package com.example.videodownload.data.model

/** 可恢复的下载任务。 */
data class DownloadTask(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val state: DownloadState,
    val videoUrl: String,
    val webpageUrl: String,
    val fileName: String = "",
    val ext: String = "mp4",
    val directoryUri: String = "",
    val fileUri: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val workId: String = "",
    val startedAtMillis: Long = 0,
    val finishedAtMillis: Long = 0,
)

/** 已完成下载的历史记录。 */
data class DownloadHistoryItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val fileName: String,
    val fileUri: String,
    val videoUrl: String,
    val webpageUrl: String,
    val timestamp: Long,
    /** 扫描本地文件恢复历史时保存平台分类；旧记录为空时仍根据网页地址判断。 */
    val platform: String = "",
)
