package com.example.videodownload.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilibiliNativeParserTest {
    @Test
    fun `将 B 站 HTTP 封面升级为 HTTPS`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/cover.jpg",
            BilibiliNativeParser.normalizeThumbnailUrl(
                "http://i0.hdslb.com/bfs/archive/cover.jpg"
            ),
        )
    }

    @Test
    fun `支持协议相对封面并拒绝无效地址`() {
        assertEquals(
            "https://i1.hdslb.com/bfs/archive/cover.jpg",
            BilibiliNativeParser.normalizeThumbnailUrl(
                "//i1.hdslb.com/bfs/archive/cover.jpg"
            ),
        )
        assertNull(BilibiliNativeParser.normalizeThumbnailUrl(""))
        assertNull(BilibiliNativeParser.normalizeThumbnailUrl("file:///cover.jpg"))
    }
}
