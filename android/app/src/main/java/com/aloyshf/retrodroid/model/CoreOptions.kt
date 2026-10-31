package com.aloyshf.retrodroid.model

import org.json.JSONArray

data class CoreOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val selected: String,
)

object CoreOptions {
    fun parse(json: String): List<CoreOption> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val values = mutableListOf<String>()
                item.optJSONArray("values")?.let { list ->
                    for (value in 0 until list.length()) {
                        values += list.optString(value)
                    }
                }
                val key = item.optString("key")
                if (key.isBlank() || values.isEmpty()) continue
                add(
                    CoreOption(
                        key = key,
                        label = item.optString("label").ifBlank { key },
                        values = values,
                        selected = item.optString("selected", values.first()),
                    )
                )
            }
        }
    }
}
