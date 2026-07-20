package com.example.videodownload.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * 应用版本更新检查器。
 *
 * 调用 GitHub Releases API 拉取最新发布信息，并用语义化分段比较版本号。
 * 为便于 JVM 单测，HTTP 请求与 JSON 解析通过可注入的函数接口暴露。
 */
class AppUpdateChecker(
    private val client: OkHttpClient = NetworkClients.standard,
    private val fetcher: (suspend (url: String, client: OkHttpClient) -> String)? = null,
) {

    /**
     * 最新发布信息。`apkUrl` 为 null 表示该版本未附带 APK 资源。
     */
    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val apkUrl: String?,
        val releaseNotes: String,
        val htmlUrl: String,
    )

    /**
     * 拉取仓库最新发布。请求失败、JSON 解析失败或版本号缺失时返回 null。
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetcher?.invoke(API_URL, client) ?: executeRequest(API_URL, client)
            parseRelease(raw)
        }.onFailure { e ->
            if (e is CancellationException) throw e
        }.getOrNull()
    }

    /**
     * 比较两个版本号字符串。
     *
     * 支持带或不带 `v` 前缀，按 `.` 分段转 Int 逐段比较；非数字段按 0 处理。
     * 返回：负数表示 [a] 旧于 [b]，0 表示相同，正数表示 [a] 新于 [b]。
     */
    fun compareVersions(a: String, b: String): Int {
        val partsA = normalizeVersion(a)
        val partsB = normalizeVersion(b)
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    private fun normalizeVersion(version: String): List<Int> {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        return trimmed.split(".")
            .map { it.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
    }

    private fun executeRequest(url: String, client: OkHttpClient): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API HTTP ${response.code}")
            }
            return response.body?.string()
                ?: throw IOException("GitHub API 响应体为空")
        }
    }

    private fun parseRelease(raw: String): ReleaseInfo? {
        val json = JSONObject(raw)
        // 跳过草稿与预发布：latest 接口本身不应返回，但仍做防御性过滤
        if (json.optBoolean("draft", false)) return null
        if (json.optBoolean("prerelease", false)) return null
        val tagName = json.optString("tag_name").takeIf(String::isNotBlank) ?: return null
        val htmlUrl = json.optString("html_url").takeIf(String::isNotBlank) ?: return null
        val releaseNotes = json.optString("body").orEmpty()
        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).mapNotNull { i ->
                val asset = assets.optJSONObject(i) ?: return@mapNotNull null
                val url = asset.optString("browser_download_url")
                val name = asset.optString("name")
                if (url.isNotBlank() && name.lowercase().endsWith(".apk")) url else null
            }.firstOrNull()
        }
        return ReleaseInfo(
            tagName = tagName,
            versionName = tagName.trim().removePrefix("v").removePrefix("V"),
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            htmlUrl = htmlUrl,
        )
    }

    companion object {
        private const val REPO_OWNER = "qiyuezzz"
        private const val REPO_NAME = "video-downloader"
        private const val API_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val USER_AGENT = "VideoDownloader-Android"
    }
}
