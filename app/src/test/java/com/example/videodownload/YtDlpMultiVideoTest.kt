package com.example.videodownload

import com.example.videodownload.parser.YtDlpParser
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class YtDlpMultiVideoTest {
    @Test
    fun testMultiVideo() = runBlocking {
        // We cannot easily init YoutubeDL without an application context in unit tests
        // So this might fail if youtubedl is not initialized.
    }
}
