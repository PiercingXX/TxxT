package com.piercingxx.txxt.service

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The dialer's daily window, copied as a pure function so TxxT does not
 * depend on xx-dialer's modules. Inclusive start, exclusive end; `end <=
 * start` wraps midnight. Bit 0 = Monday … bit 6 = Sunday. Matches
 * `com.piercingxx.xxdialer.core.Window`.
 */
object BusinessSchedule {

    const val ALL_DAYS = 0b1111111
    const val DEFAULT_START_MINUTE = 9 * 60
    const val DEFAULT_END_MINUTE = 19 * 60
    private const val MINUTES_PER_DAY = 24 * 60

    fun contains(
        now: LocalDateTime,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        daysMask: Int,
    ): Boolean {
        if (startMinuteOfDay !in 0 until MINUTES_PER_DAY) return false
        if (endMinuteOfDay !in 0 until MINUTES_PER_DAY) return false
        if (daysMask !in 1..ALL_DAYS) return false
        if (daysMask and arrivalBit(now.dayOfWeek) == 0) return false
        val t = now.toLocalTime()
        val start = timeAt(startMinuteOfDay)
        val end = timeAt(endMinuteOfDay)
        return if (end <= start) t >= start || t < end else t >= start && t < end
    }

    /**
     * True when a Business-tier sender should not ring the phone: they are
     * on the tier, not starred, and [now] is outside the window. A missing
     * snapshot (dialer uninstalled / provider refused) fails open — notify.
     */
    fun shouldQuiet(
        snapshot: DialerBusinessTier.Snapshot?,
        lookupKey: String?,
        starred: Boolean,
        now: LocalDateTime,
    ): Boolean {
        if (starred) return false
        if (snapshot == null) return false
        if (lookupKey.isNullOrEmpty() || lookupKey !in snapshot.keys) return false
        return !contains(now, snapshot.startMinute, snapshot.endMinute, snapshot.daysMask)
    }

    private fun arrivalBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    private fun timeAt(minuteOfDay: Int): LocalTime =
        LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}
