package com.aloyshf.retrodroid.model

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class FirmwareFile(
    val file: String,
    val required: Boolean,
    val hintEn: String,
    val hintZh: String,
) {
    fun hint(language: String): String =
        if (language.startsWith("zh")) hintZh.ifBlank { hintEn } else hintEn
}

data class SystemInfo(
    val id: String,
    val core: String,
    val soName: String,
    val nameEn: String,
    val nameZh: String,
    val shortName: String,
    val manufacturer: String,
    val extensions: List<String>,
    val firmware: List<FirmwareFile>,
    val input: String,
    val orientation: String,
    val notesEn: String,
    val notesZh: String,
) {
    fun name(language: String): String =
        if (language.startsWith("zh")) nameZh.ifBlank { nameEn } else nameEn

    fun notes(language: String): String =
        if (language.startsWith("zh")) notesZh.ifBlank { notesEn } else notesEn

    fun accepts(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension.isNotEmpty() && extensions.any { it.equals(extension, ignoreCase = true) }
    }
}

object Registry {
    fun load(context: Context): List<SystemInfo> {
        val raw = runCatching {
            context.assets.open("registry.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val systems = root.optJSONArray("systems") ?: return emptyList()
        return buildList {
            for (index in 0 until systems.length()) {
                val item = systems.optJSONObject(index) ?: continue
                add(parse(item))
            }
        }
    }

    private fun parse(item: JSONObject): SystemInfo {
        val extensions = mutableListOf<String>()
        item.optJSONArray("extensions")?.let { array ->
            for (index in 0 until array.length()) {
                extensions += array.optString(index)
            }
        }
        val firmware = mutableListOf<FirmwareFile>()
        item.optJSONArray("firmware")?.let { array ->
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val hint = entry.optJSONObject("hint")
                firmware += FirmwareFile(
                    file = entry.optString("file"),
                    required = entry.optBoolean("required", false),
                    hintEn = hint?.optString("en").orEmpty(),
                    hintZh = hint?.optString("zh").orEmpty(),
                )
            }
        }
        val names = item.optJSONObject("names")
        val notes = item.optJSONObject("notes")
        return SystemInfo(
            id = item.optString("id"),
            core = item.optString("core", item.optString("id")),
            soName = item.optString("so"),
            nameEn = names?.optString("en") ?: item.optString("id"),
            nameZh = names?.optString("zh") ?: names?.optString("en") ?: item.optString("id"),
            shortName = item.optString("short", item.optString("id")),
            manufacturer = item.optString("manufacturer"),
            extensions = extensions,
            firmware = firmware,
            input = item.optString("input", "dpad-ab-select"),
            orientation = item.optString("orientation", "landscape"),
            notesEn = notes?.optString("en").orEmpty(),
            notesZh = notes?.optString("zh").orEmpty(),
        )
    }
}
