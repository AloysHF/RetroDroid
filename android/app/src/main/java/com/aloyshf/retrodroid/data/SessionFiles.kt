package com.aloyshf.retrodroid.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

data class PreparedContent(val file: File, val cacheDir: File)

/**
 * App-private directories handed to cores. Content itself is copied into a
 * throwaway cache directory because cores need real file paths while SAF only
 * exposes content URIs.
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

    fun prepareContent(context: Context, uri: Uri, displayName: String): PreparedContent {
        val cacheDir = File(context.cacheDir, "retrodroid/${UUID.randomUUID()}")
        cacheDir.mkdirs()
        val target = File(cacheDir, sanitize(displayName))
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected file cannot be read." }
            target.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        }
        return PreparedContent(target, cacheDir)
    }

    fun cleanupCache(cacheDir: File) {
        runCatching { cacheDir.deleteRecursively() }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "game" }
    }
}
