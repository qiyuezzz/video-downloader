package com.example.videodownload.parser

import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.util.NetworkConstants
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 yt-dlp 的视频解析器
 */
class YtDlpParser : VideoParser {

    override fun supports(url: String): Boolean = true

    /**
     * 解析视频链接
     */
    override suspend fun parse(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            
            // 基础配置
            request.addOption("--no-check-certificate")
            request.addOption("--add-header", "Accept-Language:zh-CN,zh;q=0.9,en;q=0.8")

            // 针对特定网站的优化
            if (url.contains("bilibili.com") || url.contains("b23.tv")) {
                request.addOption("--add-header", "Referer:${NetworkConstants.BILIBILI_BASE_URL}")
                request.addOption("--allow-unplayable-formats")
                request.addOption("--user-agent", NetworkConstants.USER_AGENT_DESKTOP)
            } else {
                request.addOption("--user-agent", NetworkConstants.USER_AGENT)
            }

            // 获取视频信息，相当于 yt-dlp --dump-json
            val streamInfo = YoutubeDL.getInstance().getInfo(request)
            val formats = mutableListOf<VideoFormat>()
            
            // 过滤并转换格式
            streamInfo.formats?.forEach { format ->
                val ext = format.ext ?: return@forEach
                val vcodec = format.vcodec
                val urlFormat = format.url ?: return@forEach
                
                // 我们主要关注 mp4/m3u8 且有视频轨的
                if ((ext == "mp4" || ext == "m3u8" || ext == "webm") && vcodec != "none") {
                    // yt-dlp 返回的高度
                    val height = format.height ?: 0
                    val filesize = if (format.fileSize > 0) format.fileSize else null
                    
                    formats.add(
                        VideoFormat(
                            formatId = format.formatId ?: "unknown",
                            quality = if (height > 0) "${height}p" else "Video",
                            ext = ext,
                            filesize = filesize,
                            url = urlFormat,
                            height = height,
                            thumbnailUrl = streamInfo.thumbnail
                        )
                    )
                }
            }

            // 按照高度降序排序
            formats.sortByDescending { it.height }

            // 去重，同一个画质保留一个
            val distinctFormats = formats.distinctBy { it.height }.toMutableList()
            
            // 如果没有提取到具体格式，但主链接存在，提供一个默认选项
            if (distinctFormats.isEmpty() && !streamInfo.url.isNullOrBlank()) {
                distinctFormats.add(
                    VideoFormat(
                        formatId = "default",
                        quality = "Default",
                        ext = streamInfo.ext ?: "mp4",
                        filesize = null,
                        url = streamInfo.url!!,
                        height = 0,
                        thumbnailUrl = streamInfo.thumbnail
                    )
                )
            }

            if (distinctFormats.isEmpty()) return@withContext null

            return@withContext VideoInfo(
                title = streamInfo.title ?: "Unknown Video",
                thumbnailUrl = streamInfo.thumbnail,
                formats = distinctFormats,
                webpageUrl = url
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 重新抛出异常，让上层捕获并显示
            throw e
        }
    }
}
