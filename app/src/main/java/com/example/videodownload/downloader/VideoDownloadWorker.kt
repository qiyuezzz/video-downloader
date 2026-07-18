package com.example.videodownload.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.videodownload.MainActivity
import com.example.videodownload.R
import com.example.videodownload.data.model.DownloadState
import kotlinx.coroutines.flow.collect

/** 由 WorkManager 承载的可恢复下载任务，应用退到后台后仍可继续运行。 */
class VideoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val input = DownloadInput.from(inputData)
            ?: return Result.failure(errorData(applicationContext.getString(R.string.error_download_input_incomplete)))

        setForeground(createForegroundInfo(input.fileName, -1))
        var terminalResult: Result? = null

        VideoDownloader(applicationContext).downloadFlow(
            videoUrl = input.videoUrl,
            fileName = input.fileName,
            ext = input.ext,
            directoryUri = input.directoryUri.toUri(),
            platformFolder = input.platformFolder,
            referer = input.referer,
            existingFileUri = input.existingFileUri?.toUri(),
            alreadyDownloadedBytes = input.downloadedBytes,
            expectedTotalBytes = input.totalBytes,
        ).collect { state ->
            when (state) {
                is DownloadState.Progress -> {
                    val progress = workDataOf(
                        KEY_PERCENT to state.percent,
                        KEY_FILE_URI to state.fileUri.orEmpty(),
                        KEY_DOWNLOADED_BYTES to state.downloadedBytes,
                        KEY_TOTAL_BYTES to state.totalBytes,
                    )
                    setProgress(progress)
                    setForeground(createForegroundInfo(input.fileName, state.percent))
                }
                is DownloadState.Success -> {
                    terminalResult = Result.success(
                        workDataOf(
                            KEY_FILE_NAME to state.fileName,
                            KEY_FILE_URI to state.fileUri.orEmpty(),
                        )
                    )
                }
                is DownloadState.Error -> terminalResult = Result.failure(errorData(state.message))
                DownloadState.Idle, DownloadState.Interrupted -> Unit
            }
        }

        return terminalResult ?: Result.failure(
            errorData(applicationContext.getString(R.string.error_download_abnormal_end))
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(inputData.getString(KEY_FILE_NAME).orEmpty(), -1)

    private fun createForegroundInfo(fileName: String, percent: Int): ForegroundInfo {
        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (percent in 0..100) setProgress(100, percent, false)
                else setProgress(0, 0, true)
            }
            .build()

        return ForegroundInfo(
            id.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    data class DownloadInput(
        val videoUrl: String,
        val fileName: String,
        val ext: String,
        val directoryUri: String,
        val platformFolder: String?,
        val referer: String?,
        val existingFileUri: String?,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) {
        fun toData(): Data = workDataOf(
            KEY_VIDEO_URL to videoUrl,
            KEY_FILE_NAME to fileName,
            KEY_EXT to ext,
            KEY_DIRECTORY_URI to directoryUri,
            KEY_PLATFORM_FOLDER to platformFolder,
            KEY_REFERER to referer,
            KEY_EXISTING_FILE_URI to existingFileUri,
            KEY_DOWNLOADED_BYTES to downloadedBytes,
            KEY_TOTAL_BYTES to totalBytes,
        )

        companion object {
            fun from(data: Data): DownloadInput? {
                val videoUrl = data.getString(KEY_VIDEO_URL) ?: return null
                val fileName = data.getString(KEY_FILE_NAME) ?: return null
                val directoryUri = data.getString(KEY_DIRECTORY_URI) ?: return null
                return DownloadInput(
                    videoUrl = videoUrl,
                    fileName = fileName,
                    ext = data.getString(KEY_EXT) ?: "mp4",
                    directoryUri = directoryUri,
                    platformFolder = data.getString(KEY_PLATFORM_FOLDER),
                    referer = data.getString(KEY_REFERER),
                    existingFileUri = data.getString(KEY_EXISTING_FILE_URI),
                    downloadedBytes = data.getLong(KEY_DOWNLOADED_BYTES, 0),
                    totalBytes = data.getLong(KEY_TOTAL_BYTES, 0),
                )
            }
        }
    }

    companion object {
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_EXT = "extension"
        const val KEY_DIRECTORY_URI = "directory_uri"
        const val KEY_PLATFORM_FOLDER = "platform_folder"
        const val KEY_REFERER = "referer"
        const val KEY_EXISTING_FILE_URI = "existing_file_uri"
        const val KEY_PERCENT = "percent"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "video_downloads"
    }
}
