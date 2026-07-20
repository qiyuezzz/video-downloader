package com.example.videodownload.util

import java.util.Locale

/**
 * 视频时长格式化工具。
 */
object DurationFormatter {

    /**
     * 将毫秒时长格式化为可读字符串：
     * - 不足 1 小时：`MM:SS`（如 `05:32`）
     * - 超过 1 小时：`H:MM:SS`（如 `1:02:30`）
     * - 非正数或为空：返回 null，由调用方决定是否展示。
     */
    fun format(millis: Long?): String? {
        if (millis == null || millis <= 0) return null
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
