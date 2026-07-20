package com.example.videodownload.data

import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRecordCodecTest {

    private fun sampleTask(
        durationMillis: Long? = null,
        id: String = "task-1",
    ) = DownloadTask(
        id = id,
        title = "示例视频",
        thumbnailUrl = "https://example.com/thumb.jpg",
        state = DownloadState.Idle,
        videoUrl = "https://example.com/video.mp4",
        webpageUrl = "https://example.com/page",
        fileName = "示例视频",
        ext = "mp4",
        directoryUri = "content://dir",
        fileUri = "content://dir/示例视频.mp4",
        downloadedBytes = 1024L,
        totalBytes = 4096L,
        workId = "work-1",
        startedAtMillis = 1_700_000_000_000L,
        finishedAtMillis = 1_700_000_045_000L,
        durationMillis = durationMillis,
    )

    private fun sampleHistory(
        durationMillis: Long? = null,
        id: String = "hist-1",
    ) = DownloadHistoryItem(
        id = id,
        title = "历史视频",
        thumbnailUrl = "https://example.com/hist.jpg",
        fileName = "历史视频.mp4",
        fileUri = "content://dir/历史视频.mp4",
        videoUrl = "https://example.com/hist.mp4",
        webpageUrl = "https://example.com/hist",
        timestamp = 1_700_000_045_000L,
        platform = "Bilibili",
        durationMillis = durationMillis,
    )

    @Test
    fun `活跃任务 durationMillis 编解码对称`() {
        val task = sampleTask(durationMillis = 332_000L)

        val decoded = DownloadRecordCodec.decodeActiveTasks(
            DownloadRecordCodec.encodeActiveTasks(listOf(task)),
        ).single()

        assertEquals(task.durationMillis, decoded.durationMillis)
        assertEquals(task.id, decoded.id)
        assertEquals(task.title, decoded.title)
        assertEquals(task.startedAtMillis, decoded.startedAtMillis)
    }

    @Test
    fun `活跃任务 durationMillis 为 null 时解码仍为 null`() {
        val task = sampleTask(durationMillis = null)

        val decoded = DownloadRecordCodec.decodeActiveTasks(
            DownloadRecordCodec.encodeActiveTasks(listOf(task)),
        ).single()

        assertNull(decoded.durationMillis)
    }

    @Test
    fun `历史记录 durationMillis 编解码对称`() {
        val item = sampleHistory(durationMillis = 3_750_000L)

        val decoded = DownloadRecordCodec.decodeHistory(
            DownloadRecordCodec.encodeHistory(listOf(item)),
        ).single()

        assertEquals(item.durationMillis, decoded.durationMillis)
        assertEquals(item.platform, decoded.platform)
        assertEquals(item.timestamp, decoded.timestamp)
    }

    @Test
    fun `老数据缺少 durationMillis 字段时反序列化为 null 而非崩溃`() {
        // 模拟升级前的旧 JSON（无 durationMillis 字段）
        val legacyActiveJson = """[{
            "id":"old-task","title":"旧任务",
            "thumbnailUrl":"https://example.com/old.jpg",
            "videoUrl":"https://example.com/old.mp4",
            "webpageUrl":"https://example.com/old",
            "fileName":"旧任务","ext":"mp4",
            "directoryUri":"content://dir","fileUri":"content://dir/old.mp4",
            "downloadedBytes":0,"totalBytes":0,
            "workId":"","startedAtMillis":0,"finishedAtMillis":0
        }]"""

        val decodedTask = DownloadRecordCodec.decodeActiveTasks(legacyActiveJson).single()
        assertNull(decodedTask.durationMillis)
        assertEquals("旧任务", decodedTask.title)

        val legacyHistoryJson = """[{
            "id":"old-hist","title":"旧历史",
            "thumbnailUrl":"https://example.com/old.jpg",
            "fileName":"旧历史.mp4","fileUri":"content://dir/old.mp4",
            "videoUrl":"https://example.com/old.mp4",
            "webpageUrl":"https://example.com/old",
            "timestamp":1700000000000,"platform":"YouTube"
        }]"""

        val decodedItem = DownloadRecordCodec.decodeHistory(legacyHistoryJson).single()
        assertNull(decodedItem.durationMillis)
        assertEquals("YouTube", decodedItem.platform)
    }

    @Test
    fun `durationMillis 为 0 时视为无效并反序列化为 null`() {
        val task = sampleTask(durationMillis = 0L)

        val decoded = DownloadRecordCodec.decodeActiveTasks(
            DownloadRecordCodec.encodeActiveTasks(listOf(task)),
        ).single()

        assertNull(decoded.durationMillis)
    }

    @Test
    fun `Success 状态的活跃任务不会被编码`() {
        val task = sampleTask().copy(state = DownloadState.Success("done.mp4"))

        val json = DownloadRecordCodec.encodeActiveTasks(listOf(task))

        assertEquals("[]", json)
    }
}
