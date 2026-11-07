package com.aloyshf.retrodroid.ui

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aloyshf.retrodroid.core.RetroButton

/** Deadzone applied to analog stick axes before they map to D-pad bits. */
private const val stickDeadzone = 0.5f

fun isGamepadDevice(device: InputDevice?): Boolean {
    if (device == null || device.isVirtual) return false
    val sources = device.sources
    return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
}

/**
 * Whether a key event source belongs to a controller. DPAD is included so
 * d-pad-only devices (TV-style remotes) also drive games; it must not count
 * for the auto-hide check because a remote is not a gaming controller.
 */
fun isGamepadSource(source: Int): Boolean =
    (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
        (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

fun hasGamepadDevice(context: Context): Boolean {
    val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return false
    return manager.inputDeviceIds.any { isGamepadDevice(InputDevice.getDevice(it)) }
}

/**
 * Tracks whether a physical gamepad is connected, hot-plug aware: the initial
 * state comes from a device scan and every add/remove/change event rescans.
 */
@Composable
fun rememberGamepadConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(hasGamepadDevice(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                if (isGamepadDevice(InputDevice.getDevice(deviceId))) connected = true
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                connected = hasGamepadDevice(context)
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                connected = hasGamepadDevice(context)
            }
        }
        manager.registerInputDeviceListener(listener, null)
        onDispose { manager.unregisterInputDeviceListener(listener) }
    }
    return connected
}

/** Maps an Android gamepad keycode to its RetroPad bit, or null for non-gamepad keys. */
fun gamepadButtonBit(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> RetroButton.A
    KeyEvent.KEYCODE_BUTTON_B -> RetroButton.B
    KeyEvent.KEYCODE_BUTTON_X -> RetroButton.X
    KeyEvent.KEYCODE_BUTTON_Y -> RetroButton.Y
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> RetroButton.L
    KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> RetroButton.R
    KeyEvent.KEYCODE_BUTTON_SELECT -> RetroButton.SELECT
    KeyEvent.KEYCODE_BUTTON_START -> RetroButton.START
    KeyEvent.KEYCODE_DPAD_UP -> RetroButton.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> RetroButton.DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> RetroButton.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> RetroButton.RIGHT
    else -> null
}

/**
 * Maps the left stick and the hat switch of a generic motion event onto the
 * D-pad bits. Key events cover most D-pads already; this adds analog sticks.
 */
fun applyGamepadMotion(event: MotionEvent, state: PadState): Boolean {
    if (event.action != MotionEvent.ACTION_MOVE) return false
    if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
        !event.isFromSource(InputDevice.SOURCE_GAMEPAD)
    ) {
        return false
    }

    val x = event.getAxisValue(MotionEvent.AXIS_X)
    val y = event.getAxisValue(MotionEvent.AXIS_Y)
    val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
    val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

    state.set(RetroButton.LEFT, x < -stickDeadzone || hatX < -stickDeadzone)
    state.set(RetroButton.RIGHT, x > stickDeadzone || hatX > stickDeadzone)
    state.set(RetroButton.UP, y < -stickDeadzone || hatY < -stickDeadzone)
    state.set(RetroButton.DOWN, y > stickDeadzone || hatY > stickDeadzone)
    return true
}
