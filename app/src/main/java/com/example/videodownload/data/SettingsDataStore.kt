package com.example.videodownload.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置数据存储，管理保存路径和画质偏好
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val SAVE_LOCATION_KEY = stringPreferencesKey("save_location")
        private val PREFERRED_QUALITY_KEY = stringPreferencesKey("preferred_quality")
        private val DOWNLOAD_HISTORY_KEY = stringPreferencesKey("download_history")
        private val ACTIVE_DOWNLOADS_KEY = stringPreferencesKey("active_downloads")
        private val HISTORY_LAYOUT_KEY = intPreferencesKey("history_layout")

        const val QUALITY_BEST = "best"
        const val QUALITY_720P = "720p"
        const val QUALITY_480P = "480p"

        const val HISTORY_LAYOUT_LIST = 0
        const val HISTORY_LAYOUT_GRID = 1
        const val HISTORY_LAYOUT_COMPACT_GRID = 2
    }

    /** 保存目录的 URI 字符串 */
    val saveLocation: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SAVE_LOCATION_KEY]
    }

    /** 首选画质 */
    val preferredQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREFERRED_QUALITY_KEY] ?: QUALITY_BEST
    }

    /** 历史记录 JSON 字符串 */
    val downloadHistory: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_HISTORY_KEY]
    }

    /** 活跃下载任务 JSON 字符串 */
    val activeDownloads: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_DOWNLOADS_KEY]
    }

    /** 本地视频页布局：列表、双列或三列。 */
    val historyLayout: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[HISTORY_LAYOUT_KEY]?.takeIf { it in HISTORY_LAYOUT_LIST..HISTORY_LAYOUT_COMPACT_GRID }
            ?: HISTORY_LAYOUT_LIST
    }

    /** 更新历史记录 */
    suspend fun saveDownloadHistory(json: String) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_HISTORY_KEY] = json
        }
    }

    /** 更新活跃下载任务 */
    suspend fun saveActiveDownloads(json: String) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_DOWNLOADS_KEY] = json
        }
    }

    /** 更新保存目录 */
    suspend fun setSaveLocation(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[SAVE_LOCATION_KEY] = uri
        }
    }

    /** 更新首选画质 */
    suspend fun setPreferredQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[PREFERRED_QUALITY_KEY] = quality
        }
    }

    suspend fun setHistoryLayout(layout: Int) {
        context.dataStore.edit { prefs ->
            prefs[HISTORY_LAYOUT_KEY] = layout.coerceIn(
                HISTORY_LAYOUT_LIST,
                HISTORY_LAYOUT_COMPACT_GRID,
            )
        }
    }
}
