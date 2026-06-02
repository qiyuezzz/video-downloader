package com.example.videodownload.downloader

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.util.NetworkConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 视频下载器
 * 使用 OkHttp 下载视频流，通过 SAF 写入用户选择的目录
 */
class VideoDownloader(private val context: Context) {

    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private val ILLEGAL_CHAR_REGEX = Regex("[/\\\\:*?\"<>|]")
    }

    /**
     * 下载视频到指定目录，返回状态流
     */
    fun downloadFlow(
        videoUrl: String,
        fileName: String,
        ext: String,
        directoryUri: Uri,
        referer: String? = null,
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0))

        try {
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
                ?: throw IOException("无法访问保存目录")

            val safeName = fileName.replace(ILLEGAL_CHAR_REGEX, "_")
            val mimeType = getMimeType(ext)
            val file = dir.createFile(mimeType, "$safeName.$ext")
                ?: throw IOException("无法创建文件")

            val isBilibili = videoUrl.contains("bilivideo.com") || referer?.contains("bilibili.com") == true
            val userAgent = if (isBilibili) NetworkConstants.USER_AGENT_DESKTOP else NetworkConstants.USER_AGENT

            val request = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer != null) {
                        val safeReferer = if (isBilibili) NetworkConstants.BILIBILI_BASE_URL else referer
                        header("Referer", safeReferer)
                    }
                }
                .build()
            
            val response = sharedClient.newCall(request).execute()

            if (!response.isSuccessful) {
                file.delete()
                throw IOException("下载失败: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("响应体为空")
            val totalBytes = body.contentLength()
            
            // 如果 Content-Length 为 0，通常意味着链接失效或被拦截
            if (totalBytes == 0L) {
                file.delete()
                throw IOException("下载失败: 视频内容为空 (0 bytes)")
            }

            var downloadedBytes = 0L
            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val percent =
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceAtMost(100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                emit(DownloadState.Progress(percent))
                            }
                        } else if (downloadedBytes == 0L) {
                            // 未知总大片，仅在开始时发送一次状态更新
                            emit(DownloadState.Progress(-1))
                        }
                    }
                    outputStream.flush()
                }
            } ?: throw IOException("无法打开输出流")

            // 最终校验：如果下载字节数为 0，删除文件
            if (downloadedBytes == 0L) {
                file.delete()
                throw IOException("下载失败: 写入数据为 0")
            }

            emit(DownloadState.Success("$safeName.$ext", file.uri.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "下载失败"))
        }
    }.flowOn(Dispatchers.IO)

    private fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: when (ext.lowercase()) {
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
    }
}
