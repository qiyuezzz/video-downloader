package com.example.videodownload.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {
    @Test
    fun extract_returnsFirstUrlFromShareText() {
        val normalizer = UrlNormalizer()

        assertEquals(
            "https://x.com/user/status/123?ref=share",
            normalizer.extract("分享视频 https://x.com/user/status/123?ref=share 立即查看"),
        )
        assertNull(normalizer.extract("没有链接的文本"))
    }

    @Test
    fun `extract 截断无分隔符拼接的第二个链接`() {
        val normalizer = UrlNormalizer()

        assertEquals(
            "https://x.com/i/status/2074058517439480151",
            normalizer.extract(
                "https://x.com/i/status/2074058517439480151" +
                    "https://x.com/i/status/2074058517439480151"
            ),
        )
    }

    @Test
    fun normalize_removesTrackingParameters() = runBlocking {
        val normalizer = UrlNormalizer()

        assertEquals(
            "https://twitter.com/user/status/123",
            normalizer.normalize("https://twitter.com/user/status/123?s=20"),
        )
    }

    @Test
    fun normalize_resolvesB23AndCanonicalizesBilibiliUrl() = runBlocking {
        val normalizer = UrlNormalizer(
            shortLinkResolver = ShortLinkResolver {
                "https://m.bilibili.com/video/BV1abc123?share_source=copy"
            }
        )

        assertEquals(
            "https://www.bilibili.com/video/BV1abc123",
            normalizer.normalize("https://b23.tv/example"),
        )
    }
}
