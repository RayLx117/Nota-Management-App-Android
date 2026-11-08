package dev.ray.reminders.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.MainActivity
import dev.ray.reminders.data.NotesStore

/** The Quick note widget's popup: type, then the big check saves it as a new (unnamed) note. */
class QuickNoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemindersTheme {
                var text by rememberSaveable { mutableStateOf("") }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                fun save() {
                    if (text.isBlank()) return
                    NotesStore.get(this).addQuick(text)
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                // Tapping outside the card closes it.
                Box(
                    Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
                        .imePadding().navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Nota.Shelf)
                            .clickable(remember { MutableInteractionSource() }, indication = null) {}
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("QUICK NOTE", style = Nota.Label, color = Nota.Sub, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                startActivity(
                                    Intent(this@QuickNoteActivity, MainActivity::class.java)
                                        .putExtra(MainActivity.EXTRA_TAB, "notes").putExtra(MainActivity.EXTRA_NOTE_TEXT, text)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                )
                                finish()
                            }) { Text("Full note", color = Nota.Mint) }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(
                                text, { text = it }, placeholder = { Text("Write a note…", color = Nota.Sub) },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp, max = 260.dp).focusRequester(focus),
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Outline, cursorColor = Nota.Mint,
                                    focusedTextColor = Nota.Text, unfocusedTextColor = Nota.Text,
                                    focusedContainerColor = Nota.Raised, unfocusedContainerColor = Nota.Raised,
                                ),
                            )
                            // Plain until there's something to save, then it lights up in the accent.
                            val ready = text.isNotBlank()
                            Box(
                                Modifier.padding(start = 10.dp).size(64.dp).clip(CircleShape)
                                    .background(if (ready) Nota.Mint else Nota.Raised)
                                    .clickable(enabled = ready) { save() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check, contentDescription = "Save note", tint = if (ready) Nota.Ink else Nota.Outline,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
