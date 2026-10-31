package com.aloyshf.retrodroid

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aloyshf.retrodroid.core.NativeCore
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.model.Registry
import com.aloyshf.retrodroid.model.SystemInfo
import com.aloyshf.retrodroid.ui.EmulationScreen
import com.aloyshf.retrodroid.ui.GameListScreen
import com.aloyshf.retrodroid.ui.HomeScreen
import com.aloyshf.retrodroid.ui.RetroDroidTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NativeCore.nativeInit()
        NativeCore.nativeSetLanguage(if (Locale.getDefault().language.startsWith("zh")) 12 else 0)
        setContent {
            RetroDroidTheme {
                App()
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Games(val system: SystemInfo) : Screen
    data class Play(val system: SystemInfo, val game: RomEntry, val treeUri: Uri) : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val systems = remember { Registry.load(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            systems = systems,
            onOpenSystem = { screen = Screen.Games(it) },
        )

        is Screen.Games -> GameListScreen(
            system = current.system,
            onBack = { screen = Screen.Home },
            onPlay = { game, treeUri -> screen = Screen.Play(current.system, game, treeUri) },
        )

        is Screen.Play -> EmulationScreen(
            system = current.system,
            game = current.game,
            treeUri = current.treeUri,
            onExit = { screen = Screen.Games(current.system) },
        )
    }
}
