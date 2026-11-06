package dev.ray.reminders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.alarm.ListAlarms
import dev.ray.reminders.data.ListType
import dev.ray.reminders.data.ListsStore
import dev.ray.reminders.data.NotaList

/** Home screen widgets for Lists: one fixed list ([ListWidget]) or all of them with arrows ([AllListsWidget]). */
object ListWidgets {
    const val ACTION_TOGGLE = "dev.ray.reminders.widget.TOGGLE"
    const val ACTION_CLEAR = "dev.ray.reminders.widget.CLEAR"
    const val ACTION_PREV = "dev.ray.reminders.widget.PREV"
    const val ACTION_NEXT = "dev.ray.reminders.widget.NEXT"
    const val EXTRA_LIST = "list"
    const val EXTRA_ITEM = "item"

    private val SECTION_COLORS = intArrayOf(0xFF2EE59D.toInt(), 0xFF22C3E6.toInt(), 0xFF8B7CFF.toInt(), 0xFFFFC857.toInt(), 0xFFFF7AB6.toInt())
    private const val TEXT = 0xFFEEF2FA.toInt()
    private const val SUB = 0xFF8E9AB8.toInt()
    private const val ERROR = 0xFFFF6B6B.toInt()

    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** Which list a widget shows (chosen for single-list widgets, current page for the all-lists one). */
    fun listFor(ctx: Context, widgetId: Int): Int = prefs(ctx).getInt("list_$widgetId", -1)
    fun setListFor(ctx: Context, widgetId: Int, listId: Int) = prefs(ctx).edit().putInt("list_$widgetId", listId).apply()

    /** Re-render every list widget (after any change to the lists). */
    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (cls in listOf(ListWidget::class.java, AllListsWidget::class.java)) {
            mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { render(ctx, mgr, it, all = cls == AllListsWidget::class.java) }
        }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int, all: Boolean) {
        val lists = ListsStore.get(ctx).lists.value
        val rv = RemoteViews(ctx.packageName, R.layout.widget_list)
        var list = lists.firstOrNull { it.id == listFor(ctx, widgetId) }
        if (all && list == null) list = lists.firstOrNull()

        // Tapping the top opens Nota on that list.
        rv.setOnClickPendingIntent(R.id.header, openApp(ctx, widgetId, list?.id))
        rv.setViewVisibility(R.id.prev, if (all && lists.size > 1) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.next, if (all && lists.size > 1) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.position, if (all && lists.size > 1) View.VISIBLE else View.GONE)
        if (all) {
            rv.setOnClickPendingIntent(R.id.prev, broadcast(ctx, ACTION_PREV, widgetId, -1))
            rv.setOnClickPendingIntent(R.id.next, broadcast(ctx, ACTION_NEXT, widgetId, -1))
            val index = lists.indexOf(list)
            rv.setTextViewText(R.id.position, "${index + 1}/${lists.size}")
        }

        if (list == null) {
            rv.setTextViewText(R.id.kind, "")
            rv.setTextViewText(R.id.title, "Lists")
            rv.setViewVisibility(R.id.clear, View.GONE)
            rv.setRemoteAdapter(R.id.items, RemoteViews.RemoteCollectionItems.Builder().build())
            rv.setTextViewText(R.id.empty, if (all) "Make a list in Nota to see it here." else "This list was deleted.")
            rv.setEmptyView(R.id.items, R.id.empty)
            mgr.updateAppWidget(widgetId, rv)
            return
        }

        rv.setTextViewText(R.id.kind, if (list.type == ListType.CHECKLIST) "CHECKLIST" else "SECTIONED LIST")
        rv.setTextViewText(R.id.title, list.name)
        val anyChecked = list.type == ListType.CHECKLIST && list.items.any { it.checked }
        rv.setViewVisibility(R.id.clear, if (anyChecked) View.VISIBLE else View.GONE)
        rv.setOnClickPendingIntent(R.id.clear, broadcast(ctx, ACTION_CLEAR, widgetId, list.id))
        rv.setTextViewText(R.id.empty, if (list.type == ListType.CHECKLIST) "Nothing on this list." else "No items yet.")
        rv.setEmptyView(R.id.items, R.id.empty)
        rv.setRemoteAdapter(R.id.items, items(ctx, list))
        // Rows fill in which item was tapped; checklists toggle it, sectioned lists open the app.
        rv.setPendingIntentTemplate(
            R.id.items,
            if (list.type == ListType.CHECKLIST) PendingIntent.getBroadcast(
                ctx, widgetId, Intent(ctx, ListWidgetActions::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ) else PendingIntent.getActivity(
                ctx, widgetId, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        mgr.updateAppWidget(widgetId, rv)
    }

    private fun items(ctx: Context, list: NotaList): RemoteViews.RemoteCollectionItems {
        val b = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(3)
        if (list.type == ListType.CHECKLIST) {
            val ordered = list.items.filterNot { it.checked } + list.items.filter { it.checked }
            ordered.forEach { item ->
                val row = RemoteViews(ctx.packageName, R.layout.widget_item_check)
                row.setImageViewResource(R.id.check, if (item.checked) R.drawable.widget_check_on else R.drawable.widget_check_off)
                row.setTextViewText(R.id.text, item.text)
                row.setTextColor(R.id.text, if (item.checked) SUB else TEXT)
                row.setInt(R.id.text, "setPaintFlags", if (item.checked) Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG else Paint.ANTI_ALIAS_FLAG)
                if (item.dueDay != null && !item.checked) {
                    val late = (item.dueAt() ?: Long.MAX_VALUE) < System.currentTimeMillis()
                    row.setViewVisibility(R.id.due, View.VISIBLE)
                    row.setTextViewText(R.id.due, "Due " + ListAlarms.describeDue(ctx, item))
                    row.setTextColor(R.id.due, if (late) ERROR else SUB)
                } else row.setViewVisibility(R.id.due, View.GONE)
                row.setOnClickFillInIntent(
                    R.id.row, Intent().putExtra(EXTRA_LIST, list.id).putExtra(EXTRA_ITEM, item.id),
                )
                b.addItem(item.id.toLong(), row)
            }
        } else {
            val known = list.statuses.map { it.id }.toSet()
            val open = Intent().putExtra(MainActivity.EXTRA_TAB, "lists").putExtra(MainActivity.EXTRA_LIST, list.id)
            list.statuses.forEachIndexed { i, section ->
                val color = SECTION_COLORS[i % SECTION_COLORS.size]
                val header = RemoteViews(ctx.packageName, R.layout.widget_section_header)
                val inSection = list.items.filter { section.id in it.sections }
                header.setTextViewText(R.id.name, "${section.name.uppercase()}  ${inSection.size}")
                header.setTextColor(R.id.name, color)
                header.setOnClickFillInIntent(R.id.name, open)
                b.addItem(-1_000_000L - section.id, header)
                inSection.forEach { item -> b.addItem(section.id * 100_000L + item.id, sectionItem(ctx, item, color, open)) }
            }
            val loose = list.items.filter { it.sections.none { id -> id in known } }
            if (loose.isNotEmpty()) {
                val header = RemoteViews(ctx.packageName, R.layout.widget_section_header)
                header.setTextViewText(R.id.name, "NO SECTION  ${loose.size}")
                header.setTextColor(R.id.name, SUB)
                header.setOnClickFillInIntent(R.id.name, open)
                b.addItem(-2_000_000L, header)
                loose.forEach { item -> b.addItem(-3_000_000L - item.id, sectionItem(ctx, item, 0xFF34405F.toInt(), open)) }
            }
        }
        return b.build()
    }

    private fun sectionItem(ctx: Context, item: dev.ray.reminders.data.ListItem, color: Int, open: Intent) =
        RemoteViews(ctx.packageName, R.layout.widget_section_item).apply {
            setTextViewText(R.id.text, item.text)
            setInt(R.id.bar, "setColorFilter", color)
            if (item.description.isNotBlank()) {
                setViewVisibility(R.id.notes, View.VISIBLE)
                setTextViewText(R.id.notes, item.description)
            } else setViewVisibility(R.id.notes, View.GONE)
            setOnClickFillInIntent(R.id.row, open)
        }

    private fun openApp(ctx: Context, widgetId: Int, listId: Int?) = PendingIntent.getActivity(
        ctx, 50_000 + widgetId,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "lists")
            .putExtra(MainActivity.EXTRA_LIST, listId ?: -1).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.parse("nota://lists/$widgetId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(ctx: Context, action: String, widgetId: Int, listId: Int) = PendingIntent.getBroadcast(
        ctx, widgetId,
        Intent(ctx, ListWidgetActions::class.java).setAction(action).setData(Uri.parse("nota://widget/$widgetId/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId).putExtra(EXTRA_LIST, listId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** A widget showing one chosen list. */
class ListWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { ListWidgets.render(ctx, mgr, it, all = false) }
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        ListWidgets.prefs(ctx).edit().apply { ids.forEach { remove("list_$it") } }.apply()
    }
}

/** A widget that flips through every list. */
class AllListsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { ListWidgets.render(ctx, mgr, it, all = true) }
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        ListWidgets.prefs(ctx).edit().apply { ids.forEach { remove("list_$it") } }.apply()
    }
}

/** Button presses from list widgets. */
class ListWidgetActions : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = ListsStore.get(ctx)
        val listId = intent.getIntExtra(ListWidgets.EXTRA_LIST, -1)
        when (intent.action) {
            ListWidgets.ACTION_TOGGLE -> {
                val item = store.item(listId, intent.getIntExtra(ListWidgets.EXTRA_ITEM, -1)) ?: return
                store.updateItem(listId, item.copy(checked = !item.checked))
            }
            ListWidgets.ACTION_CLEAR -> store.clearChecked(listId)
            ListWidgets.ACTION_PREV, ListWidgets.ACTION_NEXT -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val lists = store.lists.value
                if (lists.isEmpty() || widgetId < 0) return
                val current = lists.indexOfFirst { it.id == ListWidgets.listFor(ctx, widgetId) }.coerceAtLeast(0)
                val step = if (intent.action == ListWidgets.ACTION_NEXT) 1 else -1
                val next = lists[(current + step + lists.size) % lists.size]
                ListWidgets.setListFor(ctx, widgetId, next.id)
                ListWidgets.render(ctx, AppWidgetManager.getInstance(ctx), widgetId, all = true)
            }
        }
    }
}
