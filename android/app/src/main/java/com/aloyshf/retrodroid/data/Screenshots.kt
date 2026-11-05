package com.aloyshf.retrodroid.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import com.aloyshf.retrodroid.core.NativeCore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Screenshots {
    /** Latest emulator frame as an opaque bitmap, or null before video starts. */
    fun capture(): Bitmap? {
        val frame = NativeCore.nativeFrameArgb() ?: return null
        if (frame.size < 2) return null
        val width = frame[0]
        val height = frame[1]
        if (width <= 0 || height <= 0) return null
        val stride = width * height
        if (frame.size < stride + 2) return null
        return Bitmap.createBitmap(frame, 2, width, width, height, Bitmap.Config.ARGB_8888)
    }

    fun save(context: Context, bitmap: Bitmap, gameName: String): File? = runCatching {
        val target = directory(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        val file = File(target, "${fileStem(gameName)}-$stamp.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        file
    }.getOrNull()

    /** Screenshots live beside the library when it is readable, so users can find them. */
    private fun directory(context: Context): File =
        if (Library.hasAccess(context)) File(Library.root(), "Screenshots")
        else File(context.filesDir, "retrodroid/screenshots")

    private fun fileStem(gameName: String): String {
        val base = gameName.substringBeforeLast('.').ifBlank { gameName }
        return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "game" }
    }
}
