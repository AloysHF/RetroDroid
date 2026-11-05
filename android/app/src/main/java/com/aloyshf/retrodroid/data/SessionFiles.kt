package com.aloyshf.retrodroid.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * App-private directories handed to cores: firmware, saves and save states.
 */
object SessionFiles {
    private fun root(context: Context) = File(context.filesDir, "retrodroid")

    /** Shared system root; every core reads its own fixed subfolder below it. */
    fun systemDir(context: Context): File =
        File(root(context), "system").apply { mkdirs() }

    fun saveDir(context: Context, core: String): File =
        File(root(context), "saves/$core").apply { mkdirs() }

    fun statesDir(context: Context, core: String): File =
        File(root(context), "states/$core").apply { mkdirs() }

    /** Per-game folder holding the manual slots, the automatic slot and previews. */
    fun gameStatesDir(context: Context, core: String, gameName: String, gameUri: Uri): File =
        File(statesDir(context, core), gameKey(gameName, gameUri)).apply { mkdirs() }

    /** Retired single-state file, migrated into the automatic slot on first use. */
    fun legacyStateFile(context: Context, core: String, gameName: String, gameUri: Uri): File =
        File(statesDir(context, core), gameKey(gameName, gameUri) + ".state")

    private fun gameKey(gameName: String, gameUri: Uri): String {
        val base = gameName.substringBeforeLast('.').ifBlank { gameName }
        val safe = sanitize(base)
        val hash = gameUri.toString().hashCode().toUInt().toString(16)
        return "$safe-$hash"
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "game" }
    }
}
