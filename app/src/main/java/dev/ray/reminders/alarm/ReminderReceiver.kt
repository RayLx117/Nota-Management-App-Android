package dev.ray.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives our own alarms and notification button presses. Not exported. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderEngine.EXTRA_ID, -1)
        if (id < 0) return
        val action = intent.action ?: return
        when (action) {
            ReminderEngine.ACTION_DONE -> ReminderEngine.done(context, id)
            ReminderEngine.ACTION_SNOOZE -> ReminderEngine.snooze(context, id)
            ReminderEngine.ACTION_SWIPED -> ReminderEngine.swiped(context, id)
            ReminderEngine.ACTION_SKIP -> ReminderEngine.skipNext(context, id)
            else -> ReminderEngine.onAlarm(context, action, id)
        }
    }
}

/** Alarms are wiped on reboot and can drift on clock/time-zone changes, so rebuild them. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.ensureChannels(context)
        ReminderEngine.restoreAll(context)
        Timer.restore(context)
    }
}
