package com.example.videodownload.ui.home

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.CookieManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient

/**
 * 从 VideoSniffer 的 WebView CookieManager 收集所有域名的 Cookie。
 * X/Twitter 的 CDN 认证 Cookie 可能存储在 x.com 域名下而非 video.twimg.com。
 */
private fun collectAllVideoCookies(videoUrl: String): String {
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

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    videoUrl: String,
    webpageUrl: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    // 当 URL 或网页来源变化时，重新构建播放器以确保 Header 正确
    val exoPlayer = remember(videoUrl, webpageUrl) {
        // 收集所有相关域名的 Cookie
        val cookies = collectAllVideoCookies(videoUrl)

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)

        val headers = mutableMapOf<String, String>()
        if (webpageUrl.isNotEmpty()) {
            val referer = if (webpageUrl.contains("x.com") || webpageUrl.contains("twitter.com")) {
                "https://x.com/"
            } else {
                webpageUrl
            }
            headers["Referer"] = referer
        }
        if (cookies.isNotEmpty()) {
            headers["Cookie"] = cookies
        }
        dataSourceFactory.setDefaultRequestProperties(headers)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("VideoPreview", "ExoPlayer Error [${error.errorCode}]: ${error.errorCodeName}", error)
                        val message = when(error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器拒绝访问 (403)"
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "视频解析失败 (请尝试刷新或重新解析)"
                            else -> "播放错误: ${error.errorCodeName}"
                        }
                        Toast.makeText(context, "播放预览失败: $message", Toast.LENGTH_SHORT).show()
                    }
                })

                if (videoUrl.isNotEmpty()) {
                    val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)

                    // 根据 URL 特征设置 MIME 类型，帮助 ExoPlayer 选择正确的解析器
                    val lowerUrl = videoUrl.lowercase()
                    if (lowerUrl.contains(".m3u8") || lowerUrl.contains("format=m3u8")) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (lowerUrl.contains(".mp4") || lowerUrl.contains("format=mp4")
                        || lowerUrl.contains("video.twimg.com")) {
                        // video.twimg.com 的视频都是 MP4 格式
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                    }

                    setMediaItem(mediaItemBuilder.build())
                    prepare()
                    playWhenReady = true
                }
            }
    }

    // 页面退出或组件销毁时释放资源
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(false)
                    setShowRewindButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
