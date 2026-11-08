package dev.ray.reminders.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Press-and-hold arranging for a LazyColumn: hold an item to start selecting (everything wobbles),
 * keep holding and drag to reorder it, or drop it on a folder row (keys that aren't Ints).
 */
class ArrangeState(val list: LazyListState) {
    /** The item being dragged. */
    var dragging by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    /** Folder row (or other drop target) under the dragged item. */
    var hover by mutableStateOf<Any?>(null)
        private set
    /** Live order while dragging. */
    var order by mutableStateOf<List<Int>?>(null)
        private set
    private var moved = false

    fun start(id: Int, current: List<Int>) {
        dragging = id; offset = 0f; order = current; hover = null; moved = false
    }

    fun drag(dy: Float, canSwap: (Int, Int) -> Boolean) {
        val id = dragging ?: return
        offset += dy
        if (dy != 0f) moved = true
        val info = list.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = me.offset + offset + me.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.key != id && center >= it.offset && center < it.offset + it.size }
        hover = null
        when (val k = target?.key) {
            is Int -> {
                val ord = order ?: return
                if (k in ord && canSwap(id, k)) {
                    order = ord.toMutableList().apply { remove(id); add(ord.indexOf(k), id) }
                    // Keep it under the finger: it now sits where the target was.
                    val newStart = if (target.offset > me.offset) target.offset + target.size - me.size else target.offset
                    offset -= (newStart - me.offset)
                }
            }
            is String -> hover = k
            else -> {}
        }
        // Near the top or bottom edge: scroll along.
        val edge = 140f
        val step = when {
            center < info.viewportStartOffset + edge -> -14f
            center > info.viewportEndOffset - edge -> 14f
            else -> 0f
        }
        if (step != 0f) offset += list.dispatchRawDelta(step)
    }

    /** Ends the drag; returns (item, drop target or null, final order, whether it moved). */
    fun end(): Drop? {
        val id = dragging ?: return null
        val result = Drop(id, hover, order.orEmpty(), moved)
        dragging = null; offset = 0f; hover = null; order = null
        return result
    }

    data class Drop(val id: Int, val target: Any?, val order: List<Int>, val moved: Boolean)
}

@Composable
fun rememberArrangeState(list: LazyListState) = remember(list) { ArrangeState(list) }

/** A slow little wobble angle shared by every item while arranging. */
@Composable
fun rememberWobble(): State<Float> = rememberInfiniteTransition(label = "wobble").animateFloat(
    -1f, 1f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "angle",
)

/**
 * Tap = open (or toggle while selecting). Hold = select it and pick it up; drag to move it.
 * [order] is the current top-to-bottom ids; [onDrop] gets what happened.
 */
@Composable
fun Modifier.arrangeable(
    state: ArrangeState, sel: Selection, id: Int, enabled: Boolean, wobble: State<Float>,
    order: () -> List<Int>, canSwap: (Int, Int) -> Boolean, onOpen: () -> Unit, onDrop: (ArrangeState.Drop) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val open by rememberUpdatedState(onOpen)
    val drop by rememberUpdatedState(onDrop)
    val currentOrder by rememberUpdatedState(order)
    val swap by rememberUpdatedState(canSwap)
    val dragging = state.dragging == id
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            if (dragging) {
                translationY = state.offset
                scaleX = 1.03f; scaleY = 1.03f
                shadowElevation = 18f
            } else if (sel.active) {
                rotationZ = wobble.value * (if (id % 2 == 0) 0.9f else -0.9f)
            }
        }
        // Not keyed on selection: picking an item up selects it, and that mustn't restart the gesture.
        .pointerInput(id, enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Already wobbling: no need to hold again, just drag it (or tap to toggle).
                if (sel.active && enabled) {
                    val first = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (first == null) {
                        val last = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (last != null && !last.pressed && !last.isConsumed) { last.consume(); sel.toggle(id) }
                        return@awaitEachGesture
                    }
                    if (!sel.has(id)) sel.toggle(id)
                    state.start(id, currentOrder())
                    state.drag(first.positionChange().y, swap)
                    drag(first.id) { change ->
                        state.drag(change.positionChange().y, swap)
                        change.consume()
                    }
                    state.end()?.let(drop)
                    return@awaitEachGesture
                }
                var cancelled = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation().also { if (it == null) cancelled = true }
                }
                if (cancelled) return@awaitEachGesture
                if (up != null) {
                    up.consume()
                    if (sel.active) sel.toggle(id) else open()
                    return@awaitEachGesture
                }
                // Held down: select it and pick it up.
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!sel.has(id)) sel.toggle(id)
                if (!enabled) {
                    // Can't drag here (e.g. while searching): just wait for the finger to lift.
                    drag(down.id) { it.consume() }
                    return@awaitEachGesture
                }
                state.start(id, currentOrder())
                drag(down.id) { change ->
                    state.drag(change.positionChange().y, swap)
                    change.consume()
                }
                state.end()?.let(drop)
            }
        }
}
