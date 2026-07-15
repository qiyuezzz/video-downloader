package com.example.videodownload.util

/** 根据原始页面地址确定下载文件夹。 */
object VideoPlatform {
    fun folderName(url: String): String = when {
        url.contains("bilibili.com", ignoreCase = true) ||
            url.contains("b23.tv", ignoreCase = true) -> "Bilibili"
        url.contains("x.com", ignoreCase = true) ||
            url.contains("twitter.com", ignoreCase = true) -> "X"
        url.contains("instagram.com", ignoreCase = true) -> "Instagram"
        url.contains("youtube.com", ignoreCase = true) ||
            url.contains("youtu.be", ignoreCase = true) -> "YouTube"
        else -> "其他"
    }
}
