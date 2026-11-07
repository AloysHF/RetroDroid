package com.aloyshf.retrodroid.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.aloyshf.retrodroid.AppLocale
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.GameLayout
import com.aloyshf.retrodroid.data.GameSort
import com.aloyshf.retrodroid.data.GameStat
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.data.PlayStats
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.RomTree
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.data.sortGames
import com.aloyshf.retrodroid.model.FirmwareFile
import com.aloyshf.retrodroid.model.ModelOption
import com.aloyshf.retrodroid.model.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECENT_LIMIT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    system: SystemInfo,
    folder: String,
    onFolder: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (RomEntry?) -> Unit,
) {
    val context = LocalContext.current
    val language = AppLocale.language(context)
    val importedMessage = stringResource(R.string.firmware_imported)
    val failedMessage = stringResource(R.string.firmware_failed)
    val prefs = remember { Prefs(context) }
    val playStats = remember { PlayStats(context) }
    val layout = remember { prefs.gameLayout() }

    var hasAccess by remember { mutableStateOf(Library.hasAccess(context)) }
    var games by remember { mutableStateOf<List<RomEntry>>(emptyList()) }
    var stats by remember { mutableStateOf<Map<String, GameStat>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(prefs.gameSort()) }
    var scanning by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var firmwareVersion by remember { mutableIntStateOf(0) }
    val models = remember(system) { system.models() }
    var model by remember(system.id) {
        mutableStateOf(
            // Ignore a stored value the profile no longer offers so the panel
            // never starts with nothing selected.
            system.defaultBootValue(prefs).takeIf { value -> models.any { it.value == value } }
                ?: models.firstOrNull()?.value
                ?: "",
        )
    }

    OnResume {
        hasAccess = Library.hasAccess(context)
        refreshKey++
    }

    // A search is the innermost step back should undo, then the folder walk.
    BackHandler(enabled = folder.isNotEmpty() || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else onFolder(RomTree.parent(folder))
    }

    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val target = SessionFiles.systemDir(context)
        val expectedByName = system.firmware.groupBy { File(it.file).name }
        var copied = 0
        for (uri in uris) {
            val name = (DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment)?.takeIf { it.isNotBlank() } ?: continue
            val fileName = File(name).name
            // Firmware names are case-sensitive; one basename may exist for
            // several model folders (BBK A4980/A4988), so copy to each match.
            val destinations = expectedByName[fileName] ?: continue
            val ok = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                destinations.forEach { requirement ->
                    val out = File(target, requirement.file)
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
                if (system.noGame) emptyList() else Library.scanGames(system.id, system.extensions)
            } else {
                emptyList()
            }
        }
        stats = withContext(Dispatchers.IO) { playStats.load() }
        hasAccess = Library.hasAccess(context)
        scanning = false
        firmwareVersion++
    }

    // A refresh or a deleted folder can leave us standing somewhere that no
    // longer holds games; fall back to the system root in that case.
    LaunchedEffect(games, scanning) {
        if (!scanning && !RomTree.exists(games, folder)) onFolder("")
    }

    // Firmware status of every model, so the panel can show which models are
    // bootable instead of only the selected one. Systems without models fall
    // back to the single entry keyed by an empty value.
    val missingByModel = remember(firmwareVersion, models) {
        val systemDir = SessionFiles.systemDir(context)
        fun state(requirements: List<FirmwareFile>): ModelFirmwareState {
            val absent = requirements.filter { requirement -> !File(systemDir, requirement.file).exists() }
            return ModelFirmwareState(
                missingRequired = absent.filterNot { it.optional },
                missingOptional = absent.filter { it.optional },
            )
        }
        if (models.isEmpty()) {
            mapOf("" to state(system.firmware.filter { it.required }))
        } else {
            models.associate { option -> option.value to state(option.firmware) }
        }
    }
    val missingFirmware = missingByModel[model]?.missingRequired.orEmpty()

    // The home screen already names the absolute library root, so the bar only
    // needs the short path — an absolute one gets ellipsized before the folder
    // you are standing in.
    val folderPath = listOf("ROMS", system.id, folder)
        .filter { it.isNotEmpty() }
        .joinToString("/")
    // A query searches the whole system, so games buried in subfolders are
    // found without walking into them; the results are shown flat.
    val trimmedQuery = query.trim()
    val searching = trimmedQuery.isNotEmpty()
    val folders = remember(games, folder) { RomTree.folders(games, folder) }
    val folderGames = remember(games, folder) { RomTree.games(games, folder) }
    val visibleGames = remember(games, folder, sort, stats, trimmedQuery) {
        val source = if (searching) {
            games.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        } else {
            folderGames
        }
        sortGames(source, sort, stats)
    }
    val folderCounts = remember(games, folders) {
        folders.associateWith { RomTree.countBelow(games, it) }
    }
    val totalGames = remember(games, folder) { RomTree.countBelow(games, folder) }
    val atRoot = folder.isEmpty()
    // Shortcuts across the whole system once per root visit; subfolders hide
    // them because the folder walk itself is the navigation there.
    val recentGames = remember(games, stats, searching, atRoot) {
        if (searching || !atRoot) {
            emptyList()
        } else {
            sortGames(games, GameSort.RECENT, stats)
                .filter { (stats[it.uri.toString()]?.lastPlayed ?: 0L) > 0L }
                .take(RECENT_LIMIT)
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
                    // One level up inside the library, out of the system at its root.
                    TextButton(onClick = { if (atRoot) onBack() else onFolder(RomTree.parent(folder)) }) {
                        Text(if (atRoot) "←" else "↑", style = MaterialTheme.typography.titleLarge)
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
            if (system.firmware.any { it.required } || missingFirmware.isNotEmpty()) {
                FirmwareBanner(
                    names = missingFirmware.map { Library.biosRelativePath(system, it.file) },
                    hints = missingFirmware.map { it.hint(language) }.filter { it.isNotBlank() },
                    ready = missingFirmware.isEmpty(),
                    folderHint = stringResource(R.string.bios_hint, Library.biosDir(system.id).absolutePath),
                    onImport = { firmwarePicker.launch(arrayOf("*/*")) },
                )
            }

            if (system.noGame) {
                // Several model rows can exceed a short screen, so the panel
                // scrolls on its own instead of being clipped.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BootPanel(
                        system = system,
                        language = language,
                        models = models,
                        selected = model,
                        missingByModel = missingByModel,
                        onSelect = { value ->
                            system.bootOptions.firstOrNull()?.key?.let { key ->
                                prefs.setOption(system.core, key, value)
                            }
                            model = value
                        },
                        onStart = { onPlay(null) },
                    )
                }
            } else {
                BrowseControls(
                    query = query,
                    onQueryChange = { query = it },
                    sort = sort,
                    onSortChange = { option ->
                        sort = option
                        prefs.setGameSort(option)
                    },
                )
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

                    searching && visibleGames.isEmpty() -> EmptyState(
                        text = stringResource(R.string.no_search_results, trimmedQuery),
                        action = stringResource(R.string.cancel),
                        onAction = { query = "" },
                    )

                    layout == GameLayout.GRID -> GameGridContent(
                        folders = if (searching) emptyList() else folders,
                        folderCounts = folderCounts,
                        recent = recentGames,
                        games = visibleGames,
                        count = if (searching) visibleGames.size else totalGames,
                        stats = stats,
                        showFolder = searching,
                        onFolder = onFolder,
                        onPlay = onPlay,
                    )

                    else -> GameListContent(
                        folders = if (searching) emptyList() else folders,
                        folderCounts = folderCounts,
                        recent = recentGames,
                        games = visibleGames,
                        count = if (searching) visibleGames.size else totalGames,
                        stats = stats,
                        showFolder = searching,
                        onFolder = onFolder,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseControls(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: GameSort,
    onSortChange: (GameSort) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.game_search)) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { onQueryChange("") }, contentPadding = PaddingValues(0.dp)) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
        )
        Box {
            TextButton(onClick = { sortMenu = true }) {
                Text(stringResource(R.string.game_sort))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                GameSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option))) },
                        onClick = {
                            sortMenu = false
                            onSortChange(option)
                        },
                        trailingIcon = {
                            if (option == sort) Text("✓", color = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameListContent(
    folders: List<String>,
    folderCounts: Map<String, Int>,
    recent: List<RomEntry>,
    games: List<RomEntry>,
    count: Int,
    stats: Map<String, GameStat>,
    showFolder: Boolean,
    onFolder: (String) -> Unit,
    onPlay: (RomEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { GameCountHeader(count) }
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sort_recent)) }
            items(recent, key = { "recent:${it.uri}" }) { entry ->
                GameRow(
                    entry = entry,
                    stat = stats[entry.uri.toString()] ?: GameStat(),
                    showFolder = true,
                ) { onPlay(entry) }
            }
        }
        items(folders, key = { "folder:$it" }) { path ->
            FolderRow(
                name = RomTree.name(path),
                count = folderCounts[path] ?: 0,
            ) { onFolder(path) }
        }
        items(games, key = { it.uri.toString() }) { entry ->
            GameRow(
                entry = entry,
                stat = stats[entry.uri.toString()] ?: GameStat(),
                showFolder = showFolder,
            ) { onPlay(entry) }
        }
    }
}

@Composable
private fun GameGridContent(
    folders: List<String>,
    folderCounts: Map<String, Int>,
    recent: List<RomEntry>,
    games: List<RomEntry>,
    count: Int,
    stats: Map<String, GameStat>,
    showFolder: Boolean,
    onFolder: (String) -> Unit,
    onPlay: (RomEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { GameCountHeader(count) }
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.sort_recent))
            }
            gridItems(recent, key = { "recent:${it.uri}" }) { entry ->
                GameTile(
                    entry = entry,
                    stat = stats[entry.uri.toString()] ?: GameStat(),
                    showFolder = true,
                ) { onPlay(entry) }
            }
        }
        gridItems(folders, key = { "folder:$it" }, span = { GridItemSpan(maxLineSpan) }) { path ->
            FolderRow(
                name = RomTree.name(path),
                count = folderCounts[path] ?: 0,
            ) { onFolder(path) }
        }
        gridItems(games, key = { it.uri.toString() }) { entry ->
            GameTile(
                entry = entry,
                stat = stats[entry.uri.toString()] ?: GameStat(),
                showFolder = showFolder,
            ) { onPlay(entry) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun GameCountHeader(count: Int) {
    Text(
        pluralStringResource(R.plurals.game_count, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun GameTile(
    entry: RomEntry,
    stat: GameStat,
    showFolder: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FolderHint(entry = entry, showFolder = showFolder)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    gameMeta(entry, stat, compact = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▶", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Missing dumps of one model, split by whether the core needs them to boot. */
private data class ModelFirmwareState(
    val missingRequired: List<FirmwareFile> = emptyList(),
    val missingOptional: List<FirmwareFile> = emptyList(),
) {
    val ready: Boolean get() = missingRequired.isEmpty()
}

@Composable
private fun BootPanel(
    system: SystemInfo,
    language: String,
    models: List<ModelOption>,
    selected: String,
    missingByModel: Map<String, ModelFirmwareState>,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
) {
    val label = system.bootOptions.firstOrNull()?.label(language).orEmpty()
    val missing = missingByModel[selected] ?: ModelFirmwareState()
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (label.isNotBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            models.forEach { option ->
                ModelRow(
                    system = system,
                    option = option,
                    selected = option.value == selected,
                    state = missingByModel[option.value] ?: ModelFirmwareState(),
                    onSelect = { onSelect(option.value) },
                )
            }
            Button(
                onClick = onStart,
                enabled = missing.ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.boot_firmware))
            }
            if (!missing.ready) {
                Text(
                    stringResource(R.string.boot_firmware_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One machine model of a firmware-only system. The row is the selector itself:
 * every model the core supports stays visible with its firmware status, so the
 * user can see the other models and which dumps still have to be copied.
 */
@Composable
private fun ModelRow(
    system: SystemInfo,
    option: ModelOption,
    selected: Boolean,
    state: ModelFirmwareState,
    onSelect: () -> Unit,
) {
    val ready = state.ready
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onSelect)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "BIOS/${system.id}/${option.folder}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (ready) {
                    stringResource(R.string.model_firmware_ready)
                } else {
                    stringResource(
                        R.string.model_firmware_missing,
                        state.missingRequired.joinToString(", ") { File(it.file).name },
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.missingOptional.isNotEmpty()) {
                // Optional dumps never block the boot, so they are listed apart
                // from the required ones and do not colour the row as an error.
                Text(
                    stringResource(
                        R.string.model_firmware_optional,
                        state.missingOptional.joinToString(", ") { File(it.file).name },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun SystemInfo.defaultBootValue(prefs: Prefs): String {
    val option = bootOptions.firstOrNull() ?: return ""
    return prefs.option(core, option.key, option.values.first())
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
private fun GameRow(
    entry: RomEntry,
    stat: GameStat,
    showFolder: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FolderHint(entry = entry, showFolder = showFolder)
                val meta = gameMeta(entry, stat)
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("▶", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Which folder a search result came from, so flat results stay traceable. */
@Composable
private fun FolderHint(entry: RomEntry, showFolder: Boolean) {
    if (!showFolder || entry.dir.isEmpty()) return
    Text(
        "${entry.dir}/",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun gameMeta(entry: RomEntry, stat: GameStat, compact: Boolean = false): String {
    val parts = mutableListOf<String>()
    if (entry.size > 0) parts += formatSize(entry.size)
    if (stat.playMillis > 0) parts += formatPlayTime(stat.playMillis)
    if (!compact && stat.lastPlayed > 0) parts += lastPlayedLabel(stat.lastPlayed)
    return parts.joinToString("  ·  ")
}

/**
 * Relative time worded by the app language. DateUtils resolves against the
 * system locale, which would leak Chinese into an English UI (and back).
 */
@Composable
private fun lastPlayedLabel(lastPlayed: Long): String {
    val minutes = ((System.currentTimeMillis() - lastPlayed).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.hours_ago, (minutes / 60).toInt())
        minutes < 7 * 24 * 60 -> stringResource(R.string.days_ago, (minutes / (24 * 60)).toInt())
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(lastPlayed))
    }
}

@Composable
private fun formatPlayTime(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.play_time_less_minute)
        minutes < 60 -> stringResource(R.string.play_time_minutes, minutes.toInt())
        else -> stringResource(R.string.play_time_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

@Composable
private fun FolderRow(name: String, count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$name/",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.game_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

private fun sortLabel(sort: GameSort): Int = when (sort) {
    GameSort.NAME -> R.string.sort_name
    GameSort.RECENT -> R.string.sort_recent
    GameSort.PLAY_TIME -> R.string.sort_play_time
    GameSort.SIZE -> R.string.sort_size
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
