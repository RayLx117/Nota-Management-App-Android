package dev.ray.reminders.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.data.ListType
import dev.ray.reminders.data.ListsStore
import dev.ray.reminders.ui.Nota
import dev.ray.reminders.ui.PageHeader
import dev.ray.reminders.ui.RemindersTheme

/** Shown when a "List" widget is added (or reconfigured): pick which list it shows. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val lists = ListsStore.get(this).lists.value
        setContent {
            RemindersTheme {
                Column(
                    Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().navigationBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PageHeader("Pick a list")
                    if (lists.isEmpty()) Text(
                        "You don't have any lists yet. Make one in Nota's Lists tab first.",
                        style = MaterialTheme.typography.bodyLarge, color = Nota.Sub,
                    )
                    lists.forEach { list ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nota.Shelf)
                                .clickable {
                                    ListWidgets.setListFor(this@WidgetConfigActivity, widgetId, list.id)
                                    ListWidgets.render(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId, all = false)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                    finish()
                                }
                                .padding(16.dp),
                        ) {
                            Text(
                                if (list.type == ListType.CHECKLIST) "CHECKLIST" else "CATEGORIZED LIST",
                                style = Nota.Label, color = Nota.Sub,
                            )
                            Text(list.name, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Nota.Text)
                        }
                    }
                }
            }
        }
    }
}
