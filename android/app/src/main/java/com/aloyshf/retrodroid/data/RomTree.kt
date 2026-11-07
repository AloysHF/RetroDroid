package com.aloyshf.retrodroid.data

import java.util.Locale

/**
 * Walks the flat result of [Library.scanGames] one folder at a time. Folder
 * paths are relative to ROMS/<system>/, '/'-separated, and "" is the system
 * root. Only folders that hold a supported game somewhere below them exist,
 * so the tree never shows empty directories.
 */
object RomTree {
    /** Immediate sub folders of [dir], ascending by name. */
    fun folders(entries: List<RomEntry>, dir: String): List<String> =
        entries.asSequence()
            .flatMap { chain(it.dir).asSequence() }
            .distinct()
            .filter { parent(it) == dir }
            .sortedBy { name(it).lowercase(Locale.ROOT) }
            .toList()

    /** Games lying directly in [dir], ascending by name. */
    fun games(entries: List<RomEntry>, dir: String): List<RomEntry> =
        entries.filter { it.dir == dir }.sortedBy { it.name.lowercase(Locale.ROOT) }

    /** Number of games in [dir] and everything below it. */
    fun countBelow(entries: List<RomEntry>, dir: String): Int =
        if (dir.isEmpty()) {
            entries.size
        } else {
            entries.count { it.dir == dir || it.dir.startsWith("$dir/") }
        }

    /** Folder one level above [dir]; the root's parent is the root itself. */
    fun parent(dir: String): String = dir.substringBeforeLast('/', "")

    /** Last path segment of [dir], used as the folder's title. */
    fun name(dir: String): String = dir.substringAfterLast('/')

    /** True when [dir] still exists in [entries], e.g. after a refresh. */
    fun exists(entries: List<RomEntry>, dir: String): Boolean =
        dir.isEmpty() || countBelow(entries, dir) > 0

    /** [dir] itself plus every folder above it. */
    private fun chain(dir: String): List<String> {
        if (dir.isEmpty()) return emptyList()
        val parts = dir.split('/')
        return (1..parts.size).map { parts.subList(0, it).joinToString("/") }
    }
}
