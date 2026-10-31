package com.aloyshf.retrodroid.data

import android.content.Context
import com.aloyshf.retrodroid.model.SystemInfo
import org.json.JSONObject

class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("retrodroid", Context.MODE_PRIVATE)

    fun treeUri(systemId: String): String? = prefs.getString("tree_$systemId", null)

    fun setTreeUri(systemId: String, uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove("tree_$systemId") else putString("tree_$systemId", uri)
        }.apply()
    }

    fun optionsJson(core: String): String =
        prefs.getString("options_$core", null) ?: "{}"

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
}

enum class Scaling {
    FIT,
    STRETCH,
    INTEGER,
}
