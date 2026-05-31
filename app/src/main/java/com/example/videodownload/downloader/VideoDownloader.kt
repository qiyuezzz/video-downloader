package com.example.videodownload.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.videodownload.data.model.DownloadState
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
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
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

            val request = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", USER_AGENT)
                .apply {
                    if (referer != null) {
                        header("Referer", referer)
                    }
                    val cookies = collectCookies(videoUrl)
                    if (cookies.isNotEmpty()) {
                        header("Cookie", cookies)
                    }
                }
                .build()
            val response = sharedClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("下载失败: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("响应体为空")
            val totalBytes = body.contentLength()

            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
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
                        }
                    }
                    outputStream.flush()
                }
            } ?: throw IOException("无法打开输出流")

            emit(DownloadState.Success("$safeName.$ext", file.uri.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "下载失败"))
        }
    }.flowOn(Dispatchers.IO)

    private fun collectCookies(videoUrl: String): String {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf(videoUrl, "https://x.com/", "https://twitter.com/", "https://video.twimg.com/")
        val allCookies = domains
            .mapNotNull { cookieManager.getCookie(it) }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return allCookies.joinToString("; ")
    }

    private fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: when (ext.lowercase()) {
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
    }
}
