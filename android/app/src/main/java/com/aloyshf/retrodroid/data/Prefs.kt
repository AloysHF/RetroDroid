package com.aloyshf.retrodroid.data

import android.content.Context
import com.aloyshf.retrodroid.model.SystemInfo
import org.json.JSONObject

class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("retrodroid", Context.MODE_PRIVATE)

    fun optionsJson(core: String): String =
        prefs.getString("options_$core", null) ?: "{}"

    fun option(core: String, key: String, default: String): String =
        runCatching { JSONObject(optionsJson(core)).optString(key, default) }.getOrDefault(default)

    fun setOption(core: String, key: String, value: String) {
        val current = runCatching { JSONObject(optionsJson(core)) }.getOrElse { JSONObject() }
        current.put(key, value)
        prefs.edit().putString("options_$core", current.toString()).apply()
    }

    fun rotation(system: SystemInfo): Int = prefs.getInt("rotation_${system.id}", 0)

    fun setRotation(systemId: String, rotation: Int) {
        prefs.edit().putInt("rotation_$systemId", rotation).apply()
    }

    fun scaling(systemId: String): Scaling =
        Scaling.entries.getOrElse(prefs.getInt("scaling_$systemId", 0)) { Scaling.FIT }

    fun setScaling(systemId: String, scaling: Scaling) {
        prefs.edit().putInt("scaling_$systemId", scaling.ordinal).apply()
    }

    fun gameLayout(): GameLayout =
        GameLayout.entries.getOrElse(prefs.getInt("game_layout", 0)) { GameLayout.LIST }

    fun setGameLayout(layout: GameLayout) {
        prefs.edit().putInt("game_layout", layout.ordinal).apply()
    }

    fun themeMode(): ThemeMode =
        ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", ThemeMode.DARK.ordinal)) { ThemeMode.DARK }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
    }
}

enum class Scaling {
    FIT,
    STRETCH,
    INTEGER,
}

enum class GameLayout {
    LIST,
    GRID,
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}
