package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BusinessScheduleTest {

    private val thursdayNoon = LocalDateTime.of(2026, 8, 20, 12, 0)
    private val thursdayEvening = LocalDateTime.of(2026, 8, 20, 20, 0)
    private val defaultWindowStart = BusinessSchedule.DEFAULT_START_MINUTE
    private val defaultWindowEnd = BusinessSchedule.DEFAULT_END_MINUTE
    private val allDays = BusinessSchedule.ALL_DAYS

    @Test
    fun `default business window is 09 to 19 inclusive-exclusive`() {
        val day = java.time.LocalDate.of(2026, 8, 20)
        assertFalse(
            BusinessSchedule.contains(day.atTime(8, 59, 59), defaultWindowStart, defaultWindowEnd, allDays),
        )
        assertTrue(
            BusinessSchedule.contains(day.atTime(9, 0, 0), defaultWindowStart, defaultWindowEnd, allDays),
        )
        assertTrue(
            BusinessSchedule.contains(day.atTime(18, 59, 59), defaultWindowStart, defaultWindowEnd, allDays),
        )
        assertFalse(
            BusinessSchedule.contains(day.atTime(19, 0, 0), defaultWindowStart, defaultWindowEnd, allDays),
        )
    }

    @Test
    fun `monday bit is bit zero`() {
        val mondayOnly = 1 shl 0
        val mondayNoon = LocalDateTime.of(2026, 8, 24, 12, 0)
        val sundayNoon = LocalDateTime.of(2026, 8, 23, 12, 0)
        assertTrue(BusinessSchedule.contains(mondayNoon, 540, 1140, mondayOnly))
        assertFalse(BusinessSchedule.contains(sundayNoon, 540, 1140, mondayOnly))
    }

    @Test
    fun `missing snapshot fails open — never quiet`() {
        assertFalse(
            BusinessSchedule.shouldQuiet(
                snapshot = null,
                lookupKey = "abc",
                starred = false,
                now = thursdayEvening,
            ),
        )
    }

    @Test
    fun `starred beats business outside the window`() {
        val snap = DialerBusinessTier.Snapshot(540, 1140, allDays, setOf("dentist"))
        assertFalse(
            BusinessSchedule.shouldQuiet(snap, "dentist", starred = true, now = thursdayEvening),
        )
    }

    @Test
    fun `business sender outside the window is quiet`() {
        val snap = DialerBusinessTier.Snapshot(540, 1140, allDays, setOf("dentist"))
        assertTrue(
            BusinessSchedule.shouldQuiet(snap, "dentist", starred = false, now = thursdayEvening),
        )
        assertFalse(
            BusinessSchedule.shouldQuiet(snap, "dentist", starred = false, now = thursdayNoon),
        )
    }

    @Test
    fun `saved but not business is never quieted by the window`() {
        val snap = DialerBusinessTier.Snapshot(540, 1140, allDays, setOf("dentist"))
        assertFalse(
            BusinessSchedule.shouldQuiet(snap, "neighbour", starred = false, now = thursdayEvening),
        )
        assertFalse(
            BusinessSchedule.shouldQuiet(snap, null, starred = false, now = thursdayEvening),
        )
    }
}
