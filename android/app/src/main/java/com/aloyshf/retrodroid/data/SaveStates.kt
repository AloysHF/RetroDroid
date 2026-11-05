package com.aloyshf.retrodroid.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/** Automatic slot plus five manual slots, kept per game. */
enum class SaveSlot(val fileKey: String, val number: Int?) {
    AUTO("auto", null),
    SLOT1("1", 1),
    SLOT2("2", 2),
    SLOT3("3", 3),
    SLOT4("4", 4),
    SLOT5("5", 5),
    ;

    companion object {
        val manual: List<SaveSlot> = entries.filter { it.number != null }
    }
}

data class SaveSlotInfo(val exists: Boolean, val updatedAt: Long)

class SaveStateStore(
    private val context: Context,
    private val core: String,
    private val gameName: String,
    private val gameUri: Uri,
) {
    private val dir = SessionFiles.gameStatesDir(context, core, gameName, gameUri)

    init {
        migrateLegacyState()
    }

    fun stateFile(slot: SaveSlot): File = File(dir, "${slot.fileKey}.state")

    fun previewFile(slot: SaveSlot): File = File(dir, "${slot.fileKey}.png")

    fun exists(slot: SaveSlot): Boolean = stateFile(slot).isFile

    fun snapshot(): Map<SaveSlot, SaveSlotInfo> = SaveSlot.entries.associateWith { slot ->
        val file = stateFile(slot)
        SaveSlotInfo(exists = file.isFile, updatedAt = file.lastModified())
    }

    fun read(slot: SaveSlot): ByteArray? =
        runCatching { stateFile(slot).takeIf { it.isFile }?.readBytes() }.getOrNull()

    fun write(slot: SaveSlot, state: ByteArray, preview: Bitmap?): Boolean = runCatching {
        replace(stateFile(slot), state)
        val image = previewFile(slot)
        if (preview != null) {
            val temp = File(image.parentFile, "${image.name}.tmp")
            FileOutputStream(temp).use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
            check(temp.renameTo(image)) { "could not replace ${image.name}" }
        } else {
            image.delete()
        }
        Unit
    }.isSuccess

    fun delete(slot: SaveSlot): Boolean = runCatching {
        stateFile(slot).delete()
        previewFile(slot).delete()
        Unit
    }.isSuccess

    private fun replace(target: File, data: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(data)
        check(temp.renameTo(target)) { "could not replace ${target.name}" }
    }

    private fun migrateLegacyState() {
        val legacy = SessionFiles.legacyStateFile(context, core, gameName, gameUri)
        if (!legacy.isFile) return
        val auto = stateFile(SaveSlot.AUTO)
        if (auto.isFile) {
            legacy.delete()
            return
        }
        legacy.renameTo(auto)
    }
}

fun formatSlotTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
