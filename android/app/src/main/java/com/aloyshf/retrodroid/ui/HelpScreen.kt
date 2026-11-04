package com.aloyshf.retrodroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aloyshf.retrodroid.AppLocale
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.model.SystemInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(systems: List<SystemInfo>, onBack: () -> Unit) {
    val language = AppLocale.language(LocalContext.current)
    val gamesSystems = systems.filter { !it.noGame }
    val firmwareSystems = systems.filter { it.firmware.isNotEmpty() }
    val firmwareOnly = systems.filter { it.noGame }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HelpSection(stringResource(R.string.help_section_setup)) {
                Text(
                    stringResource(R.string.library_root, Library.root().absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(stringResource(R.string.help_setup_access), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.help_setup_structure), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.help_setup_tree),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.help_setup_refresh), style = MaterialTheme.typography.bodyMedium)
            }

            HelpSection(stringResource(R.string.help_section_games)) {
                Text(stringResource(R.string.help_games_intro), style = MaterialTheme.typography.bodyMedium)
                gamesSystems.forEach { system ->
                    GamesFolderRow(system = system, language = language)
                }
                Text(
                    stringResource(R.string.help_games_subfolders),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.help_games_companions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (firmwareSystems.isNotEmpty()) {
                HelpSection(stringResource(R.string.help_section_firmware)) {
                    Text(stringResource(R.string.help_bios_intro), style = MaterialTheme.typography.bodyMedium)
                    firmwareSystems.forEach { system ->
                        FirmwareBlock(system = system, language = language)
                    }
                    Text(stringResource(R.string.help_bios_import), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.help_bios_flagged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (firmwareOnly.isNotEmpty()) {
                HelpSection(stringResource(R.string.help_section_firmware_only)) {
                    firmwareOnly.forEach { system ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(system.name(language), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                system.models().joinToString(" · ") { it.label },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stringResource(R.string.help_firmware_only_body), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HelpSection(stringResource(R.string.help_section_playing)) {
                Text(stringResource(R.string.help_playing_body), style = MaterialTheme.typography.bodyMedium)
            }

            HelpSection(stringResource(R.string.help_section_trouble)) {
                HelpBullet(stringResource(R.string.help_trouble_game))
                HelpBullet(stringResource(R.string.help_trouble_firmware))
                HelpBullet(stringResource(R.string.help_trouble_access))
            }
        }
    }
}

@Composable
private fun GamesFolderRow(system: SystemInfo, language: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                system.name(language),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "ROMs/${system.id}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (system.extensions.isNotEmpty()) {
            Text(
                stringResource(R.string.help_formats, system.extensions.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirmwareBlock(system: SystemInfo, language: String) {
    val optionalSuffix = stringResource(R.string.help_optional_suffix)
    val folders = system.firmware.groupBy { requirement ->
        Library.biosRelativePath(system, requirement.file).substringBeforeLast('/', "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(system.name(language), style = MaterialTheme.typography.bodyMedium)
        folders.forEach { (folder, requirements) ->
            val path = if (folder.isEmpty()) "BIOS/${system.id}/" else "BIOS/${system.id}/$folder/"
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    requirements.joinToString(" · ") { requirement ->
                        val name = Library.biosRelativePath(system, requirement.file).substringAfterLast('/')
                        if (requirement.optional) "$name$optionalSuffix" else name
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HelpBullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
