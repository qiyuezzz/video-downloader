package com.example.videodownload.parser

import android.util.Log
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.util.NetworkClients
import com.example.videodownload.util.NetworkConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** 使用公开嵌入服务解析 X/Twitter 视频，不读取用户 Cookie。 */
class TwitterApiParser(
    private val client: OkHttpClient = NetworkClients.standard,
) : VideoParser {

    override fun supports(url: String): Boolean = extractTweetReference(url) != null

    override suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val reference = extractTweetReference(url) ?: return@withContext null
        try {
            val endpoints = buildList {
                // v2 支持仅凭推文 ID 查询，适用于 x.com/i/status/{id} 分享链接。
                add("https://api.fxtwitter.com/2/status/${reference.id}" to ApiKind.FX)
                reference.username?.let { username ->
                    add("https://api.fxtwitter.com/$username/status/${reference.id}" to ApiKind.FX)
                    add("https://api.vxtwitter.com/$username/status/${reference.id}" to ApiKind.VX)
                }
            }

            endpoints.forEach { (endpoint, kind) ->
                val json = fetchJson(endpoint) ?: return@forEach
                val result = when (kind) {
                    ApiKind.FX -> parseFxResponse(json, url)
                    ApiKind.VX -> parseVxResponse(json, url)
                }
                if (result != null) return@withContext result
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "X/Twitter 解析失败", e)
            null
        }
    }

    private fun fetchJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkConstants.USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.contains("json", ignoreCase = true)) return null
            response.body?.string()
        } ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun parseFxResponse(root: JSONObject, webpageUrl: String): VideoInfo? {
        val tweet = root.optJSONObject("status") ?: root.optJSONObject("tweet") ?: return null
        val mediaOwners = buildList {
            add(tweet)
            tweet.optJSONObject("quote")?.let(::add)
        }
        val formats = mutableListOf<VideoFormat>()
        mediaOwners.forEachIndexed { ownerIndex, owner ->
            val videos = owner.optJSONObject("media")?.optJSONArray("videos") ?: return@forEachIndexed
            appendFxVideos(videos, ownerIndex, formats)
        }
        if (formats.isEmpty()) return null

        return VideoInfo(
            title = tweet.optString("text", "X Video").ifBlank { "X Video" }.take(80),
            thumbnailUrl = formats.firstNotNullOfOrNull(VideoFormat::thumbnailUrl),
            formats = formats.distinctBy(VideoFormat::url),
            webpageUrl = webpageUrl,
        )
    }

    private fun appendFxVideos(
        videos: JSONArray,
        ownerIndex: Int,
        output: MutableList<VideoFormat>,
    ) {
        for (index in 0 until videos.length()) {
            val video = videos.optJSONObject(index) ?: continue
            val videoUrl = video.optString("url").takeIf { it.startsWith("https://") } ?: continue
            val height = video.optInt("height", 0)
            val thumbnail = video.optString("thumbnail_url").ifBlank {
                video.optString("thumbnail")
            }.takeIf { it.isNotBlank() }
            output += VideoFormat(
                formatId = "fx_${ownerIndex}_$index",
                quality = if (height > 0) "${height}p" else "Video ${output.size + 1}",
                ext = extensionFromUrl(videoUrl),
                filesize = getRemoteFileSize(videoUrl),
                url = videoUrl,
                height = height,
                thumbnailUrl = thumbnail,
            )
        }
    }

    private fun parseVxResponse(root: JSONObject, webpageUrl: String): VideoInfo? {
        if (!root.optBoolean("hasMedia", true)) return null
        val formats = mutableListOf<VideoFormat>()
        val media = root.optJSONArray("media_extended")
        if (media != null) {
            for (index in 0 until media.length()) {
                val item = media.optJSONObject(index) ?: continue
                if (item.optString("type") != "video") continue
                val videoUrl = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val height = item.optJSONObject("size")?.optInt("height") ?: 0
                formats += VideoFormat(
                    formatId = "vx_$index",
                    quality = if (height > 0) "${height}p" else "Video ${index + 1}",
                    ext = extensionFromUrl(videoUrl),
                    filesize = getRemoteFileSize(videoUrl),
                    url = videoUrl,
                    height = height,
                    thumbnailUrl = item.optString("thumbnail_url").takeIf { it.isNotBlank() },
                )
            }
        }

        if (formats.isEmpty()) {
            val urls = root.optJSONArray("mediaURLs")
            if (urls != null) {
                for (index in 0 until urls.length()) {
                    val videoUrl = urls.optString(index)
                    if (!videoUrl.startsWith("https://") || !isVideoUrl(videoUrl)) continue
                    formats += VideoFormat(
                        formatId = "vx_url_$index",
                        quality = "Video ${formats.size + 1}",
                        ext = extensionFromUrl(videoUrl),
                        filesize = getRemoteFileSize(videoUrl),
                        url = videoUrl,
                    )
                }
            }
        }
        if (formats.isEmpty()) return null

        return VideoInfo(
            title = root.optString("text", "X Video").ifBlank { "X Video" }.take(80),
            thumbnailUrl = formats.firstNotNullOfOrNull(VideoFormat::thumbnailUrl),
            formats = formats.distinctBy(VideoFormat::url),
            webpageUrl = webpageUrl,
        )
    }

    private fun getRemoteFileSize(url: String): Long? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkConstants.USER_AGENT)
            .head()
            .build()
        client.newCall(request).execute().use { response ->
            response.takeIf { it.isSuccessful }
                ?.header("Content-Length")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        }
    }.getOrNull()

    private fun extensionFromUrl(url: String): String {
        return urlExtension(url)?.takeIf { it in VIDEO_EXTENSIONS } ?: "mp4"
    }

    private fun isVideoUrl(url: String): Boolean = urlExtension(url) in VIDEO_EXTENSIONS

    private fun urlExtension(url: String): String? {
        val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
        return path.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
    }

    internal data class TweetReference(val username: String?, val id: String)

    private enum class ApiKind { FX, VX }

    internal companion object {
        private const val TAG = "TwitterApiParser"
        private val ALLOWED_HOSTS = setOf("x.com", "twitter.com")
        private val USERNAME_REGEX = Regex("[A-Za-z0-9_]{1,15}")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm")

        fun extractTweetReference(url: String): TweetReference? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            val host = parsed.host.removePrefix("www.").removePrefix("mobile.")
            if (host !in ALLOWED_HOSTS) return null
            val segments = parsed.pathSegments
            val statusIndex = segments.indexOfFirst { it.equals("status", ignoreCase = true) }
            if (statusIndex <= 0 || statusIndex + 1 >= segments.size) return null
            val username = segments[statusIndex - 1]
            val id = segments[statusIndex + 1]
            if (id.isEmpty() || id.any { !it.isDigit() }) return null
            if (username.equals("i", ignoreCase = true)) return TweetReference(null, id)
            if (!USERNAME_REGEX.matches(username)) return null
            return TweetReference(username, id)
        }
    }
}
