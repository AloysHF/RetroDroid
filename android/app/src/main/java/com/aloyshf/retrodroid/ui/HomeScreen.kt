package com.aloyshf.retrodroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aloyshf.retrodroid.AppLocale
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.model.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class SystemRowState(
    val count: Int?,
    val firmwareMissing: Boolean,
)

private val systemLogos = mapOf(
    "native32" to R.drawable.logo_native32,
    "coolbar" to R.drawable.logo_coolbar,
    "dingoo" to R.drawable.logo_dingoo,
    "hyperscan" to R.drawable.logo_hyperscan,
    "lavax" to R.drawable.logo_lavax,
    "playdia" to R.drawable.logo_playdia,
    "spmp8000" to R.drawable.logo_spmp8000,
    "wqx" to R.drawable.logo_wqx,
    "bbk" to R.drawable.logo_bbk,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    systems: List<SystemInfo>,
    access: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSystem: (SystemInfo) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val language = AppLocale.language(context)
    var rows by remember { mutableStateOf<Map<String, SystemRowState>>(emptyMap()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    OnResume { refreshKey++ }

    LaunchedEffect(systems, access, refreshKey) {
        rows = withContext(Dispatchers.IO) {
            if (access) {
                Library.ensureLayout(systems)
                systems.forEach { Library.syncFirmware(context, it) }
            }
            systems.associate { system ->
                val count = if (access && !system.noGame) {
                    Library.countGames(system.id, system.extensions)
                } else {
                    null
                }
                val missing = system.firmware.any { requirement ->
                    requirement.required &&
                        !File(SessionFiles.systemDir(context), requirement.file).exists()
                }
                system.id to SystemRowState(count, missing)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        if (systems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.systems_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LibraryCard(access = access, onRequestAccess = onRequestAccess, onOpenHelp = onOpenHelp)
            }
            items(systems, key = { it.id }) { system ->
                val row = rows[system.id]
                SystemCard(
                    system = system,
                    language = language,
                    label = "ROMs/${system.id}",
                    count = row?.count,
                    firmwareMissing = row?.firmwareMissing == true,
                    models = remember(system) { system.models().map { it.label } },
                    onClick = { onOpenSystem(system) },
                )
            }
            item {
                Text(
                    stringResource(R.string.rom_legal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(access: Boolean, onRequestAccess: () -> Unit, onOpenHelp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (access) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.library_root, Library.root().absolutePath),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (access) {
                    stringResource(R.string.library_authorized)
                } else {
                    stringResource(R.string.library_denied, Library.root().absolutePath)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!access) {
                    TextButton(onClick = onRequestAccess, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.library_grant))
                    }
                }
                TextButton(onClick = onOpenHelp, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.help_summary))
                }
            }
        }
    }
}

@Composable
private fun SystemCard(
    system: SystemInfo,
    language: String,
    label: String,
    count: Int?,
    firmwareMissing: Boolean,
    models: List<String>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                )
                val logo = systemLogos[system.id]
                if (logo != null) {
                    Image(
                        painter = painterResource(logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        system.shortName.take(2).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    system.name(language),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    system.manufacturer.takeIf { it.isNotBlank() },
                    // Firmware-only systems have no extensions; skip the
                    // separator instead of leaving a dangling one.
                    system.extensions.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                ).joinToString("  ·  ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (models.isNotEmpty()) {
                    // Systems whose firmware comes in per-model dumps (WQX)
                    // list every supported model, not just the booted one.
                    Text(
                        models.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    when {
                        system.noGame -> stringResource(R.string.boot_firmware)
                        count != null -> pluralStringResource(R.plurals.folder_and_count, count, label, count)
                        else -> label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (firmwareMissing) {
                    Text(
                        stringResource(R.string.firmware_not_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
