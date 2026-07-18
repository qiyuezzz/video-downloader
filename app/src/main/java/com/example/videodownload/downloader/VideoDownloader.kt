package com.example.videodownload.downloader

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.R
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
        private const val MAX_FILE_NAME_LENGTH = 120
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
        platformFolder: String? = null,
        referer: String? = null,
        existingFileUri: Uri? = null,
        alreadyDownloadedBytes: Long = 0L,
        expectedTotalBytes: Long = 0L,
    ): Flow<DownloadState> = flow {
        try {
            if (!videoUrl.startsWith("https://", ignoreCase = true)) {
                throw IOException(context.getString(R.string.error_https_only))
            }
            val rootDir = DocumentFile.fromTreeUri(context, directoryUri)
                ?: throw IOException(context.getString(R.string.error_save_directory_access))
            val dir = platformFolder?.let { folderName ->
                rootDir.findFile(folderName)?.takeIf { it.isDirectory }
                    ?: rootDir.createDirectory(folderName)
                    ?: throw IOException(context.getString(R.string.error_create_platform_directory, folderName))
            } ?: rootDir

            val safeName = fileName.replace(ILLEGAL_CHAR_REGEX, "_")
                .trim()
                .trimEnd('.')
                .take(MAX_FILE_NAME_LENGTH)
                .ifEmpty { "video" }
            val safeExt = ext.lowercase().filter(Char::isLetterOrDigit).ifEmpty { "mp4" }

            // 判断是续传还是新下载
            val file: DocumentFile
            val initialBytes: Long

            if (existingFileUri != null && alreadyDownloadedBytes > 0) {
                // 续传模式：使用已有文件
                file = DocumentFile.fromSingleUri(context, existingFileUri)
                    ?: throw IOException(context.getString(R.string.error_existing_file_access))
                initialBytes = alreadyDownloadedBytes
            } else {
                // 新下载模式：创建新文件
                val mimeType = getMimeType(safeExt)
                file = dir.createFile(mimeType, "$safeName.$safeExt")
                    ?: throw IOException(context.getString(R.string.error_create_file))
                initialBytes = 0L
            }

            val fileUriStr = file.uri.toString()

            // 立即发出首次进度（带文件 URI），让调用方能持久化文件位置
            val initialPercent = if (expectedTotalBytes > 0) {
                DownloadProgress.percent(initialBytes, expectedTotalBytes)
            } else if (initialBytes > 0) {
                -1
            } else {
                0
            }
            emit(DownloadState.Progress(initialPercent, fileUriStr, initialBytes, expectedTotalBytes))

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

            if (response.code == 206 && initialBytes > 0) {
                val contentRange = response.header("Content-Range")
                if (contentRange?.startsWith("bytes $initialBytes-") != true) {
                    response.close()
                    throw IOException(context.getString(R.string.error_invalid_range))
                }
            }

            if (!response.isSuccessful && response.code != 206) {
                // 续传失败（服务器不支持 Range 或链接失效），从头开始
                if (initialBytes > 0) {
                    // 先验证全量请求可用，避免链接失效时误删已有断点文件。
                    response.close()
                    val mimeType = getMimeType(safeExt)
                    val newFile = dir.createFile(mimeType, "$safeName.$safeExt")
                        ?: throw IOException(context.getString(R.string.error_create_file))

                    val newFileUriStr = newFile.uri.toString()

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
                        freshResponse.close()
                        throw IOException(context.getString(R.string.error_download_http, freshResponse.code))
                    }

                    freshResponse.use { safeResponse ->
                        try {
                            validateContentType(safeResponse)
                        } catch (e: IOException) {
                            newFile.delete()
                            throw e
                        }
                        file.delete()
                        emit(DownloadState.Progress(0, newFileUriStr, 0, 0))
                        val freshTotalBytes = safeResponse.body?.contentLength() ?: -1L
                        downloadToFile(safeResponse, newFile, 0L, freshTotalBytes, newFileUriStr) { emit(it) }
                    }
                    emit(DownloadState.Success(newFile.name ?: "$safeName.$safeExt", newFileUriStr))
                    return@flow
                } else {
                    file.delete()
                    response.close()
                    throw IOException(context.getString(R.string.error_download_http, response.code))
                }
            }

            validateContentType(response)

            val body = response.body ?: run {
                response.close()
                throw IOException(context.getString(R.string.error_empty_response))
            }

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
                response.close()
                throw IOException(context.getString(R.string.error_empty_video))
            }

            val startBytes = if (response.code == 206) initialBytes else 0L
            if (totalBytes <= 0) {
                emit(DownloadState.Progress(-1, fileUriStr, startBytes, 0))
            }
            response.use { safeResponse ->
                downloadToFile(safeResponse, file, startBytes, totalBytes, fileUriStr) { emit(it) }
            }

            emit(DownloadState.Success(file.name ?: "$safeName.$safeExt", fileUriStr))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: context.getString(R.string.error_download_failed)))
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
        val body = response.body ?: throw IOException(context.getString(R.string.error_empty_response))
        var downloadedBytes = startBytes

        // 续传时使用追加模式 "wa"，新下载使用默认模式
        val outputStream = if (startBytes > 0) {
            context.contentResolver.openOutputStream(file.uri, "wa")
        } else {
            context.contentResolver.openOutputStream(file.uri)
        } ?: throw IOException(context.getString(R.string.error_open_output))

        outputStream.use { os ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastReportedPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val percent = DownloadProgress.percent(
                            downloadedBytes,
                            totalBytes,
                            completed = downloadedBytes >= totalBytes,
                        )
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            emit(DownloadState.Progress(percent, fileUriStr, downloadedBytes, totalBytes))
                        }
                    } else if (lastReportedPercent != -1) {
                        lastReportedPercent = -1
                        emit(DownloadState.Progress(-1, fileUriStr, downloadedBytes, 0))
                    }
                }
                os.flush()
            }
        }

        if (downloadedBytes == 0L && startBytes == 0L) {
            file.delete()
            throw IOException(context.getString(R.string.error_zero_written))
        }
    }

    private fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: when (ext.lowercase()) {
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
    }

    private fun validateContentType(response: okhttp3.Response) {
        val contentType = response.body?.contentType()?.toString()?.lowercase().orEmpty()
        if (contentType.startsWith("text/") ||
            contentType.contains("json") ||
            contentType.contains("xml")
        ) {
            response.close()
            throw IOException(context.getString(R.string.error_not_video_content, contentType))
        }
    }
}
