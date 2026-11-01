package com.aloyshf.retrodroid.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.model.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    system: SystemInfo,
    onBack: () -> Unit,
    onPlay: (RomEntry) -> Unit,
) {
    val context = LocalContext.current
    val language = Locale.getDefault().language
    val importedMessage = stringResource(R.string.firmware_imported)
    val failedMessage = stringResource(R.string.firmware_failed)

    var hasAccess by remember { mutableStateOf(Library.hasAccess(context)) }
    var games by remember { mutableStateOf<List<RomEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var firmwareVersion by remember { mutableIntStateOf(0) }

    OnResume {
        hasAccess = Library.hasAccess(context)
        refreshKey++
    }

    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val target = SessionFiles.systemDir(context, system.core)
        val expectedByName = system.firmware.groupBy { File(it.file).name.lowercase() }
        var copied = 0
        for (uri in uris) {
            val name = (DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment)?.takeIf { it.isNotBlank() } ?: continue
            val fileName = File(name).name
            // Firmware profiles may name a path below the system directory
            // (WQX keeps one folder per model); match the picked file against
            // those names and create the folders it needs.
            val destinations = expectedByName[fileName.lowercase()]?.map { it.file } ?: listOf(fileName)
            val ok = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                destinations.forEach { relative ->
                    val out = File(target, relative)
                    out.parentFile?.mkdirs()
                    out.writeBytes(bytes)
                }
            }.isSuccess
            if (ok) copied++
        }
        firmwareVersion++
        Toast.makeText(context, if (copied > 0) importedMessage else failedMessage, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(refreshKey) {
        scanning = true
        games = withContext(Dispatchers.IO) {
            val access = Library.hasAccess(context)
            if (access) {
                Library.syncFirmware(context, system)
                Library.scanGames(system.id, system.extensions)
            } else {
                emptyList()
            }
        }
        hasAccess = Library.hasAccess(context)
        scanning = false
        firmwareVersion++
    }

    val missingFirmware = remember(firmwareVersion) {
        system.firmware.filter { requirement ->
            requirement.required && !File(SessionFiles.systemDir(context, system.core), requirement.file).exists()
        }
    }

    val folderPath = Library.romDir(system.id).absolutePath

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.games_title, system.name(language)),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            folderPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { refreshKey++ }) {
                        Text(stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (system.firmware.isNotEmpty()) {
                FirmwareBanner(
                    names = missingFirmware.map { Library.biosRelativePath(system, it.file) },
                    hints = missingFirmware.map { it.hint(language) }.filter { it.isNotBlank() },
                    ready = missingFirmware.isEmpty(),
                    folderHint = stringResource(R.string.bios_hint, Library.biosDir(system.id).absolutePath),
                    onImport = { firmwarePicker.launch(arrayOf("*/*")) },
                )
            }

            when {
                scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                games.isEmpty() -> EmptyState(
                    text = if (!hasAccess) {
                        stringResource(R.string.library_denied, Library.root().absolutePath)
                    } else {
                        stringResource(R.string.roms_hint, Library.romDir(system.id).absolutePath)
                    },
                    action = stringResource(R.string.refresh),
                    onAction = { refreshKey++ },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.game_count, games.size, games.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(games, key = { it.uri.toString() }) { entry ->
                        GameRow(entry) { onPlay(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirmwareBanner(
    names: List<String>,
    hints: List<String>,
    ready: Boolean,
    folderHint: String,
    onImport: () -> Unit,
) {
    val colors = if (ready) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = colors,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (ready) stringResource(R.string.firmware_ready) else stringResource(R.string.firmware_missing, names.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            hints.forEach { hint ->
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ready) {
                Text(
                    folderHint,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onImport, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.import_firmware))
                }
            }
        }
    }
}

@Composable
private fun GameRow(entry: RomEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.size > 0) {
                    Text(
                        formatSize(entry.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("▶", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyState(text: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
