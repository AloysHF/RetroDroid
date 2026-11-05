package com.aloyshf.retrodroid.ui

import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.aloyshf.retrodroid.data.PlayStats
import com.aloyshf.retrodroid.data.Prefs
import com.aloyshf.retrodroid.data.RomEntry
import com.aloyshf.retrodroid.data.SaveSlot
import com.aloyshf.retrodroid.data.SaveStateStore
import com.aloyshf.retrodroid.data.Scaling
import com.aloyshf.retrodroid.data.Screenshots
import com.aloyshf.retrodroid.data.SessionFiles
import com.aloyshf.retrodroid.data.ThemeMode
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

@Composable
fun EmulationScreen(
    system: SystemInfo,
    game: RomEntry?,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val audio = remember { AudioPlayer() }
    val pad = remember { PadState() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var rotation by remember { mutableIntStateOf(prefs.rotation(system)) }
    var scaling by remember { mutableStateOf(prefs.scaling(system.id)) }
    var menuOpen by remember { mutableStateOf(false) }
    var pausePage by remember { mutableStateOf(PausePage.MAIN) }
    var optionsOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var resumePrompt by remember { mutableStateOf(false) }
    var pendingAutoLoad by remember { mutableStateOf(false) }
    var slotsVersion by remember { mutableIntStateOf(0) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(IntArray(0)) }
    var surface by remember { mutableStateOf<android.view.Surface?>(null) }
    var started by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val loadFailedMessage = stringResource(R.string.load_failed)
    val savedMessage = stringResource(R.string.state_saved)
    val loadedMessage = stringResource(R.string.state_loaded)
    val saveFailedMessage = stringResource(R.string.state_save_failed)
    val loadStateFailedMessage = stringResource(R.string.state_load_failed)
    val noStateMessage = stringResource(R.string.no_state)
    val deletedMessage = stringResource(R.string.state_deleted)
    val screenshotSavedMessage = stringResource(R.string.screenshot_saved)
    val screenshotFailedMessage = stringResource(R.string.screenshot_failed)

    val gameName = game?.name ?: "firmware"
    val store = remember(game?.uri) {
        SaveStateStore(context, system.core, gameName, game?.uri ?: android.net.Uri.EMPTY)
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // Library games are plain files, so the core opens them in place.
                // Firmware-only systems boot with no content path.
                val contentPath = game?.let {
                    File(requireNotNull(it.uri.path) { "missing game path" }).absolutePath
                }
                val corePath = File(context.applicationInfo.nativeLibraryDir, system.soName).absolutePath
                val systemDir = SessionFiles.systemDir(context)
                val saveDir = SessionFiles.saveDir(context, system.core)
                val ok = NativeCore.nativeLoadCore(
                    corePath,
                    contentPath,
                    systemDir.absolutePath,
                    saveDir.absolutePath,
                    prefs.optionsJson(system.core),
                )
                check(ok) { "core load failed" }
                NativeCore.nativeAvInfo()
            }
        }
        result.onSuccess { info ->
            video = info
            loaded = true
            resumePrompt = store.exists(SaveSlot.AUTO)
        }.onFailure { error ->
            android.util.Log.e("RetroDroid", "load failed", error)
            failed = true
            message = loadFailedMessage
        }
    }

    LaunchedEffect(loaded, surface, started, resumePrompt) {
        val current = surface ?: return@LaunchedEffect
        if (!loaded || started || resumePrompt) return@LaunchedEffect
        if (!NativeCore.nativeStart(current, rotation)) {
            message = loadFailedMessage
            return@LaunchedEffect
        }
        started = true
        audio.start(video.getOrElse(3) { 44100 })
    }

    // A session counts from the moment the core actually runs, so a failed
    // load never lands in the play time.
    val gameKey = game?.uri?.toString()
    val playStats = remember { PlayStats(context) }
    val sessionStartedAt = remember { mutableLongStateOf(0L) }
    val sessionStartElapsed = remember { mutableLongStateOf(0L) }

    LaunchedEffect(started, gameKey) {
        if (!started || gameKey == null || sessionStartedAt.longValue != 0L) return@LaunchedEffect
        sessionStartedAt.longValue = System.currentTimeMillis()
        sessionStartElapsed.longValue = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) { playStats.recordStart(gameKey, sessionStartedAt.longValue) }
    }

    DisposableEffect(gameKey) {
        onDispose {
            val key = gameKey
            if (key != null && sessionStartedAt.longValue != 0L) {
                playStats.recordSession(
                    key,
                    SystemClock.elapsedRealtime() - sessionStartElapsed.longValue,
                )
            }
        }
    }

    LaunchedEffect(menuOpen, optionsOpen, resumePrompt) {
        NativeCore.nativePause(menuOpen || optionsOpen || resumePrompt)
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
        }
    }

    BackHandler {
        when {
            confirmExit -> confirmExit = false
            optionsOpen -> optionsOpen = false
            menuOpen && pausePage != PausePage.MAIN -> pausePage = PausePage.MAIN
            menuOpen -> menuOpen = false
            else -> {
                pausePage = PausePage.MAIN
                menuOpen = true
            }
        }
    }

    fun serializeState(): ByteArray? =
        runCatching { NativeCore.nativeSerialize() }.getOrNull()

    fun saveSlot(slot: SaveSlot) {
        scope.launch {
            val data = withContext(Dispatchers.IO) { serializeState() }
            if (data == null) {
                message = saveFailedMessage
                return@launch
            }
            val preview = Screenshots.capture()
            val ok = withContext(Dispatchers.IO) { store.write(slot, data, preview) }
            slotsVersion++
            message = if (ok) savedMessage else saveFailedMessage
        }
    }

    fun loadSlot(slot: SaveSlot) {
        scope.launch {
            val data = withContext(Dispatchers.IO) { store.read(slot) }
            if (data == null) {
                message = noStateMessage
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching { NativeCore.nativeUnserialize(data) }.getOrDefault(false)
            }
            message = if (ok) loadedMessage else loadStateFailedMessage
        }
    }

    fun deleteSlot(slot: SaveSlot) {
        scope.launch {
            withContext(Dispatchers.IO) { store.delete(slot) }
            slotsVersion++
            message = deletedMessage
        }
    }

    fun saveAutoState() {
        if (!started || resumePrompt) return
        scope.launch {
            val data = withContext(Dispatchers.IO) { serializeState() } ?: return@launch
            val preview = Screenshots.capture()
            val ok = withContext(Dispatchers.IO) { store.write(SaveSlot.AUTO, data, preview) }
            if (ok) slotsVersion++
        }
    }

    fun exitGame() {
        scope.launch {
            if (started && !resumePrompt) {
                val data = withContext(Dispatchers.IO) { serializeState() }
                if (data != null) {
                    val preview = Screenshots.capture()
                    withContext(Dispatchers.IO) { store.write(SaveSlot.AUTO, data, preview) }
                }
            }
            onExit()
        }
    }

    fun takeScreenshot() {
        scope.launch {
            val bitmap = Screenshots.capture()
            if (bitmap == null) {
                message = screenshotFailedMessage
                return@launch
            }
            val file = withContext(Dispatchers.IO) { Screenshots.save(context, bitmap, gameName) }
            message = if (file != null) screenshotSavedMessage else screenshotFailedMessage
        }
    }

    fun continueFromAuto() {
        resumePrompt = false
        pendingAutoLoad = true
    }

    // The automatic save is applied once the core runs: some cores only accept
    // a state after their first frame.
    LaunchedEffect(started, pendingAutoLoad) {
        if (!started || !pendingAutoLoad) return@LaunchedEffect
        pendingAutoLoad = false
        val data = withContext(Dispatchers.IO) { store.read(SaveSlot.AUTO) }
        val ok = data != null && withContext(Dispatchers.IO) {
            runCatching { NativeCore.nativeUnserialize(data) }.getOrDefault(false)
        }
        if (!ok) message = loadStateFailedMessage
    }

    fun updateRotation(next: Int) {
        rotation = next
        prefs.setRotation(system.id, next)
        NativeCore.nativeSetRotation(next)
    }

    fun updateScaling(next: Scaling) {
        scaling = next
        prefs.setScaling(system.id, next)
    }

    OnStop { saveAutoState() }

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
                if (menuOpen || optionsOpen || resumePrompt) return@onKeyEvent false
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
                    game?.name ?: stringResource(R.string.boot_firmware),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    pausePage = PausePage.MAIN
                    menuOpen = true
                }) {
                    Text(stringResource(R.string.menu), color = Color.White)
                }
            }

            if (keyboardOpen && !menuOpen) {
                KeyboardPanel(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 196.dp),
                )
            }

            if (!failed && !menuOpen) {
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
        }

        if (menuOpen) {
            PauseOverlay(
                page = pausePage,
                store = store,
                version = slotsVersion,
                onPage = { pausePage = it },
                onResume = { menuOpen = false },
                onScreenshot = {
                    menuOpen = false
                    takeScreenshot()
                },
                onReset = {
                    menuOpen = false
                    NativeCore.nativeReset()
                },
                onOptions = {
                    menuOpen = false
                    optionsOpen = true
                },
                onExit = { confirmExit = true },
                onSave = { saveSlot(it) },
                onLoad = { loadSlot(it) },
                onDelete = { deleteSlot(it) },
                settings = {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onOpenHelp = {},
                        onOpenAbout = {},
                        onBack = { pausePage = PausePage.MAIN },
                        embedded = true,
                        inGame = true,
                        rotation = rotation,
                        onRotationChange = { updateRotation(it) },
                        scaling = scaling,
                        onScalingChange = { updateScaling(it) },
                    )
                },
            )
        }

        if (resumePrompt) {
            ResumeDialog(
                store = store,
                onContinue = { continueFromAuto() },
                onStartOver = { resumePrompt = false },
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
                                exitGame()
                            }) { Text(stringResource(R.string.exit_game)) }
                        }
                    }
                }
            }
        }

        message?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            ) {
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
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
