package dev.ray.reminders.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ray.reminders.data.Images
import dev.ray.reminders.data.ListImageStyle
import dev.ray.reminders.data.ListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val ListImageStyle.label: String
    get() = when (this) {
        ListImageStyle.FADE -> "Fade in"
        ListImageStyle.BANNER -> "Banner"
        ListImageStyle.EXPAND -> "Tap to show"
    }

/**
 * One list item. Tapping it opens it up to the full notes and picture (with an edit button);
 * items with nothing more to show go straight to editing.
 */
@Composable
fun ListItemCard(
    item: ListItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    /** Notes beside the name instead of below it (categorized lists). */
    notesRight: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val picture = remember(item.imagePath) { item.imagePath?.let(Images::load)?.asImageBitmap() }
    val hasNotes = item.description.isNotBlank()
    val expandable = hasNotes || picture != null
    // Nothing left to show (e.g. its notes were just removed): back to a plain compact item.
    @Suppress("NAME_SHADOWING") val expanded = expanded && expandable
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background)
            .clickable { if (expandable) onToggle() else onEdit() }.animateContentSize(),
    ) {
        if (picture != null && item.imageStyle == ListImageStyle.BANNER && !expanded) Image(
            picture, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            // Fade in: the picture sits on the right half and melts into the card.
            if (picture != null && item.imageStyle == ListImageStyle.FADE && !expanded) Row(Modifier.matchParentSize()) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.weight(1.1f).fillMaxHeight()) {
                    Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(background, background.copy(alpha = 0.55f), Color.Transparent))))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (notesRight && hasNotes && !expanded) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) { title() }
                        Text(
                            item.description, style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                    } else {
                        title()
                        if (hasNotes) Text(
                            item.description, style = MaterialTheme.typography.bodySmall, color = Nota.Sub,
                            maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    extra()
                }
                if (expandable) Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Show less" else "Show more",
                    tint = Nota.Mint, modifier = Modifier.padding(start = 4.dp).size(26.dp).graphicsLayer { rotationZ = arrow },
                )
            }
        }
        if (expanded) {
            if (picture != null) Image(
                picture, contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(12.dp)),
            )
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit", color = Nota.Mint)
                }
            }
        }
    }
}

/** Picture for a list item: add, replace or remove it, and choose how it shows. */
@Composable
fun ItemPictureSection(path: String?, style: ListImageStyle, query: String, onPath: (String?) -> Unit, onStyle: (ListImageStyle) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { Images.import(ctx, uri) }?.let(onPath)
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val bitmap = remember(path) { path?.let(Images::load)?.asImageBitmap() }
    var finding by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (finding) FindPictureDialog(query, onPicked = { onPath(it); finding = false }, onDismiss = { finding = false })
    Text("PICTURE", style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 4.dp))
    if (bitmap == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Add picture", onClick = ::pick, color = Nota.Mint, height = 46.dp, modifier = Modifier.weight(1f))
            SoftButton("Find a picture", onClick = { finding = true }, height = 46.dp, modifier = Modifier.weight(1f))
        }
        return
    }
    Image(
        bitmap, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = ::pick),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ListImageStyle.entries.forEach { s -> DotChip(s.label, s == style) { onStyle(s) } }
    }
    Row {
        TextButton(onClick = ::pick) { Text("Replace", color = Nota.Mint) }
        TextButton(onClick = { finding = true }) { Text("Find another", color = Nota.Mint) }
        TextButton(onClick = { onPath(null) }) { Text("Remove", color = Nota.Sub) }
    }
}
