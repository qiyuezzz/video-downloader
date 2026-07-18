package com.example.videodownload.ui.home

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.videodownload.R
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.videodownload.util.NetworkConstants
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    videoUrl: String,
    modifier: Modifier = Modifier,
    webpageUrl: String = "",
) {
    val context = LocalContext.current
    val serverDeniedMessage = stringResource(R.string.preview_server_denied)
    val parseFailedMessage = stringResource(R.string.preview_parse_failed)
    val playbackErrorPrefix = stringResource(R.string.preview_playback_error_prefix)
    val previewFailedPrefix = stringResource(R.string.preview_failed_prefix)

    // 当 URL 或网页来源变化时，重新构建播放器以确保 Header 正确
    val exoPlayer = remember(
        videoUrl,
        webpageUrl,
        serverDeniedMessage,
        parseFailedMessage,
        playbackErrorPrefix,
        previewFailedPrefix,
    ) {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val isBilibili = videoUrl.contains("bilivideo.com") || webpageUrl.contains("bilibili.com")

        val userAgent = if (isBilibili) NetworkConstants.USER_AGENT_DESKTOP else NetworkConstants.USER_AGENT

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)

        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "*/*"
        headers["Connection"] = "keep-alive"

        if (webpageUrl.isNotEmpty()) {
            val referer = when {
                isBilibili -> NetworkConstants.BILIBILI_BASE_URL
                webpageUrl.contains("x.com") || webpageUrl.contains("twitter.com") -> "https://x.com/"
                else -> webpageUrl
            }
            headers["Referer"] = referer
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
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> serverDeniedMessage
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> parseFailedMessage
                            else -> playbackErrorPrefix + error.errorCodeName
                        }
                        Toast.makeText(
                            context,
                            previewFailedPrefix + message,
                            Toast.LENGTH_SHORT,
                        ).show()
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
