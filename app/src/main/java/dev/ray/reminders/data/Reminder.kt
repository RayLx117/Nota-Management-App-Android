package dev.ray.reminders.data

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatType { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Reminders live in the Reminders tab (notification-first); alarms in the Alarms tab. */
@Serializable
enum class Kind { REMINDER, ALARM }

/** How the reminder's picture appears in the notification. */
@Serializable
enum class ImageStyle { PICTURE, BANNER }

@Serializable
enum class AlertStyle {
    SOUND_AND_VIBRATE, VIBRATE_ONLY, SOUND_ONLY,
    /** Old setting from before the Notification/Alarm split; treated as "Alarm off". */
    SILENT,
}

/** How a reminder behaves once it goes off. Every option is independent. */
@Serializable
data class Behavior(
    /** "Notification" box: a silent notification in the bar (picture, pin until Done, snooze). */
    val notification: Boolean = true,
    /** "Alarm" box: sound and/or vibration, repeat alerts and the full-screen alarm. */
    val alarm: Boolean = true,
    /** Notification box: post quietly (true) or with the normal notification sound. */
    val notificationSilent: Boolean = true,
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
    /** Length of Snooze on the full-screen alarm. */
    val alarmSnoozeMinutes: Int = 10,
    /** Alarms only: a quiet heads-up notification [preAlertMinutes] before the alarm rings. */
    val preAlert: Boolean = false,
    val preAlertMinutes: Int = 10,
) {
    /** Alarm box is on (old "Silent" reminders count as alarm off). */
    val alarmOn: Boolean get() = alarm && alertStyle != AlertStyle.SILENT

    // What actually happens, taking the two boxes into account.
    val effectiveSticky: Boolean get() = notification && sticky
    // Repeat alert was replaced by alarm snooze in the editor, so it's no longer applied.
    val effectiveNag: Boolean get() = false
    // The Alarm box always means the full-screen alarm.
    val effectiveFullScreen: Boolean get() = alarmOn

    /** Minutes between the notification appearing and the full-screen alarm. */
    // The delay counts from the notification appearing, so it needs the Notification box on.
    val fullScreenDelayMinutes: Int get() = if (fullScreenDelay && notification) fullScreenAfterMinutes.coerceAtLeast(1) else 0
}

@Serializable
data class Reminder(
    val id: Int,
    val kind: Kind = Kind.REMINDER,
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
    val imageStyle: ImageStyle = ImageStyle.BANNER,

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

    /**
     * The behaviour that actually applies. Alarms always ring full-screen straight away and
     * have no notification of their own (apart from the optional heads-up before they ring).
     */
    val fx: Behavior
        // Reminders are always a notification (the Notification/Alarm boxes are gone).
        get() = if (kind == Kind.REMINDER) behavior.copy(notification = true) else behavior.copy(
            notification = false, alarm = true, fullScreenDelay = false,
            alertStyle = if (behavior.alertStyle == AlertStyle.SILENT) AlertStyle.SOUND_AND_VIBRATE else behavior.alertStyle,
        )
}
