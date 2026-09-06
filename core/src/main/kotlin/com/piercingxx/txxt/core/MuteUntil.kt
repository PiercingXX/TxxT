package com.piercingxx.txxt.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wall-clock mute-until presets.
 *
 * Forever-mute is a separate flag ([ConversationFlags.isMuted]). These
 * presets compute an expiry instant so notifications stay off until then
 * and resume afterwards without a second tap. Pure Kotlin, zero
 * `android.*` imports.
 */
object MuteUntil {

    enum class Preset {
        ONE_HOUR,
        EIGHT_HOURS,
        TONIGHT,
        MONDAY,
    }

    /** Local hour used for "tonight" (21:00). */
    const val TONIGHT_HOUR = 21

    /**
     * Expiry epoch millis for [preset] relative to [nowMillis] in [zone].
     *
     * Tonight is today at 21:00, or tomorrow 21:00 when that time has
     * already passed. Monday is the next Monday at local midnight — if
     * today is already Monday, that means next week.
     */
    fun expiryMillis(
        preset: Preset,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val expiry = when (preset) {
            Preset.ONE_HOUR -> zoned.plusHours(1)
            Preset.EIGHT_HOURS -> zoned.plusHours(8)
            Preset.TONIGHT -> {
                var target = zoned.toLocalDate()
                    .atTime(LocalTime.of(TONIGHT_HOUR, 0))
                    .atZone(zone)
                if (!zoned.isBefore(target)) target = target.plusDays(1)
                target
            }
            Preset.MONDAY -> {
                val today = zoned.toLocalDate()
                val days = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
                val next = if (days == 0) today.plusWeeks(1) else today.plusDays(days.toLong())
                next.atStartOfDay(zone)
            }
        }
        return expiry.toInstant().toEpochMilli()
    }

    /**
     * Whether a time-based mute is still active at [nowMillis].
     * [mutedUntilMillis] of `0` means no time-based mute.
     */
    fun isActive(mutedUntilMillis: Long, nowMillis: Long): Boolean =
        mutedUntilMillis > 0L && mutedUntilMillis > nowMillis
}
