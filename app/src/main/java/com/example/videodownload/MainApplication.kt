package com.example.videodownload

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.example.videodownload.parser.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类，用于全局初始化
 */
class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                YtDlpEngine.ensureInitialized(this@MainApplication)
                try {
                    FFmpeg.getInstance().init(this@MainApplication)
                } catch (e: Exception) {
                    Log.e("MainApplication", "FFmpeg 初始化失败", e)
                }

                // 后台静默更新 yt-dlp；失败不影响使用随 APK 打包的版本。
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@MainApplication, YoutubeDL.UpdateChannel.STABLE)
                    Log.d("MainApplication", "yt-dlp 更新成功")
                } catch (e: Exception) {
                    Log.e("MainApplication", "yt-dlp 在线更新失败，将使用内置版本", e)
                }
            } catch (e: Exception) {
                Log.e("MainApplication", "yt-dlp 初始化失败", e)
            }
        }
    }
}
