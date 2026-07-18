package com.example.videodownload.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRestoreTest {
    @Test
    fun `根据 MIME 类型或扩展名识别可恢复的视频`() {
        assertTrue(isRecoverableVideoFile("下载文件", "video/mp4"))
        assertTrue(isRecoverableVideoFile("片段.WEBM", null))
        assertTrue(isRecoverableVideoFile("录像.mkv", "application/octet-stream"))
    }

    @Test
    fun `扫描时忽略非视频文件`() {
        assertFalse(isRecoverableVideoFile("封面.jpg", "image/jpeg"))
        assertFalse(isRecoverableVideoFile("说明.txt", "text/plain"))
        assertFalse(isRecoverableVideoFile(null, null))
    }
}
