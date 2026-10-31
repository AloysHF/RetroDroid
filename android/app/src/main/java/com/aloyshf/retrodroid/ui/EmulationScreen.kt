package com.aloyshf.retrodroid.ui

import android.net.Uri
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.audio.AudioPlayer
import com.aloyshf.retrodroid.core.NativeCore
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.RomScanner
import com.aloyshf.retrodroid.data.Scaling
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.model.CoreOption
import com.aloyshf.retrodroid.model.CoreOptions
import com.aloyshf.retrodroid.model.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Suppress("UNUSED_PARAMETER")
@Composable
fun EmulationScreen(system: SystemInfo, game: RomEntry, treeUri: Uri, onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val audio = remember { AudioPlayer() }
    val pad = remember { PadState() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var rotation by remember { mutableIntStateOf(prefs.rotation(system)) }
    var scaling by remember { mutableStateOf(prefs.scaling(system.id)) }
    var menuOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(IntArray(0)) }
    var surface by remember { mutableStateOf<android.view.Surface?>(null) }
    var started by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cacheDir by remember { mutableStateOf<File?>(null) }
    var stateExists by remember { mutableStateOf(false) }

    val loadFailedMessage = stringResource(R.string.load_failed)
    val savedMessage = stringResource(R.string.state_saved)
    val loadedMessage = stringResource(R.string.state_loaded)
    val saveFailedMessage = stringResource(R.string.state_save_failed)
    val loadStateFailedMessage = stringResource(R.string.state_load_failed)
    val noStateMessage = stringResource(R.string.no_state)
    val stateFile = remember(game.uri) {
        SessionFiles.stateFile(context, system.core, game.name, game.uri)
    }

    LaunchedEffect(Unit) {
        var prepared: File? = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val content = SessionFiles.prepareContent(
                    context,
                    game.uri,
                    game.name,
                    RomScanner.siblings(context, treeUri, game.uri),
                )
                prepared = content.cacheDir
                val corePath = File(context.applicationInfo.nativeLibraryDir, system.soName).absolutePath
                val systemDir = SessionFiles.systemDir(context, system.core)
                val saveDir = SessionFiles.saveDir(context, system.core)
                val ok = NativeCore.nativeLoadCore(
                    corePath,
                    content.file.absolutePath,
                    systemDir.absolutePath,
                    saveDir.absolutePath,
                    prefs.optionsJson(system.core),
                )
                check(ok) { "core load failed" }
                NativeCore.nativeAvInfo()
            }
        }
        cacheDir = prepared
        result.onSuccess { info ->
            video = info
            loaded = true
            stateExists = stateFile.exists()
        }.onFailure { error ->
            android.util.Log.e("RetroDroid", "load failed", error)
            failed = true
            message = loadFailedMessage
        }
    }

    LaunchedEffect(loaded, surface, started) {
        val current = surface ?: return@LaunchedEffect
        if (!loaded || started) return@LaunchedEffect
        if (!NativeCore.nativeStart(current, rotation)) {
            message = loadFailedMessage
            return@LaunchedEffect
        }
        started = true
        audio.start(video.getOrElse(3) { 44100 })
    }

    LaunchedEffect(menuOpen, optionsOpen) {
        NativeCore.nativePause(menuOpen || optionsOpen)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            val events = NativeCore.pollEvents()
            if (events.messages.isNotEmpty()) message = events.messages.last()
            events.error?.let { message = it }
            if (events.shutdown) {
                onExit()
                break
            }
        }
    }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(2500)
        message = null
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    DisposableEffect(Unit) {
        onDispose {
            NativeCore.nativeStop()
            audio.stop()
            NativeCore.nativeUnload()
            cacheDir?.let { SessionFiles.cleanupCache(it) }
        }
    }

    BackHandler {
        when {
            confirmExit -> confirmExit = false
            optionsOpen -> optionsOpen = false
            else -> menuOpen = true
        }
    }

    fun saveState() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { NativeCore.nativeSerialize() }.getOrNull()
            }
            if (data == null) {
                message = saveFailedMessage
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching { stateFile.writeBytes(data) }.isSuccess
            }
            if (ok) {
                stateExists = true
                message = savedMessage
            } else {
                message = saveFailedMessage
            }
        }
    }

    fun loadState() {
        if (!stateFile.exists()) {
            message = noStateMessage
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { NativeCore.nativeUnserialize(stateFile.readBytes()) }.getOrDefault(false)
            }
            message = if (ok) loadedMessage else loadStateFailedMessage
        }
    }

    fun cycleRotation() {
        val next = (rotation + 90) % 360
        rotation = next
        prefs.setRotation(system.id, next)
        NativeCore.nativeSetRotation(next)
        menuOpen = false
    }

    fun cycleScaling() {
        val next = Scaling.entries[(scaling.ordinal + 1) % Scaling.entries.size]
        scaling = next
        prefs.setScaling(system.id, next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                val code = event.key.nativeKeyCode
                if (code == KeyEvent.KEYCODE_BACK ||
                    code == KeyEvent.KEYCODE_VOLUME_UP ||
                    code == KeyEvent.KEYCODE_VOLUME_DOWN
                ) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        NativeCore.nativeSetKey(code, true)
                        true
                    }

                    KeyEventType.KeyUp -> {
                        NativeCore.nativeSetKey(code, false)
                        true
                    }

                    else -> false
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val swapped = rotation == 90 || rotation == 270
            val baseWidth = max(1, if (swapped) video.getOrElse(1) { 240 } else video.getOrElse(0) { 320 })
            val baseHeight = max(1, if (swapped) video.getOrElse(0) { 320 } else video.getOrElse(1) { 240 })

            val videoModifier = when (scaling) {
                Scaling.STRETCH -> Modifier.fillMaxSize()
                Scaling.FIT -> {
                    val scale = min(maxWidth.value / baseWidth, maxHeight.value / baseHeight)
                    Modifier.size((baseWidth * scale).dp, (baseHeight * scale).dp)
                }

                Scaling.INTEGER -> {
                    val scale = max(1f, floor(min(maxWidth.value / baseWidth, maxHeight.value / baseHeight)))
                    Modifier.size((baseWidth * scale).dp, (baseHeight * scale).dp)
                }
            }

            GameSurface(
                modifier = videoModifier,
                onSurfaceChanged = { changed ->
                    if (changed == null) {
                        if (started) {
                            NativeCore.nativeStop()
                            audio.stop()
                            started = false
                        }
                    }
                    surface = changed
                },
            )

            if (!loaded) {
                if (failed) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(loadFailedMessage, color = Color.White)
                        TextButton(onClick = onExit) { Text(stringResource(R.string.back)) }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(if (started) R.string.loading else R.string.preparing),
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    game.name,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { menuOpen = true }) {
                    Text(stringResource(R.string.menu), color = Color.White)
                }
            }

            if (keyboardOpen) {
                KeyboardPanel(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 196.dp),
                )
            }

            if (!failed) {
                ControlOverlay(
                    preset = system.input,
                    state = pad,
                    onToggleKeyboard = { keyboardOpen = !keyboardOpen },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            message?.let { text ->
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                ) {
                    Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }

        if (menuOpen) {
            PauseMenu(
                stateExists = stateExists,
                scaling = scaling,
                onResume = { menuOpen = false },
                onSave = {
                    menuOpen = false
                    saveState()
                },
                onLoad = {
                    menuOpen = false
                    loadState()
                },
                onReset = {
                    menuOpen = false
                    NativeCore.nativeReset()
                },
                onRotation = { cycleRotation() },
                onScaling = { cycleScaling() },
                onOptions = {
                    menuOpen = false
                    optionsOpen = true
                },
                onExit = { confirmExit = true },
            )
        }

        if (optionsOpen) {
            CoreOptionsDialog(core = system.core, onDismiss = { optionsOpen = false })
        }

        if (confirmExit) {
            Dialog(onDismissRequest = { confirmExit = false }) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.exit_confirm), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
                            TextButton(onClick = {
                                confirmExit = false
                                onExit()
                            }) { Text(stringResource(R.string.exit_game)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSurface(modifier: Modifier, onSurfaceChanged: (android.view.Surface?) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceChanged(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceChanged(null)
                    }
                })
            }
        },
    )
}

@Composable
private fun PauseMenu(
    stateExists: Boolean,
    scaling: Scaling,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onReset: () -> Unit,
    onRotation: () -> Unit,
    onScaling: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
) {
    Dialog(onDismissRequest = onResume) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 18.dp),
            ) {
                Text(
                    stringResource(R.string.paused),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                MenuEntry(stringResource(R.string.resume), onResume)
                MenuEntry(stringResource(R.string.save_state), onSave)
                MenuEntry(stringResource(R.string.load_state), onLoad, enabled = stateExists)
                MenuEntry(stringResource(R.string.reset), onReset)
                MenuEntry(stringResource(R.string.rotation) + " · 90°", onRotation)
                MenuEntry(
                    stringResource(R.string.scaling) + " · " + stringResource(scalingLabel(scaling)),
                    onScaling,
                )
                MenuEntry(stringResource(R.string.core_options), onOptions)
                MenuEntry(stringResource(R.string.exit_game), onExit)
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CoreOptionsDialog(core: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val options = remember { CoreOptions.parse(NativeCore.nativeOptions()) }
    val selections = remember {
        mutableStateMapOf<String, String>().apply {
            options.forEach { put(it.key, it.selected) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .height(460.dp)
                    .padding(vertical = 18.dp),
            ) {
                Text(
                    stringResource(R.string.core_options),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    stringResource(R.string.options_live_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (options.isEmpty()) {
                    Text(
                        stringResource(R.string.no_core_options),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        options.forEach { option ->
                            OptionRow(
                                option = option,
                                selected = selections[option.key] ?: option.selected,
                                onSelect = { value ->
                                    if (NativeCore.nativeSetOption(option.key, value)) {
                                        selections[option.key] = value
                                        prefs.setOption(core, option.key, value)
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: CoreOption, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(option.label, style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                option.values.forEach { value ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

private fun scalingLabel(scaling: Scaling): Int = when (scaling) {
    Scaling.FIT -> R.string.scaling_fit
    Scaling.STRETCH -> R.string.scaling_stretch
    Scaling.INTEGER -> R.string.scaling_integer
}
