package com.aloyshf.retrodroid.data

import java.util.Locale

enum class GameSort {
    NAME,
    RECENT,
    PLAY_TIME,
    SIZE,
}

/**
 * Orders games by [sort]; unplayed games and equal values keep the title
 * order, so every mode stays predictable.
 */
fun sortGames(
    games: List<RomEntry>,
    sort: GameSort,
    stats: Map<String, GameStat>,
): List<RomEntry> {
    val byName = compareBy<RomEntry> { it.name.lowercase(Locale.ROOT) }
    return when (sort) {
        GameSort.NAME -> games.sortedWith(byName)
        GameSort.RECENT -> games.sortedWith(
            compareByDescending<RomEntry> { stats[it.uri.toString()]?.lastPlayed ?: 0L }.then(byName),
        )

        GameSort.PLAY_TIME -> games.sortedWith(
            compareByDescending<RomEntry> { stats[it.uri.toString()]?.playMillis ?: 0L }.then(byName),
        )

        GameSort.SIZE -> games.sortedWith(compareByDescending<RomEntry> { it.size }.then(byName))
    }
}
