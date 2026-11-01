package com.aloyshf.retrodroid

import android.Manifest
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
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.model.Registry
import com.aloyshf.retrodroid.model.SystemInfo
import com.aloyshf.retrodroid.ui.EmulationScreen
import com.aloyshf.retrodroid.ui.GameListScreen
import com.aloyshf.retrodroid.ui.HomeScreen
import com.aloyshf.retrodroid.ui.LibrarySetupScreen
import com.aloyshf.retrodroid.ui.OnResume
import com.aloyshf.retrodroid.ui.RetroDroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the system bars always need light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        NativeCore.nativeInit()
        NativeCore.nativeSetLanguage(if (Locale.getDefault().language.startsWith("zh")) 12 else 0)
        setContent {
            RetroDroidTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Games(val system: SystemInfo) : Screen
    data class Play(val system: SystemInfo, val game: RomEntry?) : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val systems = remember { Registry.load(context) }
    var access by remember { mutableStateOf(Library.hasAccess(context)) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    OnResume {
        access = Library.hasAccess(context)
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
        )

        is Screen.Games -> GameListScreen(
            system = current.system,
            onBack = { screen = Screen.Home },
            onPlay = { game -> screen = Screen.Play(current.system, game) },
        )

        is Screen.Play -> EmulationScreen(
            system = current.system,
            game = current.game,
            onExit = { screen = Screen.Games(current.system) },
        )
    }
}
