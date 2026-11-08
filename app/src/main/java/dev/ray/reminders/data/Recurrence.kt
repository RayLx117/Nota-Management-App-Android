package dev.ray.reminders.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Recurrence {

    /** The first occurrence of [r] strictly after [after], or null if there is none. */
    fun next(r: Reminder, after: ZonedDateTime): ZonedDateTime? {
        val zone = after.zone
        val start = LocalDate.ofEpochDay(r.startEpochDay)
        // All day starts at 5 AM (for early birds) and runs until midnight.
        val time = if (r.allDay) LocalTime.of(5, 0) else LocalTime.of(r.hour, r.minute)
        val n = r.interval.coerceAtLeast(1).toLong()
        val afterDate = after.toLocalDate()
        fun at(d: LocalDate): ZonedDateTime = ZonedDateTime.of(d, time, zone)

        return when (r.repeat) {
            RepeatType.ONCE -> at(start).takeIf { it.isAfter(after) }

            RepeatType.DAILY -> stepFrom(ChronoUnit.DAYS.between(start, afterDate), n, after) {
                at(start.plusDays(it))
            }

            // plusMonths/plusYears clamp to the end of shorter months (31st -> 30th/28th,
            // Feb 29 -> Feb 28), and since we always add to the start date the day recovers.
            RepeatType.MONTHLY -> stepFrom(ChronoUnit.MONTHS.between(start, afterDate), n, after) {
                at(start.plusMonths(it))
            }

            RepeatType.YEARLY -> stepFrom(ChronoUnit.YEARS.between(start, afterDate), n, after) {
                at(start.plusYears(it))
            }

            RepeatType.WEEKLY -> {
                val days = r.weekdays.ifEmpty { setOf(start.dayOfWeek.value) }
                val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                var d = maxOf(start, afterDate)
                // Any valid occurrence is at most n weeks + 1 week away.
                repeat((7 * n + 8).toInt()) {
                    val week = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weeksIn = ChronoUnit.WEEKS.between(startWeek, week)
                    if (d.dayOfWeek.value in days && weeksIn % n == 0L) {
                        val t = at(d)
                        if (t.isAfter(after)) return t
                    }
                    d = d.plusDays(1)
                }
                null
            }
        }
    }

    /**
     * Walks occurrences k*n units after the start, beginning just before the estimated
     * position of [after], and returns the first one after it.
     */
    private inline fun stepFrom(
        unitsElapsed: Long,
        n: Long,
        after: ZonedDateTime,
        occurrence: (Long) -> ZonedDateTime,
    ): ZonedDateTime {
        var k = maxOf(0L, unitsElapsed / n - 1)
        while (true) {
            val t = occurrence(k * n)
            if (t.isAfter(after)) return t
            k++
        }
    }
}
