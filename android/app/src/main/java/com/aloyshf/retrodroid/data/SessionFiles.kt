package com.aloyshf.retrodroid.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * App-private directories handed to cores: firmware, saves and save states.
 */
object SessionFiles {
    private fun root(context: Context) = File(context.filesDir, "retrodroid")

    fun systemDir(context: Context, core: String): File =
        File(root(context), "system/$core").apply { mkdirs() }

    fun saveDir(context: Context, core: String): File =
        File(root(context), "saves/$core").apply { mkdirs() }

    fun statesDir(context: Context, core: String): File =
        File(root(context), "states/$core").apply { mkdirs() }

    fun stateFile(context: Context, core: String, gameName: String, gameUri: Uri): File {
        val base = gameName.substringBeforeLast('.').ifBlank { gameName }
        val safe = sanitize(base)
        val hash = gameUri.toString().hashCode().toUInt().toString(16)
        return File(statesDir(context, core), "$safe-$hash.state")
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "game" }
    }
}
