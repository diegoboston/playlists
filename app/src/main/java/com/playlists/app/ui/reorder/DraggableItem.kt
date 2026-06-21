package com.playlists.app.ui.reorder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
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
    content: @Composable () -> Unit,
) {
    val isDragging = draggingKey == key
    var dragOffsetY by remember(key) { mutableFloatStateOf(0f) }
    var itemTopInRoot by remember(key) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    itemTopInRoot = coords.positionInRoot().y
                }
            }
            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
            .graphicsLayer {
                if (isDragging) {
                    shadowElevation = 8f
                    alpha = 0.95f
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(key, draggingKey) {
                        var dragStartTop = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragStartTop = itemTopInRoot
                                dragOffsetY = 0f
                                onDragStart(key)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                onDrag(key, dragStartTop + dragOffsetY)
                            },
                            onDragEnd = {
                                dragOffsetY = 0f
                                onDragEnd(key)
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                                onDragEnd(key)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
