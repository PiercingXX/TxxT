package com.piercingxx.txxt.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MuteUntilTest {

    private val utc = ZoneOffset.UTC

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    @Test
    fun `one hour is now plus 3600000`() {
        val now = millis(2026, 3, 4, 12, 0)
        assertEquals(now + 3_600_000L, MuteUntil.expiryMillis(MuteUntil.Preset.ONE_HOUR, now, utc))
    }

    @Test
    fun `eight hours is now plus eight hours`() {
        val now = millis(2026, 3, 4, 12, 0)
        assertEquals(now + 8 * 3_600_000L, MuteUntil.expiryMillis(MuteUntil.Preset.EIGHT_HOURS, now, utc))
    }

    @Test
    fun `tonight before 21 is today at 21`() {
        val now = millis(2026, 3, 4, 18, 0)
        val tonight = millis(2026, 3, 4, MuteUntil.TONIGHT_HOUR, 0)
        assertEquals(tonight, MuteUntil.expiryMillis(MuteUntil.Preset.TONIGHT, now, utc))
    }

    @Test
    fun `tonight at or after 21 is tomorrow at 21`() {
        val now = millis(2026, 3, 4, 21, 0)
        val tomorrow = millis(2026, 3, 5, MuteUntil.TONIGHT_HOUR, 0)
        assertEquals(tomorrow, MuteUntil.expiryMillis(MuteUntil.Preset.TONIGHT, now, utc))
    }

    @Test
    fun `Monday from Wednesday is the coming Monday midnight`() {
        // 2026-03-04 is a Wednesday.
        val now = millis(2026, 3, 4, 15, 0)
        val monday = millis(2026, 3, 9, 0, 0)
        assertEquals(monday, MuteUntil.expiryMillis(MuteUntil.Preset.MONDAY, now, utc))
    }

    @Test
    fun `Monday from Monday is next week`() {
        // 2026-03-09 is a Monday.
        val now = millis(2026, 3, 9, 9, 0)
        val next = millis(2026, 3, 16, 0, 0)
        assertEquals(next, MuteUntil.expiryMillis(MuteUntil.Preset.MONDAY, now, utc))
    }

    @Test
    fun `time-based mute is active only while expiry is in the future`() {
        assertFalse(MuteUntil.isActive(mutedUntilMillis = 0L, nowMillis = 1_000L))
        assertTrue(MuteUntil.isActive(mutedUntilMillis = 2_000L, nowMillis = 1_000L))
        assertFalse(MuteUntil.isActive(mutedUntilMillis = 1_000L, nowMillis = 1_000L))
        assertFalse(MuteUntil.isActive(mutedUntilMillis = 999L, nowMillis = 1_000L))
    }
}
