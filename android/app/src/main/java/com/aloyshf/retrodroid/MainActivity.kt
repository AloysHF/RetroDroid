package com.aloyshf.retrodroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aloyshf.retrodroid.core.NativeCore
import com.aloyshf.retrodroid.data.Library
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.ThemeMode
import com.aloyshf.retrodroid.data.UpdateChecker
import com.aloyshf.retrodroid.data.UpdateInfo
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
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
                    )
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen

    data object Settings : Screen

    data object About : Screen

    /** Help returns to the screen it was opened from. */
    data class Help(val fromSettings: Boolean = false) : Screen

    /** Games of [system], browsed in [folder] (relative to ROMs/<system>, "" is the top). */
    data class Games(val system: SystemInfo, val folder: String = "") : Screen

    data class Play(val system: SystemInfo, val game: RomEntry?, val folder: String = "") : Screen
}

@Composable
private fun App(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    val systems = remember { Registry.load(context) }
    var access by remember { mutableStateOf(Library.hasAccess(context)) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

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

        is Screen.Play -> EmulationScreen(
            system = current.system,
            game = current.game,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onExit = { screen = Screen.Games(current.system, current.folder) },
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
