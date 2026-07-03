package com.playlists.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playlists.app.R
import kotlin.math.abs

private val WhiteKey = Color(0xFFF5F5F5)
private val WhiteKeyPressed = Color(0xFFD0D0D0)
private val BlackKey = Color(0xFF1A1A1A)
private val BlackKeyPressed = Color(0xFF404040)
private val KeyBorder = Color(0xFF888888)

@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    engine: PianoSoundEngine = rememberPianoSoundEngine(),
) {
    val whiteKeyWidth = 38.dp
    val keyboardHeight = 120.dp
    val density = LocalDensity.current
    val whiteKeyWidthPx = with(density) { whiteKeyWidth.toPx() }
    val blackKeyWidthPx = whiteKeyWidthPx * 0.55f
    val keyboardHeightPx = with(density) { keyboardHeight.toPx() }
    val labelStyle = TextStyle(
        color = Color(0xFF555555),
        fontSize = 10.sp,
    )
    val textMeasurer = rememberTextMeasurer()

    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var scrollInitialized by remember { mutableStateOf(false) }
    var pressedMidi by remember { mutableIntStateOf(-1) }

    val keyboardContent = @Composable {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val maxScrollPx = PianoLayout.maxScrollPx(whiteKeyWidthPx, viewportWidthPx)

            LaunchedEffect(viewportWidthPx) {
                if (!scrollInitialized && viewportWidthPx > 0f) {
                    scrollOffsetPx = PianoLayout.initialScrollPx(whiteKeyWidthPx, viewportWidthPx)
                    scrollInitialized = true
                }
            }
            scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScrollPx)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyboardHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A))
                    .pointerInput(maxScrollPx, whiteKeyWidthPx, keyboardHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val scrollAtDown = scrollOffsetPx
                            var scrolling = false
                            var activeMidi: Int? = PianoLayout.midiAt(
                                contentX = downPos.x + scrollAtDown,
                                y = downPos.y,
                                whiteKeyWidthPx = whiteKeyWidthPx,
                                keyboardHeightPx = keyboardHeightPx,
                                blackKeyWidthPx = blackKeyWidthPx,
                            )
                            activeMidi?.let { midi ->
                                pressedMidi = midi
                                engine.noteOn(midi)
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    activeMidi?.let { midi ->
                                        if (pressedMidi == midi) pressedMidi = -1
                                        engine.noteOff(midi)
                                    }
                                    break
                                }

                                val delta = change.position - down.position
                                if (!scrolling &&
                                    abs(delta.x) > viewConfiguration.touchSlop &&
                                    abs(delta.x) > abs(delta.y) * 1.1f
                                ) {
                                    scrolling = true
                                    activeMidi?.let { midi ->
                                        if (pressedMidi == midi) pressedMidi = -1
                                        engine.noteOff(midi)
                                    }
                                    activeMidi = null
                                }

                                if (scrolling) {
                                    scrollOffsetPx = (scrollAtDown - (change.position.x - down.position.x))
                                        .coerceIn(0f, maxScrollPx)
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                val scroll = scrollOffsetPx
                val blackHeight = size.height * 0.62f
                val corner = 4f

                for (midi in PianoLayout.MIN_MIDI..PianoLayout.MAX_MIDI) {
                    if (!PianoLayout.isWhiteKey(midi)) continue
                    val x = PianoLayout.whiteKeyX(midi, whiteKeyWidthPx) - scroll
                    if (x + whiteKeyWidthPx < 0f || x > size.width) continue
                    val pressed = midi == pressedMidi
                    drawRoundRect(
                        color = if (pressed) WhiteKeyPressed else WhiteKey,
                        topLeft = Offset(x, 0f),
                        size = Size(whiteKeyWidthPx - 1f, size.height),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    drawLine(
                        color = KeyBorder,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    PianoLayout.cLabel(midi)?.let { label ->
                        val textLayout = textMeasurer.measure(label, labelStyle)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                x + (whiteKeyWidthPx - textLayout.size.width) / 2f,
                                size.height - textLayout.size.height - 6f,
                            ),
                        )
                    }
                }

                for (midi in PianoLayout.MIN_MIDI..PianoLayout.MAX_MIDI) {
                    if (!PianoLayout.isBlackKey(midi)) continue
                    val x = PianoLayout.blackKeyX(midi, whiteKeyWidthPx) - scroll
                    if (x + blackKeyWidthPx < 0f || x > size.width) continue
                    val pressed = midi == pressedMidi
                    drawRoundRect(
                        color = if (pressed) BlackKeyPressed else BlackKey,
                        topLeft = Offset(x, 0f),
                        size = Size(blackKeyWidthPx, blackHeight),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                }
            }
        }
    }

    if (embedded) {
        keyboardContent()
        return
    }

    Card(
        modifier = modifier.padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.piano_keyboard_label),
                style = MaterialTheme.typography.titleSmall,
            )
            keyboardContent()
        }
    }
}
