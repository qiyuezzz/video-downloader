package com.example.videodownload.parser

import android.content.Context
import com.example.videodownload.R
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.util.NetworkClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过公开 Embed 服务匿名解析 Instagram 帖子，不读取账号或 Cookie。
 */
class InstagramAnonymousParser(
    private val client: OkHttpClient = NetworkClients.standard,
    private val context: Context? = null,
) : VideoParser {

    override fun supports(url: String): Boolean =
        url.contains("instagram.com", ignoreCase = true)

    override suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val postPath = extractPostPath(url) ?: return@withContext null
        runCatching {
            val shortcode = postPath.substringAfter('/')
            val request = Request.Builder()
                // Reel、帖子和 IGTV 的 shortcode 都可通过 p/{shortcode}/embed 查询。
                .url("https://www.instagram.com/p/$shortcode/embed/captioned/")
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.instagram.com/")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            } ?: return@withContext null

            val metadata = parseOpenGraph(html)
            val embedMedia = parseEmbedVideo(html)
            val videoUrl = embedMedia?.videoUrl
                ?: metadata["og:video:secure_url"]
                ?: metadata["og:video"]
                ?: return@withContext null
            val thumbnailUrl = embedMedia?.thumbnailUrl
                ?: metadata["og:image:secure_url"]
                ?: metadata["og:image"]
            val width = metadata["og:video:width"]?.toIntOrNull() ?: 0
            val height = metadata["og:video:height"]?.toIntOrNull() ?: 0

            VideoInfo(
                title = metadata["og:title"].orEmpty().ifBlank {
                    context?.getString(R.string.instagram_video_title) ?: "Instagram video"
                },
                thumbnailUrl = thumbnailUrl,
                formats = listOf(
                    VideoFormat(
                        formatId = "instagram_anonymous_$shortcode",
                        quality = if (width > 0 && height > 0) {
                            "${width}×${height}"
                        } else {
                            context?.getString(R.string.public_video_quality) ?: "Video"
                        },
                        ext = "mp4",
                        filesize = null,
                        url = videoUrl,
                        height = height,
                        thumbnailUrl = thumbnailUrl,
                    )
                ),
                webpageUrl = url,
            )
        }.getOrNull()
    }

    internal companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                "Mobile/15E148 Safari/604.1"
        private val POST_PATH_REGEX = Regex(
            """instagram\.com/(?:[^/]+/)?(p|reel|tv)/([A-Za-z0-9_-]+)""",
            RegexOption.IGNORE_CASE,
        )
        private val META_TAG_REGEX = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE_REGEX = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        private val JSON_VIDEO_URL_REGEX =
            Regex(""""video_url"\s*:\s*"(https:[^"]+)"""")
        private val JSON_DISPLAY_URL_REGEX =
            Regex(""""display_url"\s*:\s*"(https:[^"]+)"""")

        fun extractPostPath(url: String): String? {
            val match = POST_PATH_REGEX.find(url) ?: return null
            return "${match.groupValues[1].lowercase()}/${match.groupValues[2]}"
        }

        fun parseOpenGraph(html: String): Map<String, String> = buildMap {
            META_TAG_REGEX.findAll(html).forEach { tagMatch ->
                val attributes = ATTRIBUTE_REGEX.findAll(tagMatch.value).associate { match ->
                    match.groupValues[1].lowercase() to
                        decodeHtml(match.groupValues[2].ifEmpty { match.groupValues[3] })
                }
                val key = attributes["property"] ?: attributes["name"] ?: return@forEach
                attributes["content"]?.let { put(key.lowercase(), it) }
            }
        }

        internal data class EmbedMedia(val videoUrl: String, val thumbnailUrl: String?)

        /** 解析 Instagram embed 页中普通或双重转义的媒体 JSON。 */
        fun parseEmbedVideo(html: String): EmbedMedia? {
            val normalized = html
                .replace("\\\\\"", "\"")
                .replace("\\\"", "\"")
            val videoUrl = JSON_VIDEO_URL_REGEX.find(normalized)?.groupValues?.get(1)
                ?: return null
            val thumbnailUrl = JSON_DISPLAY_URL_REGEX.find(normalized)?.groupValues?.get(1)
            return EmbedMedia(
                videoUrl = decodeEmbedValue(videoUrl),
                thumbnailUrl = thumbnailUrl?.let(::decodeEmbedValue),
            )
        }

        private fun decodeEmbedValue(value: String): String = decodeHtml(value)
            .replace("\\\\\\/", "/")
            .replace("\\/", "/")
            .replace("\\u0026", "&")

        private fun decodeHtml(value: String): String = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
