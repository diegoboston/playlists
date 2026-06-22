package com.playlists.app.ui.reorder

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun DraggableItem(
    key: String,
    enabled: Boolean,
    draggingKey: String?,
    onDragStart: (String) -> Unit,
    onDrag: (String, Float) -> Unit,
    onDragEnd: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isDragging = draggingKey == key
    var dragOffsetY by remember(key) { mutableFloatStateOf(0f) }
    var itemTopInRoot by remember(key) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    itemTopInRoot = coords.positionInRoot().y
                }
            }
            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    shadowElevation = 8f
                    alpha = 0.95f
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(key) {
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val downPos = down.position

                            val longPress = withTimeoutOrNull(longPressTimeout) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeoutOrNull false
                                    if (!change.pressed) return@withTimeoutOrNull false
                                    if ((change.position - downPos).getDistance() > touchSlop) {
                                        return@withTimeoutOrNull false
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                true
                            } != false

                            if (!longPress) {
                                onClick?.invoke()
                                return@awaitEachGesture
                            }

                            val dragStartTop = itemTopInRoot
                            dragOffsetY = 0f
                            onDragStart(key)
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dy = change.position.y - change.previousPosition.y
                                if (dy != 0f) {
                                    change.consume()
                                    dragOffsetY += dy
                                    onDrag(key, dragStartTop + dragOffsetY)
                                }
                            }

                            dragOffsetY = 0f
                            onDragEnd(key)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
