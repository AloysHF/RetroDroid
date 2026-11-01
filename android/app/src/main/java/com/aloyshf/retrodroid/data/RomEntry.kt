package com.aloyshf.retrodroid.data

import android.net.Uri

/**
 * A game found below ROMs/<system>/. [dir] is the folder that holds it,
 * relative to the system folder and '/'-separated ("" for the system root), so
 * the game list can be walked folder by folder.
 */
data class RomEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dir: String = "",
)
