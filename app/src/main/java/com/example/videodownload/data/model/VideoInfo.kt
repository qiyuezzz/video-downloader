package com.example.videodownload.data.model

/**
 * 视频下载状态
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Progress(val percent: Int, val fileUri: String? = null) : DownloadState()
    data class Success(val fileName: String, val fileUri: String? = null) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data object Interrupted : DownloadState()
}

/**
 * 视频画质格式信息
 */
data class VideoFormat(
    val formatId: String,
    val quality: String,
    val ext: String,
    val filesize: Long?,
    val url: String,
    val height: Int = 0,
    val thumbnailUrl: String? = null,
) {
    /** 显示用标签，如 "720p (mp4)" */
    val displayLabel: String get() = "$quality ($ext)"
}

/**
 * 视频解析结果
 */
data class VideoInfo(
    val title: String,
    val thumbnailUrl: String?,
    val formats: List<VideoFormat>,
    val webpageUrl: String,
)
