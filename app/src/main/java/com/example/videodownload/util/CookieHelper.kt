package com.example.videodownload.util

import android.webkit.CookieManager

/**
 * Cookie 收集工具，用于从 WebView CookieManager 中收集视频播放/下载所需的认证 Cookie。
 * X/Twitter 的 CDN 认证 Cookie 可能存储在 x.com 域名下而非 video.twimg.com。
 */
object CookieHelper {

    fun collectCookies(videoUrl: String): String {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(
            videoUrl,
            "https://x.com/",
            "https://twitter.com/",
            "https://video.twimg.com/"
        )
        return domains
            .mapNotNull { cookieManager.getCookie(it) }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("; ")
    }
}
