package com.example.videodownload

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.yausername.ffmpeg.FFmpeg
import com.example.videodownload.parser.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类，用于全局初始化
 */
class MainApplication : Application(), ImageLoaderFactory {
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
            } catch (e: Exception) {
                Log.e("MainApplication", "yt-dlp 初始化失败", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
