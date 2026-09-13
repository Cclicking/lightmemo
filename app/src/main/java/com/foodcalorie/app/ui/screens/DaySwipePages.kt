package com.foodcalorie.app.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable
internal class DaySwipeState {
    var offset by mutableFloatStateOf(0f)
    /** Settle distance for one page (card width + gap), not the full screen width. */
    var width by mutableIntStateOf(0)
    var animation by mutableStateOf<Job?>(null)
    val progress: Float get() = if (width == 0) 0f else (-offset / width).coerceIn(-1f, 1f)
}

@Composable
internal fun rememberDaySwipeState(key: Any): DaySwipeState {
    val state = remember(key) { DaySwipeState() }
    DisposableEffect(state) { onDispose { state.animation?.cancel() } }
    return state
}

/** The same real card content is used throughout drag, settling, and the committed day. */
@Composable
internal fun DaySwipePages(
    state: DaySwipeState,
    enabled: Boolean,
    canMoveNext: Boolean,
    onMove: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    if (!enabled) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content(0) }
        return
    }
    val scope = rememberCoroutineScope()
    val sidePaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val flingThreshold = with(LocalDensity.current) { 400.dp.toPx() }
    Layout(
        modifier = Modifier.fillMaxWidth().clipToBounds()
            .onSizeChanged { size ->
                // Pitch = screen - one side margin, so the next card starts at the screen edge
                // and keeps a 16dp gap from the current card.
                state.width = (size.width - sidePaddingPx).coerceAtLeast(0)
            }
            .draggable(
                state = rememberDraggableState { amount ->
                    state.offset = (state.offset + amount).coerceIn(
                        if (canMoveNext) -state.width.toFloat() else 0f,
                        state.width.toFloat(),
                    )
                },
                orientation = Orientation.Horizontal,
                enabled = enabled,
                startDragImmediately = state.animation?.isActive == true,
                onDragStarted = { state.animation?.cancel() },
                onDragStopped = { velocity ->
                    val requested = when {
                        abs(velocity) > flingThreshold -> if (velocity < 0f) 1 else -1
                        abs(state.progress) > 0.25f -> if (state.offset < 0f) 1 else -1
                        else -> 0
                    }
                    val direction = if (requested > 0 && !canMoveNext) 0 else requested
                    state.animation = scope.launch {
                        animate(state.offset, -direction * state.width.toFloat(), animationSpec = tween(250)) { value, _ ->
                            state.offset = value
                        }
                        // Keep the target visible until the selected date creates the next state.
                        if (direction != 0) onMove(direction)
                    }
                },
            ),
        content = {
            for (direction in -1..1) {
                // Margins/gap come from placement, not per-page padding, so cards emerge
                // from the screen edge with a visible gap instead of from outside the margin.
                Box(Modifier.fillMaxWidth().then(
                    if (direction != 0) Modifier.clearAndSetSemantics { } else Modifier,
                )) { content(direction) }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = (constraints.maxWidth - sidePaddingPx * 2).coerceAtLeast(0)
        val pitch = pageWidth + sidePaddingPx
        val pages = measurables.map {
            it.measure(constraints.copy(minWidth = pageWidth, maxWidth = pageWidth, minHeight = 0))
        }
        val next = if (state.offset > 0f) pages[0] else pages[2]
        val height = (pages[1].height + (next.height - pages[1].height) * abs(state.progress)).roundToInt()
        layout(constraints.maxWidth, height) {
            pages.forEachIndexed { index, page ->
                page.place(sidePaddingPx + (index - 1) * pitch + state.offset.roundToInt(), 0)
            }
        }
    }
}
