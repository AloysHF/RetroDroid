package com.aloyshf.retrodroid.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.RomScanner
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.model.SystemInfo
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(system: SystemInfo, onBack: () -> Unit, onPlay: (RomEntry, Uri) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val language = Locale.getDefault().language
    val importedMessage = stringResource(R.string.firmware_imported)
    val failedMessage = stringResource(R.string.firmware_failed)

    var folder by remember { mutableStateOf(prefs.treeUri(system.id)?.let(Uri::parse)) }
    var games by remember { mutableStateOf<List<RomEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var firmwareVersion by remember { mutableIntStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.setTreeUri(system.id, uri.toString())
        folder = uri
        refreshKey++
    }

    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val target = SessionFiles.systemDir(context, system.core)
        var copied = 0
        for (uri in uris) {
            val name = (DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment)?.takeIf { it.isNotBlank() } ?: continue
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(target, File(name).name).outputStream().use { output -> input.copyTo(output) }
                } ?: error("unreadable")
            }.isSuccess
            if (ok) copied++
        }
        firmwareVersion++
        Toast.makeText(context, if (copied > 0) importedMessage else failedMessage, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(folder, refreshKey) {
        val current = folder ?: return@LaunchedEffect
        scanning = true
        games = RomScanner.scan(context, current, system.extensions.toSet())
        scanning = false
    }

    val missingFirmware = remember(firmwareVersion) {
        system.firmware.filter { requirement ->
            requirement.required && !File(SessionFiles.systemDir(context, system.core), requirement.file).exists()
        }
    }

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
                            stringResource(R.string.system_extensions, system.extensions.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
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
                    TextButton(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(if (folder == null) R.string.choose_folder else R.string.change_folder))
                    }
                    if (folder != null) {
                        TextButton(onClick = { refreshKey++ }) {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (missingFirmware.isNotEmpty()) {
                FirmwareBanner(
                    names = missingFirmware.map { it.file },
                    hints = missingFirmware.map { it.hint(language) }.filter { it.isNotBlank() },
                    onImport = { firmwarePicker.launch(arrayOf("*/*")) },
                )
            }

            val currentFolder = folder
            when {
                currentFolder == null -> EmptyState(
                    text = stringResource(R.string.no_folder),
                    action = stringResource(R.string.choose_folder),
                    onAction = { folderPicker.launch(null) },
                )

                scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                games.isEmpty() -> EmptyState(
                    text = stringResource(R.string.no_games),
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
                            stringResource(R.string.game_count, games.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(games, key = { it.uri.toString() }) { entry ->
                        GameRow(entry) { onPlay(entry, currentFolder) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirmwareBanner(names: List<String>, hints: List<String>, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.firmware_missing, names.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            hints.forEach { hint ->
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onImport, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.import_firmware))
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
