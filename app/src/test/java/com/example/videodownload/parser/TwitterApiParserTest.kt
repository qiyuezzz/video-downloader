package com.example.videodownload.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitterApiParserTest {
    @Test
    fun `从 X 链接提取真实用户名和推文 ID`() {
        assertEquals(
            TwitterApiParser.TweetReference("OpenAI", "2074389609409318985"),
            TwitterApiParser.extractTweetReference(
                "https://x.com/OpenAI/status/2074389609409318985?s=20"
            ),
        )
    }

    @Test
    fun `支持 Twitter 移动端链接`() {
        val parser = TwitterApiParser()
        assertTrue(parser.supports("https://mobile.twitter.com/example/status/123456"))
    }

    @Test
    fun `支持无用户名链接并拒绝伪造域名`() {
        val parser = TwitterApiParser()
        assertFalse(parser.supports("https://evil.example/?next=x.com/user/status/123"))
        assertTrue(parser.supports("https://x.com/i/status/123"))
        assertEquals(
            TwitterApiParser.TweetReference(null, "123"),
            TwitterApiParser.extractTweetReference("https://x.com/i/status/123"),
        )
    }
}
