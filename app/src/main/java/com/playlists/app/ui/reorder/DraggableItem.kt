package com.playlists.app.ui.reorder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drag-reorder row wrapper ported from NoTube's HistoryScreen.
 * Tap and long-press-drag share one gesture loop so drag never opens the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableItem(
    isDragging: Boolean,
    dragOffset: Float,
    dragEnabled: Boolean = true,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val tapState = rememberUpdatedState(onTap)
    val startState = rememberUpdatedState(onDragStart)
    val dragState = rememberUpdatedState(onDrag)
    val endState = rememberUpdatedState(onDragEnd)
    val cancelState = rememberUpdatedState(onDragCancel)
    val dragEnabledState = rememberUpdatedState(dragEnabled)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture

                    val touchSlop = viewConfiguration.touchSlop
                    var totalDelta = Offset.Zero
                    var releasedUp: PointerInputChange? = null
                    var swiped = false

                    val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes
                                .firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull
                            if (change.changedToUpIgnoreConsumed()) {
                                releasedUp = change
                                return@withTimeoutOrNull
                            }
                            totalDelta += change.positionChange()
                            if (totalDelta.getDistance() > touchSlop) {
                                swiped = true
                                return@withTimeoutOrNull
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        Unit
                    } == null

                    val up = releasedUp
                    when {
                        timedOut && dragEnabledState.value -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            startState.value()
                            try {
                                val ended = drag(down.id) { change ->
                                    dragState.value(change.positionChange().y)
                                    change.consume()
                                }
                                if (ended) endState.value() else cancelState.value()
                            } catch (e: CancellationException) {
                                cancelState.value()
                                throw e
                            }
                        }
                        up != null && !swiped && !up.isConsumed -> {
                            up.consume()
                            tapState.value()
                        }
                    }
                }
            }
            .then(
                if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffset }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
