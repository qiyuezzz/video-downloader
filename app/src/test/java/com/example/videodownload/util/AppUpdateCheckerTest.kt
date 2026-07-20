package com.example.videodownload.util

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    private val dummyClient = OkHttpClient()

    private fun checker(returningJson: String): AppUpdateChecker =
        AppUpdateChecker(client = dummyClient, fetcher = { _, _ -> returningJson })

    @Test
    fun `compareVersions 正确比较三位版本号`() {
        val checker = AppUpdateChecker()
        assertTrue(checker.compareVersions("1.1.2", "1.2.0") < 0)
        assertTrue(checker.compareVersions("1.2.0", "1.1.2") > 0)
        assertEquals(0, checker.compareVersions("1.1.2", "1.1.2"))
    }

    @Test
    fun `compareVersions 去除 v 前缀后比较`() {
        val checker = AppUpdateChecker()
        assertEquals(0, checker.compareVersions("v1.1.2", "1.1.2"))
        assertTrue(checker.compareVersions("v1.0.0", "v2.0.0") < 0)
    }

    @Test
    fun `compareVersions 段数不一致时缺失段按零处理`() {
        val checker = AppUpdateChecker()
        assertTrue(checker.compareVersions("1.0", "1.0.1") < 0)
        assertTrue(checker.compareVersions("2", "1.9.9") > 0)
    }

    @Test
    fun `compareVersions 非数字段容错为 0`() {
        val checker = AppUpdateChecker()
        assertEquals(0, checker.compareVersions("1.0.0", "1.0.abc"))
    }

    @Test
    fun `fetchLatestRelease 解析 tag 与 APK 地址`() = runBlocking {
        val json = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/qiyuezzz/video-downloader/releases/tag/v1.2.0",
              "body": "feat: 新功能\nfix: 修复",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "VideoDownloader_1.2.0.apk",
                  "browser_download_url": "https://github.com/qiyuezzz/video-downloader/releases/download/v1.2.0/VideoDownloader_1.2.0.apk"
                }
              ]
            }
        """.trimIndent()

        val release = checker(json).fetchLatestRelease()
        assertNotNull(release)
        assertEquals("v1.2.0", release!!.tagName)
        assertEquals("1.2.0", release.versionName)
        assertEquals(
            "https://github.com/qiyuezzz/video-downloader/releases/download/v1.2.0/VideoDownloader_1.2.0.apk",
            release.apkUrl,
        )
        assertTrue(release.releaseNotes.contains("feat: 新功能"))
    }

    @Test
    fun `fetchLatestRelease 草稿版本被过滤返回 null`() = runBlocking {
        val json = """{"tag_name":"v9.9.9","html_url":"https://x","body":"","draft":true,"prerelease":false,"assets":[]}"""
        assertNull(checker(json).fetchLatestRelease())
    }

    @Test
    fun `fetchLatestRelease 预发布版本被过滤返回 null`() = runBlocking {
        val json = """{"tag_name":"v9.9.9","html_url":"https://x","body":"","draft":false,"prerelease":true,"assets":[]}"""
        assertNull(checker(json).fetchLatestRelease())
    }

    @Test
    fun `fetchLatestRelease 无 APK 资源时 apkUrl 为 null`() = runBlocking {
        val json = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://github.com/qiyuezzz/video-downloader/releases/tag/v1.2.0",
              "body": "",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "Source.zip", "browser_download_url": "https://x/Source.zip"}
              ]
            }
        """.trimIndent()
        val release = checker(json).fetchLatestRelease()
        assertNotNull(release)
        assertNull(release!!.apkUrl)
    }

    @Test
    fun `fetchLatestRelease 多资源时选取 APK 而非首个`() = runBlocking {
        val json = """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://x",
              "body": "",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "checksums.txt", "browser_download_url": "https://x/checksums.txt"},
                {"name": "VideoDownloader_1.2.0.apk", "browser_download_url": "https://x/app.apk"}
              ]
            }
        """.trimIndent()
        val release = checker(json).fetchLatestRelease()
        assertEquals("https://x/app.apk", release?.apkUrl)
    }

    @Test
    fun `fetchLatestRelease tag_name 缺失时返回 null`() = runBlocking {
        val json = """{"html_url":"https://x","body":"","assets":[]}"""
        assertNull(checker(json).fetchLatestRelease())
    }
}
