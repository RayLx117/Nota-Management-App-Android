package dev.ray.reminders.ui

import dev.ray.reminders.data.AlertStyle
import dev.ray.reminders.data.ImageStyle
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Plain-language descriptions so it's always clear what a reminder will do. */
object Describe {
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val monthDayFmt = DateTimeFormatter.ofPattern("MMM d")
    private val whenFmt = DateTimeFormatter.ofPattern("EEE, MMM d 'at' ")

    fun time(r: Reminder): String = LocalTime.of(r.hour, r.minute).format(timeFmt)
    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)

    fun dayName(value: Int, style: TextStyle = TextStyle.SHORT): String =
        DayOfWeek.of(value).getDisplayName(style, Locale.getDefault())

    fun schedule(r: Reminder): String {
        val start = LocalDate.ofEpochDay(r.startEpochDay)
        val n = r.interval.coerceAtLeast(1)
        val at = "at ${time(r)}"
        return when (r.repeat) {
            RepeatType.ONCE -> "Once, on ${start.format(dateFmt)} $at"
            RepeatType.DAILY -> (if (n == 1) "Every day" else "Every $n days") + " $at"
            RepeatType.WEEKLY -> {
                val days = r.weekdays.ifEmpty { setOf(start.dayOfWeek.value) }.sorted()
                val dayText = if (days.size == 7) "every day" else days.joinToString(", ") { dayName(it) }
                (if (n == 1) "Every week" else "Every $n weeks") + " on $dayText $at"
            }
            RepeatType.MONTHLY -> {
                val d = start.dayOfMonth
                val clamp = if (d > 28) " (last day in shorter months)" else ""
                (if (n == 1) "Every month" else "Every $n months") + " on the ${ordinal(d)}$clamp $at"
            }
            RepeatType.YEARLY -> (if (n == 1) "Every year" else "Every $n years") +
                " on ${start.format(monthDayFmt)} $at"
        }
    }

    /** One sentence per behaviour, in the order they happen. */
    fun behavior(r: Reminder): List<String> {
        val b = r.behavior
        val first = when (b.alertStyle) {
            AlertStyle.SOUND_AND_VIBRATE -> "A notification appears with sound and vibration."
            AlertStyle.VIBRATE_ONLY -> "A notification appears and vibrates (no sound)."
            AlertStyle.SILENT -> "A notification appears silently."
        }
        return buildList {
            add(first)
            if (r.imagePath != null) add(
                if (r.imageStyle == ImageStyle.BANNER) "Your picture runs as a strip across the notification, with the title on top."
                else "It shows your picture: a thumbnail, and a full-width image when you expand it.",
            )
            add(
                if (b.sticky) "It stays in your notification bar until you tap Done. Swiping it away just brings it back."
                else "You can swipe it away like a normal notification.",
            )
            if (b.nag) add("Until you tap Done, it alerts you again every ${minutes(b.nagEveryMinutes)}.")
            if (b.fullScreen) add(
                if (b.fullScreenDelayMinutes == 0) "A full-screen alarm also opens right away, ringing until you tap Done or Snooze."
                else "If it's still not done after ${minutes(b.fullScreenDelayMinutes)}, a full-screen alarm takes over, ringing until you tap Done or Snooze.",
            )
            add("Snooze hides it for ${minutes(b.snoozeMinutes)}.")
        }
    }

    fun status(r: Reminder, now: Long = System.currentTimeMillis()): String = when {
        r.isActive -> "Waiting for you since ${clock(r.activeSince!!)}"
        r.snoozedUntil != null -> "Snoozed until ${clock(r.snoozedUntil)}"
        !r.enabled -> "Off"
        r.nextTriggerAt != null -> "Next: ${moment(r.nextTriggerAt, now)}"
        else -> "No more occurrences"
    }

    /** Line under a pinned reminder: "since 8:00 AM · every 15 min". */
    fun pinnedLine(r: Reminder): String = buildList {
        r.activeSince?.let { add("since ${clock(it)}") }
        if (r.behavior.nag) add("every ${short(r.behavior.nagEveryMinutes)}")
    }.joinToString(" · ")

    /** Compact schedule for list rows: "Daily", "Weekly · Mon, Wed", "Monthly · 1st". */
    fun shortSchedule(r: Reminder): String {
        val start = LocalDate.ofEpochDay(r.startEpochDay)
        val n = r.interval.coerceAtLeast(1)
        return when (r.repeat) {
            RepeatType.ONCE -> "Once"
            RepeatType.DAILY -> if (n == 1) "Daily" else "Every $n days"
            RepeatType.WEEKLY -> (if (n == 1) "Weekly" else "Every $n weeks") + " · " +
                r.weekdays.ifEmpty { setOf(start.dayOfWeek.value) }.sorted().joinToString(", ") { dayName(it) }
            RepeatType.MONTHLY -> (if (n == 1) "Monthly" else "Every $n months") + " · " + ordinal(start.dayOfMonth)
            RepeatType.YEARLY -> (if (n == 1) "Yearly" else "Every $n years") + " · " + start.format(monthDayFmt)
        }
    }

    /** Right side of a list row: (time, day label), e.g. ("10:00 AM", "tomorrow"). */
    fun whenParts(r: Reminder, now: Long = System.currentTimeMillis()): Pair<String, String> {
        if (!r.enabled) return time(r) to "off"
        val at = r.snoozedUntil ?: r.nextTriggerAt ?: return time(r) to "done"
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (t.toLocalDate()) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> t.format(monthDayFmt)
        }
        return t.toLocalTime().format(timeFmt) to (if (r.snoozedUntil != null) "snoozed" else day)
    }

    private fun short(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    fun minutes(m: Int): String = when {
        m < 60 -> if (m == 1) "1 minute" else "$m minutes"
        m % 60 == 0 -> (m / 60).let { if (it == 1) "1 hour" else "$it hours" }
        else -> "${m / 60} h ${m % 60} min"
    }

    private fun clock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)

    private fun moment(millis: Long, now: Long): String {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val prefix = when (t.toLocalDate()) {
            today -> "Today at "
            today.plusDays(1) -> "Tomorrow at "
            else -> t.format(whenFmt)
        }
        return prefix + t.toLocalTime().format(timeFmt)
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
