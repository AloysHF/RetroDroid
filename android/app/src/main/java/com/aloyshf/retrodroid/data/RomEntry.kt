package com.aloyshf.retrodroid.data

import android.net.Uri

data class RomEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
)
