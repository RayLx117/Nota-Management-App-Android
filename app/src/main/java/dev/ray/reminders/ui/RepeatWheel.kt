package dev.ray.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * An endless horizontal wheel of options. The centred option is selected; options fade
 * out the further they are from the centre, and scrolling wraps around forever.
 */
@Composable
fun <T> LoopWheel(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Nota.Shelf,
) {
    val count = options.size
    val middle = remember(count) { (Int.MAX_VALUE / 2).let { it - it % count } }
    val state = rememberPagerState(initialPage = middle + options.indexOf(selected).coerceAtLeast(0)) { Int.MAX_VALUE }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Select as soon as an option crosses the centre, so the settings below follow the swipe.
    LaunchedEffect(state, count) {
        var first = true
        snapshotFlow { state.currentPage }.collect { page ->
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
            currentOnSelect(options[page % count])
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp).background(background)) {
        val itemWidth = 108.dp
        HorizontalPager(
            state = state,
            pageSize = PageSize.Fixed(itemWidth),
            contentPadding = PaddingValues(horizontal = (maxWidth - itemWidth) / 2),
            beyondViewportPageCount = 3,
            flingBehavior = PagerDefaults.flingBehavior(state, pagerSnapDistance = PagerSnapDistance.atMost(count)),
            modifier = Modifier.fillMaxHeight(),
        ) { page ->
            val distance = ((page - state.currentPage) - state.currentPageOffsetFraction).absoluteValue
            val closeness = (1f - distance.coerceAtMost(2.5f) / 2.5f)
            Box(
                Modifier.fillMaxHeight().fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { state.animateScrollToPage(page) }
                    }
                    .graphicsLayer {
                        val scale = 0.78f + 0.22f * closeness
                        scaleX = scale; scaleY = scale
                        alpha = 0.15f + 0.85f * closeness
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(options[page % count]),
                    color = lerp(Nota.Sub, Nota.Text, closeness),
                    fontFamily = Nota.Outfit,
                    fontWeight = if (distance < 0.5f) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 22.sp, maxLines = 1,
                )
            }
        }
        // The notification dot marks the centre.
        Dot(size = 7.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        // Soft edges so options fade into the card.
        Box(
            Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(background, Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, background))),
        )
    }
}
