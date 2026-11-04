package com.aloyshf.retrodroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.AppLanguage
import com.aloyshf.retrodroid.data.GameLayout
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.ScreenOrientation
import com.aloyshf.retrodroid.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var layout by remember { mutableStateOf(prefs.gameLayout()) }
    var layoutDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(prefs.language()) }
    var languageDialog by remember { mutableStateOf(false) }
    var orientation by remember { mutableStateOf(prefs.screenOrientation()) }
    var orientationDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    if (layoutDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_game_layout),
            choices = GameLayout.entries.map { option ->
                Choice(
                    value = option,
                    label = stringResource(layoutLabel(option)),
                    selected = option == layout,
                )
            },
            onSelect = { option ->
                layout = option
                prefs.setGameLayout(option)
                layoutDialog = false
            },
            onDismiss = { layoutDialog = false },
        )
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            choices = ThemeMode.entries.map { option ->
                Choice(
                    value = option,
                    label = stringResource(themeLabel(option)),
                    selected = option == themeMode,
                )
            },
            onSelect = { option ->
                onThemeModeChange(option)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }

    if (languageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            choices = AppLanguage.entries.map { option ->
                Choice(
                    value = option,
                    label = stringResource(languageLabel(option)),
                    selected = option == language,
                )
            },
            onSelect = { option ->
                languageDialog = false
                if (option != language) {
                    language = option
                    prefs.setLanguage(option)
                    context.findActivity()?.recreate()
                }
            },
            onDismiss = { languageDialog = false },
        )
    }

    if (orientationDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_orientation),
            choices = ScreenOrientation.entries.map { option ->
                Choice(
                    value = option,
                    label = stringResource(orientationLabel(option)),
                    selected = option == orientation,
                )
            },
            onSelect = { option ->
                orientation = option
                prefs.setScreenOrientation(option)
                context.findActivity()?.requestedOrientation = option.activityValue
                orientationDialog = false
            },
            onDismiss = { orientationDialog = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
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
                .padding(horizontal = 16.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_section_library)) {
                SettingRow(
                    title = stringResource(R.string.settings_game_layout),
                    summary = stringResource(R.string.settings_game_layout_summary),
                    value = stringResource(layoutLabel(layout)),
                    onClick = { layoutDialog = true },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_display)) {
                SettingRow(
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    value = stringResource(themeLabel(themeMode)),
                    onClick = { themeDialog = true },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingRow(
                    title = stringResource(R.string.settings_orientation),
                    summary = stringResource(R.string.settings_orientation_summary),
                    value = stringResource(orientationLabel(orientation)),
                    onClick = { orientationDialog = true },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_general)) {
                SettingRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(R.string.settings_language_summary),
                    value = stringResource(languageLabel(language)),
                    onClick = { languageDialog = true },
                )
            }
        }
    }
}

private data class Choice<T>(
    val value: T,
    val label: String,
    val selected: Boolean,
)

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(choice.value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.selected, onClick = { onSelect(choice.value) })
                        Text(
                            choice.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun layoutLabel(layout: GameLayout): Int = when (layout) {
    GameLayout.LIST -> R.string.settings_layout_list
    GameLayout.GRID -> R.string.settings_layout_grid
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
}

private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.CHINESE -> R.string.settings_language_chinese
    AppLanguage.ENGLISH -> R.string.settings_language_english
}

private fun orientationLabel(orientation: ScreenOrientation): Int = when (orientation) {
    ScreenOrientation.SYSTEM -> R.string.settings_orientation_system
    ScreenOrientation.PORTRAIT -> R.string.settings_orientation_portrait
    ScreenOrientation.LANDSCAPE -> R.string.settings_orientation_landscape
}
