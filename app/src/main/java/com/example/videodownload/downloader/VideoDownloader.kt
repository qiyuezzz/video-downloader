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
 * 支持 HTTP Range 断点续传
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
     *
     * @param existingFileUri 已有文件的 URI（断点续传时使用）
     * @param alreadyDownloadedBytes 已下载的字节数（断点续传时使用，0 表示新下载）
     */
    fun downloadFlow(
        videoUrl: String,
        fileName: String,
        ext: String,
        directoryUri: Uri,
        referer: String? = null,
        existingFileUri: Uri? = null,
        alreadyDownloadedBytes: Long = 0L,
    ): Flow<DownloadState> = flow {
        try {
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
                ?: throw IOException("无法访问保存目录")

            val safeName = fileName.replace(ILLEGAL_CHAR_REGEX, "_")

            // 判断是续传还是新下载
            val file: DocumentFile
            val initialBytes: Long

            if (existingFileUri != null && alreadyDownloadedBytes > 0) {
                // 续传模式：使用已有文件
                file = DocumentFile.fromSingleUri(context, existingFileUri)
                    ?: throw IOException("无法访问已有文件")
                initialBytes = alreadyDownloadedBytes
            } else {
                // 新下载模式：创建新文件
                val mimeType = getMimeType(ext)
                file = dir.createFile(mimeType, "$safeName.$ext")
                    ?: throw IOException("无法创建文件")
                initialBytes = 0L
            }

            val fileUriStr = file.uri.toString()

            // 立即发出首次进度（带文件 URI），让调用方能持久化文件位置
            emit(DownloadState.Progress(if (initialBytes > 0) ((initialBytes * 100) / (initialBytes + 1)).toInt().coerceAtMost(99) else 0, fileUriStr))

            val isBilibili = videoUrl.contains("bilivideo.com") || referer?.contains("bilibili.com") == true
            val userAgent = if (isBilibili) NetworkConstants.USER_AGENT_DESKTOP else NetworkConstants.USER_AGENT

            val requestBuilder = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer != null) {
                        val safeReferer = if (isBilibili) NetworkConstants.BILIBILI_BASE_URL else referer
                        header("Referer", safeReferer)
                    }
                }

            // 断点续传：添加 Range 头
            if (initialBytes > 0) {
                requestBuilder.header("Range", "bytes=$initialBytes-")
            }

            val response = sharedClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                // 续传失败（服务器不支持 Range 或链接失效），从头开始
                if (initialBytes > 0) {
                    // 删除不完整的文件，重新创建
                    file.delete()
                    val mimeType = getMimeType(ext)
                    val newFile = dir.createFile(mimeType, "$safeName.$ext")
                        ?: throw IOException("无法创建文件")

                    val newFileUriStr = newFile.uri.toString()
                    emit(DownloadState.Progress(0, newFileUriStr))

                    // 重新发起不带 Range 的请求
                    val freshRequest = Request.Builder()
                        .url(videoUrl)
                        .header("User-Agent", userAgent)
                        .apply {
                            if (referer != null) {
                                val safeReferer = if (isBilibili) NetworkConstants.BILIBILI_BASE_URL else referer
                                header("Referer", safeReferer)
                            }
                        }
                        .build()

                    val freshResponse = sharedClient.newCall(freshRequest).execute()
                    if (!freshResponse.isSuccessful) {
                        newFile.delete()
                        throw IOException("下载失败: HTTP ${freshResponse.code}")
                    }

                    downloadToFile(freshResponse, newFile, 0L, -1L, newFileUriStr) { emit(it) }
                    emit(DownloadState.Success("$safeName.$ext", newFileUriStr))
                    return@flow
                } else {
                    file.delete()
                    throw IOException("下载失败: HTTP ${response.code}")
                }
            }

            val body = response.body ?: throw IOException("响应体为空")

            // 计算总大小和起始偏移
            val contentLength = body.contentLength()
            val totalBytes = if (response.code == 206 && initialBytes > 0) {
                // 206 响应：Content-Length 是剩余部分大小，总大小 = 已下载 + 剩余
                initialBytes + contentLength
            } else {
                contentLength
            }

            if (totalBytes == 0L && initialBytes == 0L) {
                file.delete()
                throw IOException("下载失败: 视频内容为空 (0 bytes)")
            }

            val startBytes = if (response.code == 206) initialBytes else 0L
            downloadToFile(response, file, startBytes, totalBytes, fileUriStr) { emit(it) }

            emit(DownloadState.Success("$safeName.$ext", fileUriStr))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "下载失败"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 将响应体写入文件
     */
    private suspend fun downloadToFile(
        response: okhttp3.Response,
        file: DocumentFile,
        startBytes: Long,
        totalBytes: Long,
        fileUriStr: String,
        emit: suspend (DownloadState) -> Unit,
    ) {
        val body = response.body ?: throw IOException("响应体为空")
        var downloadedBytes = startBytes

        // 续传时使用追加模式 "wa"，新下载使用默认模式
        val outputStream = if (startBytes > 0) {
            context.contentResolver.openOutputStream(file.uri, "wa")
        } else {
            context.contentResolver.openOutputStream(file.uri)
        } ?: throw IOException("无法打开输出流")

        outputStream.use { os ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastReportedPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceAtMost(100)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            emit(DownloadState.Progress(percent, fileUriStr))
                        }
                    } else if (downloadedBytes == startBytes) {
                        emit(DownloadState.Progress(-1, fileUriStr))
                    }
                }
                os.flush()
            }
        }

        if (downloadedBytes == 0L && startBytes == 0L) {
            file.delete()
            throw IOException("下载失败: 写入数据为 0")
        }
    }

    private fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: when (ext.lowercase()) {
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
    }
}
