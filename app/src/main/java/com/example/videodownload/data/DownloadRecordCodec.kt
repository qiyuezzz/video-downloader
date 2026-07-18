package com.example.videodownload.data

import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.DownloadTask
import org.json.JSONArray
import org.json.JSONObject

/** 下载记录的 JSON 持久化格式，集中处理旧数据的兼容默认值。 */
object DownloadRecordCodec {
    fun encodeActiveTasks(tasks: List<DownloadTask>): String {
        val array = JSONArray()
        tasks.filter { it.state !is DownloadState.Success }.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("thumbnailUrl", task.thumbnailUrl ?: JSONObject.NULL)
                put("videoUrl", task.videoUrl)
                put("webpageUrl", task.webpageUrl)
                put("fileName", task.fileName)
                put("ext", task.ext)
                put("directoryUri", task.directoryUri)
                put("fileUri", task.fileUri)
                put("totalBytes", task.totalBytes)
                put("workId", task.workId)
            })
        }
        return array.toString()
    }

    fun decodeActiveTasks(json: String): List<DownloadTask> = decodeArray(json) { obj ->
        DownloadTask(
            id = obj.getString("id"),
            title = obj.getString("title"),
            thumbnailUrl = obj.nullableString("thumbnailUrl"),
            state = DownloadState.Idle,
            videoUrl = obj.getString("videoUrl"),
            webpageUrl = obj.optString("webpageUrl", ""),
            fileName = obj.optString("fileName", ""),
            ext = obj.optString("ext", "mp4"),
            directoryUri = obj.optString("directoryUri", ""),
            fileUri = obj.optString("fileUri", ""),
            totalBytes = obj.optLong("totalBytes", 0),
            workId = obj.optString("workId", ""),
        )
    }

    fun encodeHistory(items: List<DownloadHistoryItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL)
                put("fileName", item.fileName)
                put("fileUri", item.fileUri)
                put("videoUrl", item.videoUrl)
                put("webpageUrl", item.webpageUrl)
                put("timestamp", item.timestamp)
                put("platform", item.platform)
            })
        }
        return array.toString()
    }

    fun decodeHistory(json: String): List<DownloadHistoryItem> = decodeArray(json) { obj ->
        DownloadHistoryItem(
            id = obj.getString("id"),
            title = obj.getString("title"),
            thumbnailUrl = obj.nullableString("thumbnailUrl"),
            fileName = obj.getString("fileName"),
            fileUri = obj.getString("fileUri"),
            videoUrl = obj.optString("videoUrl", ""),
            webpageUrl = obj.optString("webpageUrl", ""),
            timestamp = obj.getLong("timestamp"),
            platform = obj.optString("platform", ""),
        )
    }

    private inline fun <T> decodeArray(json: String, decode: (JSONObject) -> T): List<T> =
        runCatching {
            val array = JSONArray(json)
            List(array.length()) { index -> decode(array.getJSONObject(index)) }
        }.getOrDefault(emptyList())

    private fun JSONObject.nullableString(key: String): String? =
        optString(key, "").takeUnless { it.isBlank() || it == "null" }
}
