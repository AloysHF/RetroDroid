package com.aloyshf.retrodroid.core

import android.view.Surface
import org.json.JSONObject

/**
 * JNI bridge to the Rust libretro frontend. One core can be loaded at a time.
 */
object NativeCore {
    init {
        System.loadLibrary("retro_frontend")
    }

    external fun nativeInit()
    external fun nativeSetLanguage(language: Int)

    external fun nativeLoadCore(
        corePath: String,
        contentPath: String,
        systemDir: String,
        saveDir: String,
        optionsJson: String,
    ): Boolean

    external fun nativeUnload()

    external fun nativeStart(surface: Surface, rotation: Int): Boolean
    external fun nativeStop()
    external fun nativePause(paused: Boolean)

    external fun nativeReset(): Boolean

    external fun nativeSetRotation(rotation: Int)
    external fun nativeAvInfo(): IntArray
    external fun nativeOutputSize(): IntArray

    external fun nativeSetButtons(mask: Int)
    external fun nativeSetKey(androidKeycode: Int, down: Boolean)

    external fun nativeSetOption(key: String, value: String): Boolean
    external fun nativeOptions(): String
    external fun nativeSystemInfo(): String

    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean

    external fun nativeReadAudio(dst: ShortArray, frames: Int): Int
    external fun nativePollEvents(): String

    data class Events(
        val messages: List<String>,
        val shutdown: Boolean,
        val running: Boolean,
        val loaded: Boolean,
        val error: String?,
    )

    fun pollEvents(): Events {
        val json = runCatching { JSONObject(nativePollEvents()) }.getOrNull()
            ?: return Events(emptyList(), false, false, false, null)
        val messages = mutableListOf<String>()
        json.optJSONArray("messages")?.let { array ->
            for (index in 0 until array.length()) {
                messages += array.optString(index)
            }
        }
        return Events(
            messages = messages,
            shutdown = json.optBoolean("shutdown"),
            running = json.optBoolean("running"),
            loaded = json.optBoolean("loaded"),
            error = if (json.isNull("error")) null else json.optString("error").ifBlank { null },
        )
    }
}

/** RetroPad button bits, matching `RETRO_DEVICE_ID_JOYPAD_*`. */
object RetroButton {
    const val B = 1 shl 0
    const val Y = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val UP = 1 shl 4
    const val DOWN = 1 shl 5
    const val LEFT = 1 shl 6
    const val RIGHT = 1 shl 7
    const val A = 1 shl 8
    const val X = 1 shl 9
    const val L = 1 shl 10
    const val R = 1 shl 11
}
