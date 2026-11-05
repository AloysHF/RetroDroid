package com.aloyshf.retrodroid.data

import android.content.Context
import org.json.JSONObject

/** Play history of one game, keyed by its file path in the library. */
data class GameStat(
    val lastPlayed: Long = 0L,
    val playMillis: Long = 0L,
)

/**
 * Tracks when each game was last started and how long it has been played.
 * Library paths are fixed, so the ROM path is a stable key.
 */
class PlayStats(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("play_stats", Context.MODE_PRIVATE)

    fun load(): Map<String, GameStat> = runCatching {
        val json = JSONObject(prefs.getString(KEY, null) ?: "{}")
        buildMap {
            json.keys().forEach { path ->
                val entry = json.optJSONObject(path) ?: return@forEach
                put(
                    path,
                    GameStat(
                        lastPlayed = entry.optLong("lastPlayed"),
                        playMillis = entry.optLong("playMillis"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    fun recordStart(gameKey: String, at: Long) {
        update(gameKey) { it.copy(lastPlayed = at) }
    }

    fun recordSession(gameKey: String, millis: Long) {
        if (millis <= 0) return
        update(gameKey) { it.copy(playMillis = it.playMillis + millis) }
    }

    private fun update(gameKey: String, change: (GameStat) -> GameStat) {
        val current = load()
        val next = change(current[gameKey] ?: GameStat())
        val json = JSONObject()
        current.forEach { (path, stat) -> json.put(path, stat.toJson()) }
        json.put(gameKey, next.toJson())
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun GameStat.toJson(): JSONObject = JSONObject()
        .put("lastPlayed", lastPlayed)
        .put("playMillis", playMillis)

    private companion object {
        const val KEY = "stats"
    }
}
