package com.example.videodownload.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun `未知总大小返回不确定进度`() {
        assertEquals(-1, DownloadProgress.percent(1024, 0))
    }

    @Test
    fun `未完成任务不会提前显示百分之百`() {
        assertEquals(99, DownloadProgress.percent(100, 100))
    }

    @Test
    fun `完成后允许显示百分之百`() {
        assertEquals(100, DownloadProgress.percent(100, 100, completed = true))
    }

    @Test
    fun `按真实字节计算恢复进度`() {
        assertEquals(25, DownloadProgress.percent(250, 1000))
    }
}
