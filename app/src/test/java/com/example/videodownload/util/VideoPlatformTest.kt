package com.example.videodownload.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlatformTest {

    @Test
    fun `按来源链接返回平台文件夹`() {
        assertEquals("Bilibili", VideoPlatform.folderName("https://www.bilibili.com/video/BV1xx"))
        assertEquals("X", VideoPlatform.folderName("https://x.com/user/status/123"))
        assertEquals("Instagram", VideoPlatform.folderName("https://www.instagram.com/reel/abc/"))
        assertEquals("YouTube", VideoPlatform.folderName("https://youtu.be/abc"))
        assertEquals("其他", VideoPlatform.folderName("https://example.com/video"))
    }
}
