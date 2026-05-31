package com.example.videodownload

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Application 类，用于全局初始化
 */
class MainApplication : Application() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            
            // 后台静默更新 yt-dlp 到最新稳定版，以应对 X/Twitter 等网站的频繁 API 变动
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@MainApplication, YoutubeDL.UpdateChannel.STABLE)
                    Log.d("MainApplication", "yt-dlp updated successfully")
                } catch (e: Exception) {
                    Log.e("MainApplication", "Failed to update yt-dlp", e)
                }
            }
        } catch (e: YoutubeDLException) {
            Log.e("MainApplication", "Failed to initialize youtubedl-android", e)
        }
    }
}
