package dev.ray.reminders.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceTest {
    private val zone = ZoneId.of("America/New_York")

    private fun r(
        repeat: RepeatType, start: String, hour: Int = 9, minute: Int = 0,
        interval: Int = 1, weekdays: Set<Int> = emptySet(),
    ) = Reminder(
        id = 1, title = "t", repeat = repeat, interval = interval,
        startEpochDay = LocalDate.parse(start).toEpochDay(), hour = hour, minute = minute, weekdays = weekdays,
    )

    private fun at(s: String) = ZonedDateTime.of(LocalDateTime.parse(s), zone)
    private fun next(rem: Reminder, after: String) = Recurrence.next(rem, at(after))?.toLocalDateTime()?.toString()

    @Test fun once() {
        val rem = r(RepeatType.ONCE, "2026-11-10")
        assertEquals("2026-11-10T09:00", next(rem, "2026-11-06T12:00"))
        assertNull(next(rem, "2026-11-10T09:00"))
    }

    @Test fun daily() {
        val rem = r(RepeatType.DAILY, "2026-11-01")
        assertEquals("2026-11-06T09:00", next(rem, "2026-11-06T08:59"))
        assertEquals("2026-11-07T09:00", next(rem, "2026-11-06T09:00"))
        assertEquals("2026-11-01T09:00", next(rem, "2026-10-01T00:00"))
    }

    @Test fun everyThreeDays() {
        val rem = r(RepeatType.DAILY, "2026-11-01", interval = 3)
        assertEquals("2026-11-07T09:00", next(rem, "2026-11-04T09:00"))
        assertEquals("2026-11-04T09:00", next(rem, "2026-11-02T10:00"))
    }

    @Test fun weekly() {
        // 2026-11-02 is a Monday. Mon/Wed/Fri.
        val rem = r(RepeatType.WEEKLY, "2026-11-02", weekdays = setOf(1, 3, 5))
        assertEquals("2026-11-04T09:00", next(rem, "2026-11-02T09:00"))
        assertEquals("2026-11-09T09:00", next(rem, "2026-11-06T10:00"))
    }

    @Test fun everyOtherWeek() {
        val rem = r(RepeatType.WEEKLY, "2026-11-02", interval = 2, weekdays = setOf(2))
        assertEquals("2026-11-03T09:00", next(rem, "2026-11-01T00:00"))
        assertEquals("2026-11-17T09:00", next(rem, "2026-11-03T10:00"))
        assertEquals("2026-12-01T09:00", next(rem, "2026-11-17T10:00"))
    }

    @Test fun weeklyStartMidWeek() {
        // Start Thursday 2026-11-05, Mondays only: first is 2026-11-09.
        val rem = r(RepeatType.WEEKLY, "2026-11-05", weekdays = setOf(1))
        assertEquals("2026-11-09T09:00", next(rem, "2026-11-01T00:00"))
    }

    @Test fun monthlyClampsToShortMonths() {
        val rem = r(RepeatType.MONTHLY, "2026-01-31")
        assertEquals("2026-02-28T09:00", next(rem, "2026-01-31T10:00"))
        assertEquals("2026-03-31T09:00", next(rem, "2026-02-28T10:00"))
        assertEquals("2026-04-30T09:00", next(rem, "2026-04-01T00:00"))
    }

    @Test fun everyThreeMonths() {
        val rem = r(RepeatType.MONTHLY, "2026-01-15", interval = 3)
        assertEquals("2026-10-15T09:00", next(rem, "2026-07-15T09:00"))
    }

    @Test fun yearlyLeapDay() {
        val rem = r(RepeatType.YEARLY, "2028-02-29")
        assertEquals("2029-02-28T09:00", next(rem, "2028-03-01T00:00"))
        assertEquals("2032-02-29T09:00", next(rem, "2031-03-01T00:00"))
    }

    @Test fun dstGap() {
        // 2:30 AM doesn't exist on 2027-03-14 in New York; it shifts forward to 3:30.
        val rem = r(RepeatType.DAILY, "2027-03-13", hour = 2, minute = 30)
        assertEquals("2027-03-14T03:30", next(rem, "2027-03-13T03:00"))
    }
}
