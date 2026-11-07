package com.aloyshf.retrodroid.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.aloyshf.retrodroid.model.SystemInfo
import java.io.File

/**
 * A game file an external front-end (Tianma G, ES-DE, ...) asked RetroDroid to
 * play, delivered as a VIEW data URI or a ROM/rom/path/game_path string extra.
 */
data class ExternalLaunchRequest(
    val uri: Uri,
    /** Grows on every launch so the same file can be launched twice in a row. */
    val sequence: Long = 0L,
)

/** What resolving a launch request against the registry produced. */
sealed interface ExternalResolve {
    data class Ready(val system: SystemInfo, val game: RomEntry) : ExternalResolve

    /** Several systems claim this extension (bin/cue/zip); the user picks one. */
    data class Ambiguous(val file: File, val systems: List<SystemInfo>) : ExternalResolve

    /** No system in the registry accepts this file type. */
    data object Unsupported : ExternalResolve

    /** The file is missing or could not be made readable for the core. */
    data object Failed : ExternalResolve
}

object ExternalLaunch {
    private val PATH_EXTRAS = listOf("ROM", "rom", "path", "game_path")

    /**
     * Reads the launch payload out of [intent] and consumes it, so a recreated
     * activity does not replay the same launch. Null when the intent carries no
     * game at all.
     */
    fun consumeFrom(intent: Intent?): ExternalLaunchRequest? {
        intent ?: return null
        val extra = PATH_EXTRAS.firstNotNullOfOrNull { key ->
            intent.getStringExtra(key)?.takeIf(String::isNotBlank)
        }
        if (intent.action != Intent.ACTION_VIEW && extra == null) return null

        val data = intent.data
        val uri = when (data?.scheme) {
            "content", "file" -> data
            else -> extra?.let(Uri::parse)
        }
        intent.action = null
        intent.data = null
        PATH_EXTRAS.forEach(intent::removeExtra)
        return uri?.let { ExternalLaunchRequest(it) }
    }

    /**
     * Maps a launch request onto the registry: the extension picks the system.
     * `content://` URIs are copied into the cache, because the cores open plain
     * file paths.
     */
    fun resolve(context: Context, systems: List<SystemInfo>, request: ExternalLaunchRequest): ExternalResolve {
        val file = runCatching { request.uri.toReadableFile(context) }.getOrNull() ?: return ExternalResolve.Failed
        val matches = systems.filter { it.accepts(file.name) }
        return when (matches.size) {
            0 -> ExternalResolve.Unsupported
            1 -> ExternalResolve.Ready(matches[0], file.romEntry())
            else -> ExternalResolve.Ambiguous(file, matches)
        }
    }

    /** A plain file under shared storage, or a cached copy of a content URI. */
    private fun Uri.toReadableFile(context: Context): File? = when (scheme) {
        "content" -> copyToCache(context, this)
        else -> path?.let(::File)?.takeIf { it.isFile }
    }

    private fun copyToCache(context: Context, uri: Uri): File? {
        val name = displayName(context, uri) ?: return null
        val out = File(File(context.cacheDir, "external"), name)
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        input.use { source ->
            runCatching {
                out.parentFile?.mkdirs()
                out.outputStream().use(source::copyTo)
            }.getOrNull() ?: return null
        }
        return out
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment
            ?: return null
        // A display name is a file name, but a path segment may carry separators.
        return name.substringAfterLast('/').takeIf(String::isNotBlank)
    }
}

/** Library-style entry for a file that is not part of the fixed ROM tree. */
fun File.romEntry(): RomEntry = RomEntry(Uri.fromFile(this), name, length())
