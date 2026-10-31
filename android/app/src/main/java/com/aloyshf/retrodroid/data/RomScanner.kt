package com.aloyshf.retrodroid.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RomEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
)

object RomScanner {
    /**
     * Recursively lists files with a supported extension below [treeUri].
     * Runs on IO; the caller decides how to present progress.
     */
    suspend fun scan(
        context: Context,
        treeUri: Uri,
        extensions: Set<String>,
        limit: Int = 4000,
    ): List<RomEntry> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val results = mutableListOf<RomEntry>()
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        queue.addLast(root to 0)

        while (queue.isNotEmpty() && results.size < limit) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.name?.startsWith('.') == true) continue
                if (child.isDirectory) {
                    if (depth < 8) queue.addLast(child to depth + 1)
                } else {
                    val name = child.name ?: continue
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension.isNotEmpty() && extensions.any { it.equals(extension, ignoreCase = true) }) {
                        results += RomEntry(child.uri, name, child.length())
                    }
                }
            }
        }
        results.sortBy { it.name.lowercase() }
        results
    }

    fun folderName(context: Context, treeUri: Uri): String? =
        DocumentFile.fromTreeUri(context, treeUri)?.name

    /**
     * Files that sit in the same directory as [target]. Cores for CD-based
     * systems open a cue sheet that names its tracks, so the tracks have to
     * land beside the copy of the cue sheet in the cache.
     */
    suspend fun siblings(context: Context, treeUri: Uri, target: Uri): List<RomEntry> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val queue = ArrayDeque<DocumentFile>()
            queue.addLast(root)
            var visited = 0
            while (queue.isNotEmpty() && visited++ < LIMIT_DIRECTORIES) {
                val dir = queue.removeFirst()
                val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                val files = children.filter { !it.isDirectory }
                if (files.any { it.uri == target }) {
                    return@withContext files.mapNotNull { file ->
                        file.name?.let { RomEntry(file.uri, it, file.length()) }
                    }
                }
                children.filter { it.isDirectory }.forEach(queue::addLast)
            }
            emptyList()
        }

    private const val LIMIT_DIRECTORIES = 4000
}
