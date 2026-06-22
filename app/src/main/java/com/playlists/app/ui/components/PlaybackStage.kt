package com.playlists.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.ui.playback.PlaybackNav
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun PlaybackStage(
    contentKey: Any,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(contentKey) {
        scale = 1f
        offset = Offset.Zero
    }

    val navigationEnabled = scale <= PlaybackNav.ZOOM_NAV_MAX

    fun goPrev() {
        if (canGoPrev) onPrev()
    }

    fun goNext() {
        if (canGoNext) onNext()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !navigationEnabled) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.DirectionDown -> {
                        goNext()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionUp -> {
                        goPrev()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(canGoPrev, canGoNext) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    var start = down.position
                    var tapMoved = false
                    var pinchStartDist = 0f
                    var pinchStartScale = scale
                    var panStart = start
                    var panOffsetStart = offset
                    val zoomedAtStart = scale > PlaybackNav.ZOOM_NAV_MAX

                    if (zoomedAtStart) {
                        panStart = start
                        panOffsetStart = offset
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.isEmpty()) {
                            if (scale <= PlaybackNav.ZOOM_NAV_MAX) {
                                val end = event.changes.firstOrNull { it.id == pointerId }?.position ?: start
                                val dx = end.x - start.x
                                val dy = end.y - start.y
                                when {
                                    abs(dx) >= PlaybackNav.SWIPE_THRESHOLD_PX &&
                                        abs(dx) >= abs(dy) -> {
                                        if (dx < 0) goNext() else goPrev()
                                    }
                                    !tapMoved -> {
                                        if (start.x >= size.width / 2f) goNext() else goPrev()
                                    }
                                }
                            }
                            if (scale < 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            break
                        }

                        when {
                            pressed.size >= 2 -> {
                                tapMoved = true
                                val dist = pointerDistance(pressed[0], pressed[1])
                                if (pinchStartDist > 0f) {
                                    scale = (pinchStartScale * (dist / pinchStartDist))
                                        .coerceIn(1f, PlaybackNav.MAX_ZOOM)
                                    if (scale <= 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                } else {
                                    pinchStartDist = dist
                                    pinchStartScale = scale
                                }
                            }
                            pressed.size == 1 && scale > PlaybackNav.ZOOM_NAV_MAX -> {
                                val change = pressed.first()
                                if (change.id == pointerId) {
                                    offset = panOffsetStart + (change.position - panStart)
                                }
                            }
                            pressed.size == 1 -> {
                                val change = pressed.first()
                                if (change.id == pointerId && change.positionChanged()) {
                                    val dx = change.position.x - start.x
                                    val dy = change.position.y - start.y
                                    if (abs(dx) > PlaybackNav.TAP_MOVE_THRESHOLD_PX ||
                                        abs(dy) > PlaybackNav.TAP_MOVE_THRESHOLD_PX
                                    ) {
                                        tapMoved = true
                                    }
                                }
                            }
                        }

                        if (pressed.size < 2) {
                            pinchStartDist = 0f
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            content()
        }
        if (showHint) {
            Text(
                text = stringResource(R.string.playback_nav_hint),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}

private fun pointerDistance(a: PointerInputChange, b: PointerInputChange): Float =
    hypot(a.position.x - b.position.x, a.position.y - b.position.y)
