package com.example.videodownload.parser

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
) : VideoParser {

    override fun supports(url: String): Boolean =
        url.contains("instagram.com", ignoreCase = true)

    override suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val postPath = extractPostPath(url) ?: return@withContext null
        runCatching {
            val request = Request.Builder()
                .url("https://zzinstagram.com/$postPath/")
                .header("User-Agent", EMBED_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            } ?: return@withContext null

            val metadata = parseOpenGraph(html)
            val videoUrl = metadata["og:video:secure_url"]
                ?: metadata["og:video"]
                ?: return@withContext null
            val thumbnailUrl = metadata["og:image:secure_url"] ?: metadata["og:image"]
            val width = metadata["og:video:width"]?.toIntOrNull() ?: 0
            val height = metadata["og:video:height"]?.toIntOrNull() ?: 0
            val shortcode = postPath.substringAfter('/')

            VideoInfo(
                title = metadata["og:title"].orEmpty().ifBlank { "Instagram 视频" },
                thumbnailUrl = thumbnailUrl,
                formats = listOf(
                    VideoFormat(
                        formatId = "instagram_anonymous_$shortcode",
                        quality = if (width > 0 && height > 0) {
                            "${width}×${height}"
                        } else {
                            "公开视频"
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
        private const val EMBED_USER_AGENT =
            "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"
        private val POST_PATH_REGEX = Regex(
            """instagram\.com/(?:[^/]+/)?(p|reel|tv)/([A-Za-z0-9_-]+)""",
            RegexOption.IGNORE_CASE,
        )
        private val META_TAG_REGEX = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE_REGEX = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

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

        private fun decodeHtml(value: String): String = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
