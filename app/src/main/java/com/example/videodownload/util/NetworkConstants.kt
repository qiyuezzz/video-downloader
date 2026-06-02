package com.example.videodownload.util

/**
 * 网络相关常量
 */
object NetworkConstants {
    /** 移动端 UA，适用于大多数视频网站 */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    /** 桌面端 UA，适用于需要桌面版页面的网站（如 B站） */
    const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** B站主站地址，用作 Referer */
    const val BILIBILI_BASE_URL = "https://www.bilibili.com"
}
