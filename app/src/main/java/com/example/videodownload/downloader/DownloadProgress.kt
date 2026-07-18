package com.example.videodownload.downloader

/** 下载进度计算，未知总大小时返回 -1 表示不确定进度。 */
object DownloadProgress {
    fun percent(downloadedBytes: Long, totalBytes: Long, completed: Boolean = false): Int {
        if (totalBytes <= 0) return if (completed) 100 else -1
        val upperBound = if (completed) 100 else 99
        return ((downloadedBytes.coerceAtLeast(0) * 100) / totalBytes)
            .toInt()
            .coerceIn(0, upperBound)
    }
}
