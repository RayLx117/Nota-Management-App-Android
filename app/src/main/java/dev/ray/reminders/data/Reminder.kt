package dev.ray.reminders.data

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatType { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** How the reminder's picture appears in the notification. */
@Serializable
enum class ImageStyle { PICTURE, BANNER }

@Serializable
enum class AlertStyle { SOUND_AND_VIBRATE, VIBRATE_ONLY, SILENT }

/** How a reminder behaves once it goes off. Every option is independent. */
@Serializable
data class Behavior(
    /** Keep the notification in the bar until Done is tapped. Swiping it away just brings it back. */
    val sticky: Boolean = true,
    /** How the first alert (and any repeat alerts) get your attention. */
    val alertStyle: AlertStyle = AlertStyle.SOUND_AND_VIBRATE,
    /** Alert again every [nagEveryMinutes] until Done is tapped. */
    val nag: Boolean = false,
    val nagEveryMinutes: Int = 15,
    /** Show a full-screen alarm when the reminder goes off, or [fullScreenAfterMinutes] later if [fullScreenDelay]. */
    val fullScreen: Boolean = false,
    val fullScreenDelay: Boolean = true,
    val fullScreenAfterMinutes: Int = 60,
    /** Length of the Snooze button. */
    val snoozeMinutes: Int = 10,
) {
    /** Minutes between the notification appearing and the full-screen alarm. */
    val fullScreenDelayMinutes: Int get() = if (fullScreenDelay) fullScreenAfterMinutes.coerceAtLeast(1) else 0
}

@Serializable
data class Reminder(
    val id: Int,
    val title: String,
    val notes: String = "",
    val repeat: RepeatType = RepeatType.DAILY,
    /** "Every N" days / weeks / months / years. */
    val interval: Int = 1,
    /** First date (or the only date, for ONCE), as LocalDate.toEpochDay(). */
    val startEpochDay: Long,
    val hour: Int,
    val minute: Int,
    /** For WEEKLY: java.time.DayOfWeek values, 1 = Monday .. 7 = Sunday. */
    val weekdays: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val behavior: Behavior = Behavior(),
    /** Optional picture shown in the notification and on the full-screen alarm (a file in app storage). */
    val imagePath: String? = null,
    val imageStyle: ImageStyle = ImageStyle.PICTURE,

    // ---- runtime state, managed by ReminderEngine ----
    /** Next scheduled occurrence (epoch millis), or null if none. */
    val nextTriggerAt: Long? = null,
    /** Set when the reminder goes off; cleared when Done is tapped or it is snoozed. */
    val activeSince: Long? = null,
    /** When the full-screen alarm was shown for the current activation. */
    val fullScreenShownAt: Long? = null,
    /** When a snoozed reminder will come back. */
    val snoozedUntil: Long? = null,
) {
    val isActive: Boolean get() = activeSince != null
}
