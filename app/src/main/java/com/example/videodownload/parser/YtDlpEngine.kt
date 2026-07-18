package com.example.videodownload.parser

import android.content.Context
import com.example.videodownload.R
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一管理 yt-dlp 初始化，避免启动阶段失败后所有调用只返回
 * `instance not initialized` 而丢失真正原因。
 */
object YtDlpEngine {
    private val initializationMutex = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    private var lastInitializationError: Throwable? = null

    suspend fun ensureInitialized(context: Context) {
        if (initialized) return

        initializationMutex.withLock {
            if (initialized) return

            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                try {
                    initialize(appContext)
                } catch (_: Throwable) {
                    // 初始化可能因上次解压中断而留下不完整文件，仅清理引擎目录后重试。
                    File(appContext.noBackupFilesDir, YoutubeDL.baseName).deleteRecursively()
                    try {
                        initialize(appContext)
                    } catch (retryError: Throwable) {
                        lastInitializationError = retryError
                        throw IllegalStateException(
                            appContext.getString(R.string.error_ytdlp_init, rootMessage(retryError)),
                            retryError,
                        )
                    }
                }
            }
        }
    }

    fun lastErrorMessage(): String? = lastInitializationError?.let(::rootMessage)

    private fun initialize(context: Context) {
        YoutubeDL.getInstance().init(context)
        initialized = true
        lastInitializationError = null
    }

    private fun rootMessage(error: Throwable): String {
        var cause = error
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause.message?.takeIf { it.isNotBlank() }
            ?: cause.javaClass.simpleName
    }
}
