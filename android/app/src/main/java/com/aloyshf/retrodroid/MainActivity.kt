package com.aloyshf.retrodroid

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aloyshf.retrodroid.core.NativeCore
import com.aloyshf.retrodroid.data.ExternalLaunch
import com.aloyshf.retrodroid.data.ExternalLaunchRequest
import com.aloyshf.retrodroid.data.ExternalResolve
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.ThemeMode
import com.aloyshf.retrodroid.data.UpdateChecker
import com.aloyshf.retrodroid.data.UpdateInfo
import com.aloyshf.retrodroid.data.romEntry
import com.aloyshf.retrodroid.model.Registry
import com.aloyshf.retrodroid.model.SystemInfo
import com.aloyshf.retrodroid.ui.AboutScreen
import com.aloyshf.retrodroid.ui.EmulationScreen
import com.aloyshf.retrodroid.ui.GameListScreen
import com.aloyshf.retrodroid.ui.HelpScreen
import com.aloyshf.retrodroid.ui.HomeScreen
import com.aloyshf.retrodroid.ui.LibrarySetupScreen
import com.aloyshf.retrodroid.ui.OnResume
import com.aloyshf.retrodroid.ui.RetroDroidTheme
import com.aloyshf.retrodroid.ui.SettingsScreen
import com.aloyshf.retrodroid.ui.UpdateAvailableDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Front-end launch request (Tianma G, ES-DE, ...) waiting to be played. */
    private var pendingLaunch by mutableStateOf<ExternalLaunchRequest?>(null)
    private var launchSequence = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        requestedOrientation = prefs.screenOrientation().activityValue
        // Transparent bars; their icon colour follows the app theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        NativeCore.nativeInit()
        NativeCore.nativeSetLanguage(if (AppLocale.language(this).startsWith("zh")) 12 else 0)
        consumeLaunchIntent(intent)
        setContent {
            var themeMode by remember { mutableStateOf(prefs.themeMode()) }
            RetroDroidTheme(themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            prefs.setThemeMode(mode)
                            themeMode = mode
                        },
                        pendingLaunch = pendingLaunch,
                        onLaunchConsumed = { pendingLaunch = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        val request = ExternalLaunch.consumeFrom(intent) ?: return
        launchSequence += 1L
        pendingLaunch = request.copy(sequence = launchSequence)
    }
}

sealed interface Screen {
    data object Home : Screen

    data object Settings : Screen

    data object About : Screen

    /** Help returns to the screen it was opened from. */
    data class Help(val fromSettings: Boolean = false) : Screen

    /** Games of [system], browsed in [folder] (relative to ROMS/<system>, "" is the top). */
    data class Games(val system: SystemInfo, val folder: String = "") : Screen

    data class Play(val system: SystemInfo, val game: RomEntry?, val folder: String = "") : Screen
}

@Composable
private fun App(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    pendingLaunch: ExternalLaunchRequest?,
    onLaunchConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val systems = remember { Registry.load(context) }
    var access by remember { mutableStateOf(Library.hasAccess(context)) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var externalPlay by remember { mutableStateOf(false) }
    var ambiguousPick by remember { mutableStateOf<ExternalResolve.Ambiguous?>(null) }
    val scope = rememberCoroutineScope()
    val unsupportedMessage = stringResource(R.string.external_unsupported)
    val openFailedMessage = stringResource(R.string.external_open_failed)

    OnResume {
        access = Library.hasAccess(context)
    }
    LaunchedEffect(Unit) {
        val prefs = Prefs(context)
        if (!prefs.autoUpdateCheck()) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck() < UpdateChecker.CHECK_INTERVAL_MS) return@LaunchedEffect
        prefs.setLastUpdateCheck(now)
        UpdateChecker.check(BuildConfig.VERSION_NAME).onSuccess { updateInfo = it }
    }
    LaunchedEffect(access, systems) {
        if (access) withContext(Dispatchers.IO) { Library.ensureLayout(systems) }
    }

    // A front-end launch waits for storage access, so granting it on the setup
    // screen still ends in the game. The request is consumed last: consuming
    // restarts this effect, and the restart must not cut the launch short.
    LaunchedEffect(pendingLaunch, access, systems) {
        val request = pendingLaunch ?: return@LaunchedEffect
        if (!access) return@LaunchedEffect
        val resolved = withContext(Dispatchers.IO) {
            val result = ExternalLaunch.resolve(context, systems, request)
            if (result is ExternalResolve.Ready) Library.syncFirmware(context, result.system)
            result
        }
        when (resolved) {
            is ExternalResolve.Ready -> {
                externalPlay = true
                screen = Screen.Play(resolved.system, resolved.game, "")
            }
            is ExternalResolve.Ambiguous -> ambiguousPick = resolved
            ExternalResolve.Unsupported ->
                Toast.makeText(context, unsupportedMessage, Toast.LENGTH_SHORT).show()
            ExternalResolve.Failed ->
                Toast.makeText(context, openFailedMessage, Toast.LENGTH_SHORT).show()
        }
        onLaunchConsumed()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = granted
    }

    val grantAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    if (!access && !skipped) {
        LibrarySetupScreen(
            systems = systems,
            onGrant = grantAccess,
            onSkip = { skipped = true },
        )
        return
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            systems = systems,
            access = access,
            onRequestAccess = { skipped = false },
            onOpenSystem = { screen = Screen.Games(it) },
            onOpenHelp = { screen = Screen.Help() },
            onOpenSettings = { screen = Screen.Settings },
        )

        Screen.Settings -> SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenHelp = { screen = Screen.Help(fromSettings = true) },
            onOpenAbout = { screen = Screen.About },
            onBack = { screen = Screen.Home },
        )

        Screen.About -> AboutScreen(
            onBack = { screen = Screen.Settings },
        )

        is Screen.Help -> HelpScreen(
            systems = systems,
            onBack = { screen = if (current.fromSettings) Screen.Settings else Screen.Home },
        )

        is Screen.Games -> GameListScreen(
            system = current.system,
            folder = current.folder,
            onFolder = { folder -> screen = current.copy(folder = folder) },
            onBack = { screen = Screen.Home },
            onPlay = { game -> screen = Screen.Play(current.system, game, current.folder) },
        )

        is Screen.Play -> key(current.system.id, current.game?.uri) {
            // key() so a second front-end launch while playing swaps to a fresh
            // screen instead of recomposing the old core state in place.
            EmulationScreen(
                system = current.system,
                game = current.game,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onExit = {
                    if (externalPlay) {
                        // Launched by a front-end: leave, and the system
                        // returns to the calling app.
                        (context as? Activity)?.finish()
                    } else {
                        screen = Screen.Games(current.system, current.folder)
                    }
                },
            )
        }
    }

    ambiguousPick?.let { pick ->
        AmbiguousSystemDialog(
            pick = pick,
            onPick = { system ->
                ambiguousPick = null
                scope.launch {
                    withContext(Dispatchers.IO) { Library.syncFirmware(context, system) }
                    externalPlay = true
                    screen = Screen.Play(system, pick.file.romEntry(), "")
                }
            },
            onDismiss = { ambiguousPick = null },
        )
    }

    updateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            currentVersion = BuildConfig.VERSION_NAME,
            onDismiss = { updateInfo = null },
        )
    }
}

@Composable
private fun AmbiguousSystemDialog(
    pick: ExternalResolve.Ambiguous,
    onPick: (SystemInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = AppLocale.language(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_pick_system)) },
        text = {
            Column {
                pick.systems.forEach { system ->
                    TextButton(onClick = { onPick(system) }) {
                        Text(system.name(language))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
