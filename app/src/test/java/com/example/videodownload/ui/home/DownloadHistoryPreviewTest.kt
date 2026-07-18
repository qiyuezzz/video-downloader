package com.example.videodownload.ui.home

import com.example.videodownload.data.model.DownloadHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadHistoryPreviewTest {
    @Test
    fun `历史布局按列表双列三列循环切换`() {
        assertEquals(1, nextHistoryLayout(0))
        assertEquals(2, nextHistoryLayout(1))
        assertEquals(0, nextHistoryLayout(2))
    }

    @Test
    fun `扫描历史使用本地视频生成预览`() {
        val item = historyItem(thumbnailUrl = null)
        assertEquals(item.fileUri, historyPreviewModel(item))
    }

    @Test
    fun `优先使用 HTTPS 封面并为旧 HTTP 封面回退本地视频`() {
        assertEquals(
            "https://example.com/cover.jpg",
            historyPreviewModel(historyItem("https://example.com/cover.jpg")),
        )
        val oldItem = historyItem("http://example.com/cover.jpg")
        assertEquals(oldItem.fileUri, historyPreviewModel(oldItem))
    }

    private fun historyItem(thumbnailUrl: String?) = DownloadHistoryItem(
        id = "id",
        title = "视频",
        thumbnailUrl = thumbnailUrl,
        fileName = "video.mp4",
        fileUri = "content://downloads/video.mp4",
        videoUrl = "",
        webpageUrl = "",
        timestamp = 1L,
    )
}
