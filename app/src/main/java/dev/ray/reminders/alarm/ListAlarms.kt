package dev.ray.reminders.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.data.ListItem
import dev.ray.reminders.data.ListType
import dev.ray.reminders.data.ListsStore
import dev.ray.reminders.data.NotaList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Due-date reminders for checklist items. */
object ListAlarms {
    const val ACTION_REMIND = "dev.ray.reminders.list.REMIND"
    const val ACTION_CHECK = "dev.ray.reminders.list.CHECK"
    private const val CH = "list_reminders"

    private fun key(list: NotaList, item: ListItem) = list.id to item.id
    private fun notifId(listId: Int, itemId: Int) = 3_000_000 + listId * 10_000 + itemId

    /** Wanted reminder times for every unchecked, due, future checklist item. */
    private fun wanted(lists: List<NotaList>, now: Long): Map<Pair<Int, Int>, Long> = buildMap {
        for (l in lists) if (l.type == ListType.CHECKLIST) for (i in l.items) {
            val at = i.remindAt() ?: continue
            if (!i.checked && at > now) put(key(l, i), at)
        }
    }

    fun sync(ctx: Context, before: List<NotaList>, after: List<NotaList>) {
        val now = System.currentTimeMillis()
        val old = wanted(before, now)
        val new = wanted(after, now)
        for ((k, _) in old) if (k !in new) cancel(ctx, k.first, k.second)
        for ((k, at) in new) if (old[k] != at) schedule(ctx, k.first, k.second, at)
        // Checked off or deleted: take its notification down too.
        val live = after.flatMap { l -> l.items.filterNot { it.checked }.map { l.id to it.id } }.toSet()
        before.forEach { l -> l.items.forEach { if ((l.id to it.id) !in live) nm(ctx).cancel(notifId(l.id, it.id)) } }
    }

    fun restore(ctx: Context) {
        val now = System.currentTimeMillis()
        wanted(ListsStore.get(ctx).lists.value, now).forEach { (k, at) -> schedule(ctx, k.first, k.second, at) }
    }

    private fun pi(ctx: Context, action: String, listId: Int, itemId: Int) = PendingIntent.getBroadcast(
        ctx, notifId(listId, itemId),
        Intent(ctx, ListReceiver::class.java).setAction(action).setData(Uri.parse("listitem://$listId/$itemId/$action"))
            .putExtra("list", listId).putExtra("item", itemId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(ctx: Context, listId: Int, itemId: Int, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, ACTION_REMIND, listId, itemId)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    private fun cancel(ctx: Context, listId: Int, itemId: Int) =
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, ACTION_REMIND, listId, itemId))

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun notify(ctx: Context, listId: Int, itemId: Int) {
        val store = ListsStore.get(ctx)
        val list = store.lists.value.firstOrNull { it.id == listId } ?: return
        val item = list.items.firstOrNull { it.id == itemId } ?: return
        if (item.checked) return
        nm(ctx).createNotificationChannel(NotificationChannel(CH, "Checklist due dates", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, notifId(listId, itemId), Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "lists")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.text)
            .setContentText("${list.name} · due ${describeDue(ctx, item)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup("list-$listId-$itemId")
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_notification), "Check off", pi(ctx, ACTION_CHECK, listId, itemId),
                ).build(),
            )
            .build()
        nm(ctx).notify(notifId(listId, itemId), n)
    }

    /** "Today", "Tomorrow" or "Fri, Nov 7", plus the time if one was set. */
    fun describeDue(ctx: Context, item: ListItem): String {
        val day = item.dueDay ?: return ""
        val today = LocalDate.now().toEpochDay()
        val date = when (day) {
            today -> "today"
            today + 1 -> "tomorrow"
            today - 1 -> "yesterday"
            else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
        val time = item.dueMinute?.let {
            val at = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + it * 60_000L
            " " + android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date.from(Instant.ofEpochMilli(at)))
        } ?: ""
        return date + time
    }
}

class ListReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val listId = intent.getIntExtra("list", -1)
        val itemId = intent.getIntExtra("item", -1)
        when (intent.action) {
            ListAlarms.ACTION_REMIND -> ListAlarms.notify(ctx, listId, itemId)
            ListAlarms.ACTION_CHECK -> {
                val store = ListsStore.get(ctx)
                store.item(listId, itemId)?.let { store.updateItem(listId, it.copy(checked = true)) }
            }
        }
    }
}
