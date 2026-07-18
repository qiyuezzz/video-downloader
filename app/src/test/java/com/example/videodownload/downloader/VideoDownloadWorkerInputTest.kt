package com.example.videodownload.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDownloadWorkerInputTest {
    @Test
    fun `后台下载参数可完整序列化`() {
        val input = VideoDownloadWorker.DownloadInput(
            videoUrl = "https://example.com/video.mp4",
            fileName = "测试视频",
            ext = "mp4",
            directoryUri = "content://downloads/tree/root",
            platformFolder = "其他",
            referer = "https://example.com/post/1",
            existingFileUri = "content://downloads/document/video",
            downloadedBytes = 256,
            totalBytes = 1024,
        )

        assertEquals(input, VideoDownloadWorker.DownloadInput.from(input.toData()))
    }

    @Test
    fun `可选参数为空时仍可恢复任务`() {
        val input = VideoDownloadWorker.DownloadInput(
            videoUrl = "https://example.com/video.mp4",
            fileName = "测试视频",
            ext = "mp4",
            directoryUri = "content://downloads/tree/root",
            platformFolder = null,
            referer = null,
            existingFileUri = null,
            downloadedBytes = 0,
            totalBytes = 0,
        )

        val restored = VideoDownloadWorker.DownloadInput.from(input.toData())

        assertNull(restored?.platformFolder)
        assertNull(restored?.existingFileUri)
    }
}
