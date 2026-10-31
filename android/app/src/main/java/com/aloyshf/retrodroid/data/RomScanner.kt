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
}
