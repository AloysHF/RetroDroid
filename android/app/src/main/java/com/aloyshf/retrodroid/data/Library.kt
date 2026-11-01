package com.aloyshf.retrodroid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.aloyshf.retrodroid.model.SystemInfo
import java.io.File
import java.util.Locale

/**
 * The fixed, user-visible game library at /sdcard/RetroDroid:
 * ROMs live in ROMs/<system>/ and firmware in BIOS/<system>/<model>/, mirroring
 * the paths the core profiles declare.
 */
object Library {
    private const val ROOT_NAME = "RetroDroid"
    private const val MAX_DEPTH = 8
    private const val MAX_GAMES = 4000

    fun root(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    fun romsDir(): File = File(root(), "ROMs")

    fun romDir(systemId: String): File = File(romsDir(), systemId)

    fun biosDir(): File = File(root(), "BIOS")

    fun biosDir(systemId: String): File = File(biosDir(), systemId)

    /**
     * Path of a firmware file below BIOS/<system>/. Profiles may wrap every
     * file in a shared top-level folder (WQX keeps its models below
     * WQXEmu/); that wrapper is a core detail and is dropped from the
     * user-visible layout.
     */
    fun biosRelativePath(system: SystemInfo, requirementFile: String): String {
        val parts = system.firmware.map { it.file.split('/') }
        val wrapper = parts.firstOrNull()?.firstOrNull()
            ?.takeIf { name -> parts.all { it.size >= 2 && it.first() == name } }
        return requirementFile.removePrefix(wrapper?.plus("/") ?: "")
    }

    fun biosFile(system: SystemInfo, requirementFile: String): File =
        File(biosDir(system.id), biosRelativePath(system, requirementFile))

    fun ensureLayout(systems: List<SystemInfo>) {
        romsDir().mkdirs()
        systems.forEach { system ->
            romDir(system.id).mkdirs()
            if (system.firmware.isEmpty()) return@forEach
            biosDir(system.id).mkdirs()
            system.firmware.forEach { requirement ->
                biosFile(system, requirement.file).parentFile?.mkdirs()
            }
        }
    }

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun scanGames(systemId: String, extensions: List<String>): List<RomEntry> {
        val supported = extensions.map { it.lowercase(Locale.ROOT) }.toSet()
        val start = romDir(systemId)
        if (!start.isDirectory) return emptyList()

        val results = mutableListOf<RomEntry>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(start to 0)
        while (queue.isNotEmpty() && results.size < MAX_GAMES) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                val name = child.name
                if (name.startsWith('.')) continue
                if (child.isDirectory) {
                    if (depth < MAX_DEPTH) queue.addLast(child to depth + 1)
                    continue
                }
                val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension in supported) {
                    results += RomEntry(Uri.fromFile(child), name, child.length())
                }
            }
        }
        return results.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun countGames(systemId: String, extensions: List<String>): Int = scanGames(systemId, extensions).size

    /**
     * Copies each firmware file from its fixed slot below BIOS/<system>/ into
     * the private directory the core reads from.
     */
    fun syncFirmware(context: Context, system: SystemInfo): Int {
        if (system.firmware.isEmpty()) return 0
        val target = SessionFiles.systemDir(context, system.core)
        var synced = 0

        for (requirement in system.firmware) {
            val source = biosFile(system, requirement.file)
            if (!source.isFile) continue
            val out = File(target, requirement.file)
            val unchanged = out.isFile && out.length() == source.length() && out.lastModified() >= source.lastModified()
            if (unchanged) continue
            runCatching {
                out.parentFile?.mkdirs()
                source.copyTo(out, overwrite = true)
                synced++
            }
        }
        return synced
    }
}
