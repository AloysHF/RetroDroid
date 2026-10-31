package com.aloyshf.retrodroid.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.core.NativeCore
import com.aloyshf.retrodroid.core.RetroButton

private val overlayText = Color.White.copy(alpha = 0.88f)
private val overlayFill = Color.White.copy(alpha = 0.10f)
private val overlayStroke = Color.White.copy(alpha = 0.16f)

/** Tracks the joypad mask and forwards every change to the native input queue. */
class PadState {
    var mask by mutableIntStateOf(0)
        private set

    fun set(bit: Int, down: Boolean) {
        val next = if (down) mask or bit else mask and bit.inv()
        if (next == mask) return
        mask = next
        NativeCore.nativeSetButtons(next)
    }

    fun clear() {
        if (mask == 0) return
        mask = 0
        NativeCore.nativeSetButtons(0)
    }
}

@Composable
fun ControlOverlay(
    preset: String,
    state: PadState,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (preset == "qwerty") {
            SmallButton(stringResource(R.string.keyboard), { pressed -> if (pressed) onToggleKeyboard() }, Modifier.align(Alignment.BottomStart))
        } else {
            DPad(state, Modifier.align(Alignment.BottomStart))
        }
        FaceCluster(preset, state, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun DPad(state: PadState, modifier: Modifier = Modifier, size: Dp = 152.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(overlayFill, CircleShape)
            .border(1.dp, overlayStroke, CircleShape)
            .pointerInput(Unit) {
                val width = this.size.width
                val height = this.size.height
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    updateDpad(state, down.position, width, height)
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (change.pressed) {
                            updateDpad(state, change.position, width, height)
                            change.consume()
                        } else {
                            active = false
                            change.consume()
                        }
                    }
                    clearDpad(state)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("▲", color = overlayText, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        Text("▼", color = overlayText, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        Text("◀", color = overlayText, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
        Text("▶", color = overlayText, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )
    }
}

private fun updateDpad(state: PadState, position: Offset, width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    val dx = (position.x - width / 2f) / (width / 2f)
    val dy = (position.y - height / 2f) / (height / 2f)
    val threshold = 0.22f
    state.set(RetroButton.UP, dy < -threshold)
    state.set(RetroButton.DOWN, dy > threshold)
    state.set(RetroButton.LEFT, dx < -threshold)
    state.set(RetroButton.RIGHT, dx > threshold)
}

private fun clearDpad(state: PadState) {
    state.set(RetroButton.UP, false)
    state.set(RetroButton.DOWN, false)
    state.set(RetroButton.LEFT, false)
    state.set(RetroButton.RIGHT, false)
}

@Composable
private fun FaceCluster(preset: String, state: PadState, modifier: Modifier = Modifier) {
    val withLr = preset == "dpad-ab-lr-select"
    val withExtra = preset == "dpad-abcd-select"

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (withLr) {
            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                SmallButton("L", { state.set(RetroButton.L, it) })
                SmallButton("R", { state.set(RetroButton.R, it) })
            }
        }
        if (withExtra || preset != "qwerty") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (withExtra) {
                    PadButton("Y", RetroButton.Y, state, size = 50.dp)
                    PadButton("X", RetroButton.X, state, size = 50.dp)
                } else {
                    PadButton("B", RetroButton.B, state, size = 58.dp)
                    PadButton("A", RetroButton.A, state, size = 58.dp)
                }
            }
        }
        if (withExtra) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadButton("B", RetroButton.B, state, size = 58.dp)
                PadButton("A", RetroButton.A, state, size = 58.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(stringResource(R.string.select), { state.set(RetroButton.SELECT, it) })
            SmallButton(stringResource(R.string.start), { state.set(RetroButton.START, it) })
        }
    }
}

@Composable
private fun PadButton(label: String, bit: Int, state: PadState, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, overlayStroke, CircleShape)
            .pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    state.set(bit, true)
                    waitForUpOrCancellation()?.consume()
                    state.set(bit, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = overlayText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(label: String, onPressed: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(overlayFill, RoundedCornerShape(17.dp))
            .border(1.dp, overlayStroke, RoundedCornerShape(17.dp))
            .padding(horizontal = 14.dp)
            .pointerInput(onPressed) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    onPressed(true)
                    waitForUpOrCancellation()?.consume()
                    onPressed(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = overlayText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun KeyboardPanel(modifier: Modifier = Modifier) {
    val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { char ->
                    KeyButton(char.toString(), androidKeyCode(char), Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyButton("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, Modifier.weight(1.4f))
            KeyButton("Space", KeyEvent.KEYCODE_SPACE, Modifier.weight(3f))
            KeyButton("⌫", KeyEvent.KEYCODE_DEL, Modifier.weight(1.2f))
            KeyButton("↵", KeyEvent.KEYCODE_ENTER, Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun KeyButton(label: String, code: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .pointerInput(code) {
                if (code == 0) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    NativeCore.nativeSetKey(code, true)
                    waitForUpOrCancellation()?.consume()
                    NativeCore.nativeSetKey(code, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = overlayText, fontSize = 12.sp, maxLines = 1)
    }
}

private fun androidKeyCode(char: Char): Int = when {
    char in 'A'..'Z' -> KeyEvent.KEYCODE_A + (char - 'A')
    char in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
    else -> 0
}
