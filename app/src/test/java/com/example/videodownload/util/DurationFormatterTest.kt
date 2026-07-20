package com.example.videodownload.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun `null 或非正数返回 null`() {
        assertNull(DurationFormatter.format(null))
        assertNull(DurationFormatter.format(0L))
        assertNull(DurationFormatter.format(-1L))
    }

    @Test
    fun `不足一小时显示为 MM_SS`() {
        assertEquals("00:00", DurationFormatter.format(0L.coerceAtLeast(1)))
        assertEquals("00:05", DurationFormatter.format(5_000L))
        assertEquals("05:32", DurationFormatter.format(332_000L))
        assertEquals("59:59", DurationFormatter.format(3_599_000L))
    }

    @Test
    fun `超过一小时显示为 H_MM_SS`() {
        assertEquals("1:00:00", DurationFormatter.format(3_600_000L))
        assertEquals("1:02:30", DurationFormatter.format(3_750_000L))
        assertEquals("2:00:00", DurationFormatter.format(7_200_000L))
    }

    @Test
    fun `毫秒不足一秒的部分被丢弃`() {
        assertEquals("00:01", DurationFormatter.format(1_999L))
    }
}
