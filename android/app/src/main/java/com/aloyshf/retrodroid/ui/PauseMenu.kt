package com.aloyshf.retrodroid.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.SaveSlot
import com.aloyshf.retrodroid.data.SaveSlotInfo
import com.aloyshf.retrodroid.data.SaveStateStore
import com.aloyshf.retrodroid.data.formatSlotTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class PausePage {
    MAIN,
    SAVE,
    LOAD,
    SETTINGS,
}

@Composable
fun PauseOverlay(
    page: PausePage,
    store: SaveStateStore,
    version: Int,
    onPage: (PausePage) -> Unit,
    onResume: () -> Unit,
    onScreenshot: () -> Unit,
    onReset: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    onSave: (SaveSlot) -> Unit,
    onLoad: (SaveSlot) -> Unit,
    onDelete: (SaveSlot) -> Unit,
    settings: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        when (page) {
            PausePage.MAIN -> PauseMain(
                onResume = onResume,
                onSavePage = { onPage(PausePage.SAVE) },
                onLoadPage = { onPage(PausePage.LOAD) },
                onScreenshot = onScreenshot,
                onSettings = { onPage(PausePage.SETTINGS) },
                onReset = onReset,
                onOptions = onOptions,
                onExit = onExit,
            )

            PausePage.SAVE, PausePage.LOAD -> SaveSlotsPage(
                page = page,
                store = store,
                version = version,
                onBack = { onPage(PausePage.MAIN) },
                onSave = onSave,
                onLoad = onLoad,
                onDelete = onDelete,
            )

            PausePage.SETTINGS -> settings()
        }
    }
}

@Composable
private fun PauseMain(
    onResume: () -> Unit,
    onSavePage: () -> Unit,
    onLoadPage: () -> Unit,
    onScreenshot: () -> Unit,
    onSettings: () -> Unit,
    onReset: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(320.dp).heightIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 18.dp),
        ) {
            Text(
                stringResource(R.string.paused),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            MenuEntry(stringResource(R.string.resume), onResume)
            MenuEntry(stringResource(R.string.save_state), onSavePage)
            MenuEntry(stringResource(R.string.load_state), onLoadPage)
            MenuEntry(stringResource(R.string.screenshot), onScreenshot)
            MenuEntry(stringResource(R.string.settings_title), onSettings)
            MenuEntry(stringResource(R.string.reset), onReset)
            MenuEntry(stringResource(R.string.core_options), onOptions)
            MenuEntry(stringResource(R.string.exit_game), onExit)
        }
    }
}

@Composable
private fun SaveSlotsPage(
    page: PausePage,
    store: SaveStateStore,
    version: Int,
    onBack: () -> Unit,
    onSave: (SaveSlot) -> Unit,
    onLoad: (SaveSlot) -> Unit,
    onDelete: (SaveSlot) -> Unit,
) {
    var slots by remember { mutableStateOf<Map<SaveSlot, SaveSlotInfo>>(emptyMap()) }
    var pendingDelete by remember { mutableStateOf<SaveSlot?>(null) }
    val saving = page == PausePage.SAVE

    LaunchedEffect(page, version) {
        slots = withContext(Dispatchers.IO) { store.snapshot() }
    }

    pendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.slot_delete_title)) },
            text = { Text(stringResource(R.string.slot_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(slot)
                }) { Text(stringResource(R.string.slot_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(if (saving) R.string.save_state else R.string.load_state),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!saving) {
                    item(key = SaveSlot.AUTO.fileKey) {
                        SlotRow(
                            slot = SaveSlot.AUTO,
                            info = slots[SaveSlot.AUTO],
                            saving = false,
                            preview = store.previewFile(SaveSlot.AUTO),
                            onSave = {},
                            onLoad = { onLoad(SaveSlot.AUTO) },
                            onDelete = { pendingDelete = SaveSlot.AUTO },
                        )
                    }
                }
                items(SaveSlot.manual, key = { it.fileKey }) { slot ->
                    SlotRow(
                        slot = slot,
                        info = slots[slot],
                        saving = saving,
                        preview = store.previewFile(slot),
                        onSave = { onSave(slot) },
                        onLoad = { onLoad(slot) },
                        onDelete = { pendingDelete = slot },
                    )
                }
                if (saving) {
                    item(key = "auto-note") {
                        Text(
                            stringResource(R.string.slot_auto_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(
    slot: SaveSlot,
    info: SaveSlotInfo?,
    saving: Boolean,
    preview: File,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val exists = info?.exists == true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlotThumbnail(
                file = if (exists) preview else null,
                modifier = Modifier.size(96.dp, 72.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(slotLabel(slot), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (exists) formatSlotTime(info.updatedAt) else stringResource(R.string.slot_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exists) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.slot_delete)) }
            }
            TextButton(
                onClick = if (saving) onSave else onLoad,
                enabled = saving || exists,
            ) {
                Text(
                    stringResource(
                        when {
                            saving && exists -> R.string.slot_overwrite
                            saving -> R.string.slot_save
                            else -> R.string.slot_load
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun slotLabel(slot: SaveSlot): String =
    if (slot.number == null) stringResource(R.string.slot_auto)
    else stringResource(R.string.slot_number, slot.number)

@Composable
fun SlotThumbnail(file: File?, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            if (file != null && file.isFile) {
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            } else {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Fit)
        } ?: Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ResumeDialog(
    store: SaveStateStore,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
) {
    Dialog(onDismissRequest = onStartOver) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.width(320.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.resume_title), style = MaterialTheme.typography.titleMedium)
                SlotThumbnail(
                    file = store.previewFile(SaveSlot.AUTO),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                val updated = store.stateFile(SaveSlot.AUTO).lastModified()
                Text(
                    stringResource(R.string.resume_body, formatSlotTime(updated)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onStartOver) { Text(stringResource(R.string.resume_new)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onContinue) { Text(stringResource(R.string.resume_continue)) }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
    }
}
