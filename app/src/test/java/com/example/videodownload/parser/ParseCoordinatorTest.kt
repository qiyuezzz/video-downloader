package com.example.videodownload.parser

import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseCoordinatorTest {
    @Test
    fun parse_usesSupportedSpecializedParserBeforeFallback() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = ParseCoordinator(
            specializedParsers = listOf(
                FakeParser(supported = false, result = video("unused"), name = "unsupported", calls),
                FakeParser(supported = true, result = video("specialized"), name = "specialized", calls),
            ),
            fallbackParser = FakeParser(true, video("fallback"), "fallback", calls),
        )

        assertEquals("specialized", coordinator.parse("https://example.com")?.title)
        assertEquals(listOf("specialized"), calls)
    }

    @Test
    fun parse_fallsBackWhenSpecializedParserReturnsNoFormats() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = ParseCoordinator(
            specializedParsers = listOf(
                FakeParser(true, video("empty", formats = emptyList()), "specialized", calls)
            ),
            fallbackParser = FakeParser(true, video("fallback"), "fallback", calls),
        )

        assertEquals("fallback", coordinator.parse("https://example.com")?.title)
        assertEquals(listOf("specialized", "fallback"), calls)
    }

    @Test
    fun parse_returnsNullWhenAllParsersFail() = runBlocking {
        val coordinator = ParseCoordinator(
            specializedParsers = emptyList(),
            fallbackParser = FakeParser(true, null, "fallback", mutableListOf()),
        )

        assertNull(coordinator.parse("https://example.com"))
    }

    private class FakeParser(
        private val supported: Boolean,
        private val result: VideoInfo?,
        private val name: String,
        private val calls: MutableList<String>,
    ) : VideoParser {
        override fun supports(url: String): Boolean = supported

        override suspend fun parse(url: String): VideoInfo? {
            calls += name
            return result
        }
    }

    private companion object {
        fun video(title: String, formats: List<VideoFormat> = listOf(format)) = VideoInfo(
            title = title,
            thumbnailUrl = null,
            formats = formats,
            webpageUrl = "https://example.com",
        )

        val format = VideoFormat(
            formatId = "test",
            quality = "720p",
            ext = "mp4",
            filesize = null,
            url = "https://example.com/video.mp4",
        )
    }
}
