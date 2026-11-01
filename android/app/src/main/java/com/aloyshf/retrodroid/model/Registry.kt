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

    /**
     * Folder that holds this file, which is the model folder for per-model
     * dumps (`wqxemu/nc1020/obj_lu.bin` -> `nc1020`).
     */
    fun folder(): String =
        file.substringBeforeLast('/', "").substringAfterLast('/', "")
}

/**
 * One selectable machine model of a system whose firmware comes in per-model
 * dumps, e.g. a WQX NC1020 or PC1000. [value] is the boot option value the core
 * expects, [folder] the folder below `BIOS/<system>/` that holds its dumps.
 */
data class ModelOption(
    val value: String,
    val label: String,
    val folder: String,
    val firmware: List<FirmwareFile>,
)

data class BootOption(
    val key: String,
    val labelEn: String,
    val labelZh: String,
    val values: List<String>,
) {
    fun label(language: String): String =
        if (language.startsWith("zh")) labelZh.ifBlank { labelEn } else labelEn
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
    val noGame: Boolean,
    val bootOptions: List<BootOption>,
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

    /**
     * Machine models offered by the first boot option, each with the firmware
     * files it needs. Empty for systems that are not model-based. Models are
     * labelled in upper case because dumps name them that way (nc1020 ->
     * NC1020), while [ModelOption.folder] keeps the exact on-disk name.
     */
    fun models(): List<ModelOption> {
        val values = bootOptions.firstOrNull()?.values ?: return emptyList()
        return values.map { value ->
            val files = firmware.filter { it.folder() == value }
            ModelOption(
                value = value,
                label = value.uppercase(Locale.ROOT),
                folder = files.firstOrNull()?.folder() ?: value,
                firmware = files,
            )
        }
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
        val bootOptions = mutableListOf<BootOption>()
        item.optJSONArray("bootOptions")?.let { array ->
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val label = entry.optJSONObject("label")
                val values = mutableListOf<String>()
                entry.optJSONArray("values")?.let { choices ->
                    for (choice in 0 until choices.length()) {
                        values += choices.optString(choice)
                    }
                }
                if (values.isEmpty()) continue
                bootOptions += BootOption(
                    key = entry.optString("key"),
                    labelEn = label?.optString("en").orEmpty(),
                    labelZh = label?.optString("zh").orEmpty(),
                    values = values,
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
            noGame = item.optBoolean("noGame", false),
            bootOptions = bootOptions,
            notesEn = notes?.optString("en").orEmpty(),
            notesZh = notes?.optString("zh").orEmpty(),
        )
    }
}
