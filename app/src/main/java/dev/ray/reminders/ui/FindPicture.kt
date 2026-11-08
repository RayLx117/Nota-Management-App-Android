package dev.ray.reminders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.ray.reminders.data.ImageSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Find a picture": searches for [initial] (the item's name) and saves the photo you tap. */
@Composable
fun FindPictureDialog(initial: String, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initial.trim()) }
    var searched by remember { mutableStateOf(initial.trim()) }
    var results by remember { mutableStateOf<List<ImageSearch.Result>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf(ImageSearch.Source.PHOTOS) }

    LaunchedEffect(searched, source) {
        if (searched.isBlank()) { results = emptyList(); return@LaunchedEffect }
        results = null; error = null
        runCatching { withContext(Dispatchers.IO) { ImageSearch.search(searched, source) } }
            .onSuccess { results = it }
            .onFailure { results = emptyList(); error = "Couldn't search. Check your internet connection." }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Find a picture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(query, { query = it }, "Search pictures", Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageSearch.Source.entries.forEach { s -> DotChip(s.label, s == source) { source = s } }
                }
                TextButton(onClick = { searched = query.trim() }, enabled = query.isNotBlank() && query.trim() != searched) {
                    Text("Search", color = if (query.isNotBlank() && query.trim() != searched) Nota.Mint else Nota.Outline)
                }
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    val list = results
                    when {
                        list == null -> CircularProgressIndicator(color = Nota.Mint)
                        error != null -> Text(error!!, color = Nota.Sub, style = MaterialTheme.typography.bodyMedium)
                        list.isEmpty() -> Text("No photos found. Try other words.", color = Nota.Sub, style = MaterialTheme.typography.bodyMedium)
                        else -> LazyVerticalGrid(
                            GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(list, key = { it.id }) { r ->
                                Thumb(r, dim = saving != null && saving != r.id, busy = saving == r.id) {
                                    if (saving != null) return@Thumb
                                    saving = r.id
                                    scope.launch {
                                        val path = withContext(Dispatchers.IO) { ImageSearch.save(ctx, r) }
                                        saving = null
                                        if (path != null) onPicked(path) else error = "Couldn't download that one. Try another."
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    when (source) {
                        ImageSearch.Source.PHOTOS -> "Openly licensed photos from Openverse."
                        ImageSearch.Source.WIKIPEDIA -> "Pictures from Wikipedia articles."
                        ImageSearch.Source.ANIME -> "Covers and banners from AniList."
                    }, color = Nota.Outline, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
private fun Thumb(r: ImageSearch.Result, dim: Boolean, busy: Boolean, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, r.thumb) {
        value = withContext(Dispatchers.IO) { ImageSearch.thumbnail(r.thumb) }?.asImageBitmap()
    }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Nota.Raised).alpha(if (dim) 0.4f else 1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it, contentDescription = r.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        if (busy) CircularProgressIndicator(color = Nota.Mint, modifier = Modifier.size(26.dp).padding(2.dp))
    }
}
