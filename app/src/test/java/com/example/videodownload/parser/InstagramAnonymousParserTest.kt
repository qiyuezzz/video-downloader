package com.example.videodownload.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramAnonymousParserTest {

    @Test
    fun `提取 Reel 和帖子路径`() {
        assertEquals(
            "reel/DagrcFjJSxo",
            InstagramAnonymousParser.extractPostPath(
                "https://www.instagram.com/reel/DagrcFjJSxo/?igsh=test"
            ),
        )
        assertEquals(
            "p/ABC_123-x",
            InstagramAnonymousParser.extractPostPath("https://instagram.com/user/p/ABC_123-x/"),
        )
        assertNull(InstagramAnonymousParser.extractPostPath("https://instagram.com/example/"))
    }

    @Test
    fun `解析 Open Graph 视频信息并解码实体`() {
        val metadata = InstagramAnonymousParser.parseOpenGraph(
            """
            <meta content="创作者 &amp; 标题" property="og:title" />
            <meta property='og:video:secure_url' content='https://media.example/video.mp4?a=1&amp;b=2' />
            <meta property="og:video:height" content="1280" />
            """.trimIndent()
        )

        assertEquals("创作者 & 标题", metadata["og:title"])
        assertEquals("https://media.example/video.mp4?a=1&b=2", metadata["og:video:secure_url"])
        assertEquals("1280", metadata["og:video:height"])
    }
}
