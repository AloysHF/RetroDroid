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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aloyshf.retrodroid.BuildConfig
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.AppLanguage
import com.aloyshf.retrodroid.data.GameLayout
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.Scaling
import com.aloyshf.retrodroid.data.ScreenOrientation
import com.aloyshf.retrodroid.data.ThemeMode
import com.aloyshf.retrodroid.data.UpdateChecker
import com.aloyshf.retrodroid.data.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    inGame: Boolean = false,
    rotation: Int = 0,
    onRotationChange: ((Int) -> Unit)? = null,
    scaling: Scaling = Scaling.FIT,
    onScalingChange: ((Scaling) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var layout by remember { mutableStateOf(prefs.gameLayout()) }
    var layoutDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var scalingDialog by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(prefs.language()) }
    var languageDialog by remember { mutableStateOf(false) }
    var orientation by remember { mutableStateOf(prefs.screenOrientation()) }
    var orientationDialog by remember { mutableStateOf(false) }
    var autoUpdateCheck by remember { mutableStateOf(prefs.autoUpdateCheck()) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    val checkForUpdates: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            scope.launch {
                updateResult = UpdateChecker.check(BuildConfig.VERSION_NAME).fold(
                    onSuccess = { info ->
                        if (info != null) UpdateResult.Available(info) else UpdateResult.Latest
                    },
                    onFailure = { UpdateResult.Failed },
                )
                checkingUpdate = false
            }
        }
    }

    when (val outcome = updateResult) {
        is UpdateResult.Available -> UpdateAvailableDialog(
            info = outcome.info,
            currentVersion = BuildConfig.VERSION_NAME,
            onDismiss = { updateResult = null },
        )

        UpdateResult.Latest -> AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(stringResource(R.string.update_latest_title)) },
            text = { Text(stringResource(R.string.update_latest_body, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = { updateResult = null }) { Text(stringResource(R.string.confirm)) }
            },
        )

        UpdateResult.Failed -> AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(R.string.update_failed_body)) },
            confirmButton = {
                TextButton(onClick = { updateResult = null }) { Text(stringResource(R.string.confirm)) }
            },
        )

        null -> Unit
    }

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

    if (scalingDialog) {
        ChoiceDialog(
            title = stringResource(R.string.scaling),
            choices = Scaling.entries.map { option ->
                Choice(
                    value = option,
                    label = stringResource(scalingLabel(option)),
                    selected = option == scaling,
                )
            },
            onSelect = { option ->
                scalingDialog = false
                onScalingChange?.invoke(option)
            },
            onDismiss = { scalingDialog = false },
        )
    }

    val sections: @Composable ColumnScope.() -> Unit = {
        if (!inGame) {
            SettingsSection(stringResource(R.string.settings_section_library)) {
                SettingRow(
                    title = stringResource(R.string.settings_game_layout),
                    summary = stringResource(R.string.settings_game_layout_summary),
                    value = stringResource(layoutLabel(layout)),
                    onClick = { layoutDialog = true },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_display)) {
            if (inGame && onRotationChange != null) {
                SettingRow(
                    title = stringResource(R.string.rotation),
                    summary = stringResource(R.string.settings_rotation_summary),
                    value = "$rotation°",
                    onClick = { onRotationChange((rotation + 90) % 360) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (inGame && onScalingChange != null) {
                SettingRow(
                    title = stringResource(R.string.scaling),
                    summary = stringResource(R.string.settings_scaling_summary),
                    value = stringResource(scalingLabel(scaling)),
                    onClick = { scalingDialog = true },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
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

        if (!inGame) {
            SettingsSection(stringResource(R.string.settings_section_general)) {
                SettingRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(R.string.settings_language_summary),
                    value = stringResource(languageLabel(language)),
                    onClick = { languageDialog = true },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_updates)) {
                SettingRow(
                    title = stringResource(R.string.update_check),
                    summary = stringResource(R.string.update_check_summary, BuildConfig.VERSION_NAME),
                    value = if (checkingUpdate) stringResource(R.string.update_checking) else "",
                    onClick = checkForUpdates,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingSwitchRow(
                    title = stringResource(R.string.update_auto),
                    summary = stringResource(R.string.update_auto_summary),
                    checked = autoUpdateCheck,
                    onCheckedChange = { enabled ->
                        autoUpdateCheck = enabled
                        prefs.setAutoUpdateCheck(enabled)
                    },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_support)) {
                SettingRow(
                    title = stringResource(R.string.help_title),
                    summary = stringResource(R.string.help_summary),
                    value = "",
                    onClick = onOpenHelp,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingRow(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.about_summary),
                    value = BuildConfig.VERSION_NAME,
                    onClick = onOpenAbout,
                )
            }
        }
    }

    if (embedded) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
                }
                sections()
                Spacer(Modifier.height(16.dp))
            }
        }
        return
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
            content = sections,
        )
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

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object Latest : UpdateResult
    data object Failed : UpdateResult
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

private fun scalingLabel(scaling: Scaling): Int = when (scaling) {
    Scaling.FIT -> R.string.scaling_fit
    Scaling.STRETCH -> R.string.scaling_stretch
    Scaling.INTEGER -> R.string.scaling_integer
}
