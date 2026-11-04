package com.aloyshf.retrodroid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val pageUrl: String)

object UpdateChecker {
    const val PAGE_URL = "https://github.com/AloysHF/RetroDroid/releases/latest"
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/AloysHF/RetroDroid/releases/latest"

    /** Returns the newer release, or null when [currentVersion] is up to date. */
    suspend fun check(currentVersion: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "RetroDroid/$currentVersion")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("HTTP ${connection.responseCode}")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val version = json.optString("tag_name").removePrefix("v")
                val page = json.optString("html_url").ifBlank { PAGE_URL }
                version.takeIf { isNewer(it, currentVersion) }?.let { UpdateInfo(it, page) }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        if (latestParts.isEmpty() || currentParts.isEmpty()) return false
        for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
            val newest = latestParts.getOrElse(index) { 0 }
            val installed = currentParts.getOrElse(index) { 0 }
            if (newest != installed) return newest > installed
        }
        return false
    }
}
