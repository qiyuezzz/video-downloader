package com.example.videodownload.parser

import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.util.NetworkClients
import com.example.videodownload.util.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * B站原生 API 解析器 (极速版)
 * 绕过 yt-dlp 的 Python 初始化，直接通过 B站接口获取 480P/720P 视频
 */
class BilibiliNativeParser(
    private val client: OkHttpClient = NetworkClients.standard,
) : VideoParser {

    override fun supports(url: String): Boolean =
        url.contains("bilibili.com", ignoreCase = true)

    override suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val bvid = extractBvid(url) ?: return@withContext null
            
            // 1. 获取视频基本信息 (Title, Thumbnail, cid)
            val viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
            val viewRequest = Request.Builder()
                .url(viewUrl)
                .header("User-Agent", NetworkConstants.USER_AGENT_DESKTOP)
                .build()
            
            val viewJson = client.newCall(viewRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty())
            }
            if (viewJson.getInt("code") != 0) return@withContext null
            
            val data = viewJson.getJSONObject("data")
            val title = data.getString("title")
            val pic = data.getString("pic")
            val cid = data.getLong("cid")

            // 2. 获取视频流地址 (使用 html5 平台接口，通常返回单链接 mp4)
            // qn=32 是 480P, qn=64 是 720P (游客最高通常只能到这里)
            val playUrl = "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=64&type=&otype=json&platform=html5&high_quality=1"
            val playRequest = Request.Builder()
                .url(playUrl)
                .header("User-Agent", NetworkConstants.USER_AGENT_DESKTOP)
                .header("Referer", "https://www.bilibili.com/video/$bvid")
                .build()
            
            val playJson = client.newCall(playRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty())
            }
            if (playJson.getInt("code") != 0) return@withContext null
            
            val playData = playJson.getJSONObject("data")
            val qn = playData.optInt("quality") // 获取实际下发的质量等级
            val qualityLabel = when(qn) {
                112 -> "1080P+"
                80 -> "1080P"
                64 -> "720P"
                32 -> "480P"
                16 -> "360P"
                else -> "Auto"
            }
            
            val durl = playData.getJSONArray("durl")
            val formats = mutableListOf<VideoFormat>()
            
            for (i in 0 until durl.length()) {
                val item = durl.getJSONObject(i)
                val videoUrl = item.getString("url")
                val size = item.getLong("size")
                
                formats.add(
                    VideoFormat(
                        formatId = "bili_native_$i",
                        quality = qualityLabel,
                        ext = "mp4",
                        filesize = if (size > 0) size else null,
                        url = videoUrl,
                        height = when(qn) {
                            112, 80 -> 1080
                            64 -> 720
                            32 -> 480
                            else -> 0
                        },
                        thumbnailUrl = pic
                    )
                )
            }

            if (formats.isEmpty()) return@withContext null

            return@withContext VideoInfo(
                title = title,
                thumbnailUrl = pic,
                formats = formats,
                webpageUrl = "https://www.bilibili.com/video/$bvid"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractBvid(url: String): String? {
        val pattern = Pattern.compile("(BV[a-zA-Z0-9]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
}
