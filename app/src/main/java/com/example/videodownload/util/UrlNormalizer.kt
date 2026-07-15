package com.example.videodownload.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface ShortLinkResolver {
    suspend fun resolve(url: String): String?
}

class B23ShortLinkResolver(
    private val client: OkHttpClient = NetworkClients.noRedirect,
) : ShortLinkResolver {
    override suspend fun resolve(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkConstants.USER_AGENT)
                    .build()
            ).execute().use { it.header("Location") }
        }.getOrNull()
    }
}

/** 从分享文案中提取链接，并统一常见站点的 URL 格式。 */
class UrlNormalizer(
    private val shortLinkResolver: ShortLinkResolver = B23ShortLinkResolver(),
) {
    fun extract(text: String): String? = URL_REGEX.find(text)?.value

    suspend fun normalize(url: String): String {
        var result = url.trim()

        if (result.contains("b23.tv", ignoreCase = true)) {
            shortLinkResolver.resolve(result)?.let { result = it }
        }

        BILIBILI_ID_REGEX.find(result)?.groupValues?.get(1)?.let { id ->
            return "https://www.bilibili.com/video/$id"
        }

        if (result.contains("bilibili.com", ignoreCase = true)) {
            return result
                .replace("http://", "https://")
                .replace("m.bilibili.com", "www.bilibili.com")
                .substringBefore("?")
        }

        if (result.contains("instagram.com", ignoreCase = true) || isXUrl(result)) {
            return result.substringBefore("?")
        }
        return result
    }

    private fun isXUrl(url: String): Boolean =
        url.contains("x.com", ignoreCase = true) ||
            url.contains("twitter.com", ignoreCase = true)

    private companion object {
        val URL_REGEX = Regex("""https?://[\w\-_]+(\.[\w\-_]+)+[\w\-.,@?^=%&:/~+#]*""")
        val BILIBILI_ID_REGEX = Regex(
            """bilibili\.com/(?:video/|bangumi/play/)(BV[\w]+|av\d+|ep\d+|ss\d+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
