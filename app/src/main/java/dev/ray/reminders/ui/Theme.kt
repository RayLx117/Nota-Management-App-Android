package dev.ray.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R
import dev.ray.reminders.data.RepeatType

/** Nota's "Shelf" look: deep navy, mint dot, cyan accents (from the app icon). */
object Nota {
    val Bg = Color(0xFF0B1020)
    val Shelf = Color(0xFF151C30)
    val Raised = Color(0xFF1F2842)
    val Line = Color(0xFF1A2238)
    val Outline = Color(0xFF34405F)
    val Text = Color(0xFFEEF2FA)
    val Sub = Color(0xFF8E9AB8)
    val Ink = Color(0xFF06121A)
    val Mint = Color(0xFF2EE59D)
    val Cyan = Color(0xFF22C3E6)
    val Violet = Color(0xFF8B7CFF)
    val Amber = Color(0xFFFFC857)
    val Pink = Color(0xFFFF7AB6)
    val Error = Color(0xFFFF6B6B)

    val Outfit = FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
    )
    val Mono = FontFamily(Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold))

    /** Small caps-style section label, e.g. "PINNED TO YOUR BAR". */
    val Label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp)

    fun accent(type: RepeatType): Color = when (type) {
        RepeatType.ONCE -> Amber
        RepeatType.DAILY -> Mint
        RepeatType.WEEKLY -> Cyan
        RepeatType.MONTHLY -> Violet
        RepeatType.YEARLY -> Pink
    }
}

private val scheme = darkColorScheme(
    primary = Nota.Mint, onPrimary = Nota.Ink,
    secondary = Nota.Cyan, onSecondary = Nota.Ink,
    tertiary = Nota.Violet,
    background = Nota.Bg, onBackground = Nota.Text,
    surface = Nota.Bg, onSurface = Nota.Text,
    surfaceVariant = Nota.Raised, onSurfaceVariant = Nota.Sub,
    surfaceContainerLowest = Nota.Bg, surfaceContainerLow = Nota.Shelf, surfaceContainer = Nota.Shelf,
    surfaceContainerHigh = Nota.Raised, surfaceContainerHighest = Nota.Raised,
    outline = Nota.Outline, outlineVariant = Nota.Line,
    error = Nota.Error, onError = Nota.Ink,
    secondaryContainer = Nota.Raised, onSecondaryContainer = Nota.Text,
    primaryContainer = Nota.Raised, onPrimaryContainer = Nota.Text,
)

private val typography = Typography().let { t ->
    fun TextStyle.o() = copy(fontFamily = Nota.Outfit)
    Typography(
        displayLarge = t.displayLarge.o(), displayMedium = t.displayMedium.o(), displaySmall = t.displaySmall.o(),
        headlineLarge = t.headlineLarge.o().copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.o().copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.o().copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.o().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.o().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.o().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.o(), bodyMedium = t.bodyMedium.o(), bodySmall = t.bodySmall.o(),
        labelLarge = t.labelLarge.o().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.o(), labelSmall = t.labelSmall.o(),
    )
}

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

@Composable
fun notaSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Nota.Ink, checkedTrackColor = Nota.Mint, checkedBorderColor = Nota.Mint,
    uncheckedThumbColor = Nota.Sub, uncheckedTrackColor = Nota.Raised, uncheckedBorderColor = Nota.Outline,
)

/** The mint "notification dot" from the logo. */
@Composable
fun Dot(size: Dp = 8.dp, color: Color = Nota.Mint, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Main action: light button with the mint dot. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Nota.Text).clickable(onClick = onClick)
            .padding(horizontal = 20.dp).height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Dot()
        Text(text, color = Nota.Ink, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

/** Secondary action: raised navy button. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp, color: Color = Nota.Text) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Nota.Raised).clickable(onClick = onClick)
            .padding(horizontal = 18.dp).height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Selectable chip; the selected one gets the notification dot on its corner. */
@Composable
fun DotChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Nota.Raised)
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (selected) Nota.Text else Nota.Sub,
            )
        }
        if (selected) Dot(
            size = 11.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).border(2.dp, Nota.Bg, CircleShape),
        )
    }
}

/** Round icon-style button used on the shelf (Done / Snooze). */
@Composable
fun RoundButton(label: String, filled: Boolean, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (filled) Modifier.background(Nota.Mint) else Modifier.border(2.dp, Nota.Outline, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (filled) Nota.Ink else Nota.Sub, fontFamily = Nota.Outfit,
            fontWeight = FontWeight.ExtraBold, fontSize = if (filled) 18.sp else 13.sp,
        )
    }
}
