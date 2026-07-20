package com.example.videodownload.ui.home

import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo

sealed class ParseState {
    data object Idle : ParseState()
    data object Loading : ParseState()
    data class Success(val videoInfo: VideoInfo) : ParseState()
    data class Error(val message: String) : ParseState()
}

sealed class HomeEvent {
    data object ShowDownloadOptions : HomeEvent()
    data class ShowDuplicateConfirm(
        val videoInfo: VideoInfo,
        val formats: List<VideoFormat>,
    ) : HomeEvent()
    /** 非 WiFi 环境下发起新下载时的确认。 */
    data class ShowWifiConfirm(
        val videoInfo: VideoInfo,
        val formats: List<VideoFormat>,
    ) : HomeEvent()
    /** 非 WiFi 环境下恢复单个下载任务时的确认。 */
    data class ShowWifiResumeConfirm(val taskId: String) : HomeEvent()
    /** 非 WiFi 环境下恢复全部下载任务时的确认。 */
    data object ShowWifiResumeAllConfirm : HomeEvent()
}
