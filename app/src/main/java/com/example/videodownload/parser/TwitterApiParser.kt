package com.example.videodownload.parser

import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * 专门针对 X/Twitter 链接的轻量级 API 解析器
 * 用于解决 yt-dlp 无法获取敏感/限制级推文视频的问题
 */
class TwitterApiParser {
    private val client = OkHttpClient()

    suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val tweetId = extractTweetId(url) ?: return@withContext null
            val apiUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"

            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: return@withContext null
            val json = JSONObject(jsonString)

            if (!json.optBoolean("hasMedia", false)) return@withContext null

            val mediaArray = json.optJSONArray("media_extended") ?: return@withContext null
            val formats = mutableListOf<VideoFormat>()

            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.getJSONObject(i)
                if (media.optString("type") == "video") {
                    val videoUrl = media.optString("url")
                    val size = media.optJSONObject("size")
                    val height = size?.optInt("height") ?: 0
                    val thumbUrl = media.optString("thumbnail_url")
                    
                    // 尝试通过网络请求获取文件大小
                    val fileSize = getRemoteFileSize(videoUrl)
                    
                    formats.add(
                        VideoFormat(
                            formatId = "vx_video_$i",
                            quality = if (height > 0) "${height}p (Video ${i + 1})" else "Video ${i + 1}",
                            ext = "mp4",
                            filesize = fileSize,
                            url = videoUrl,
                            height = height,
                            thumbnailUrl = thumbUrl.ifEmpty { null }
                        )
                    )
                }
            }

            if (formats.isEmpty()) return@withContext null

            return@withContext VideoInfo(
                title = json.optString("text", "X Video").take(50),
                thumbnailUrl = mediaArray.optJSONObject(0)?.optString("thumbnail_url"),
                formats = formats,
                webpageUrl = url
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getRemoteFileSize(url: String): Long? {
        return try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val length = response.header("Content-Length")?.toLongOrNull()
                    if (length != null && length > 0) length else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("(?:twitter|x)\\.com/(?:\\w+/status|i/status)/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
}
