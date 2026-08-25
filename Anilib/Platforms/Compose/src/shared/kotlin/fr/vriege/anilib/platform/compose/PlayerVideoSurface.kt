package fr.vriege.anilib.platform.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.player.PlayerPlayback
import fr.vriege.anilib.feature.player.PlayerAdvancedCapability
import fr.vriege.anilib.feature.player.PlayerAdvancedState
import fr.vriege.anilib.feature.player.ui.PlayerController
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Optional

private const val PROGRESS_INTERVAL_MILLIS = 5_000L
private const val CONTROLS_HIDE_DELAY_MILLIS = 3_000L
private const val SEEK_INTERVAL_MILLIS = 10_000L

@Composable
internal fun PlayerVideoSurface(
    controller: PlayerController,
    playback: PlayerPlayback,
    fullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    nextEpisode: (() -> Unit)? = null,
    previousEpisode: (() -> Unit)? = null,
    progressChanged: () -> Unit,
) {
    val bridge = playback as? ComposePlayerPlayback
    if (bridge == null) {
        UnavailablePlayerSurface()
        return
    }
    val player = rememberVideoPlayerState()
    val persistenceScope = rememberCrashSafeCoroutineScope()
    val preferences = controller.preferences()
    var controlsVisible by remember(bridge) { mutableStateOf(true) }
    var controlsActivity by remember(bridge) { mutableIntStateOf(0) }
    var locked by remember(bridge) { mutableStateOf(false) }
    var brightness by remember(bridge) { mutableFloatStateOf(1f) }
    var volume by remember(bridge) {
        mutableFloatStateOf(runCatching { bridge.snapshot().volume() }.getOrDefault(1f))
    }
    var customMenu by remember(bridge) { mutableStateOf(false) }
    var advancedMenu by remember(bridge) { mutableStateOf(false) }
    var leftAction by remember(bridge) { mutableStateOf(PlayerCustomAction.SEEK_BACK) }
    var rightAction by remember(bridge) { mutableStateOf(PlayerCustomAction.SEEK_FORWARD) }
    var drag by remember(bridge) { mutableStateOf(Offset.Zero) }
    var dragStartX by remember(bridge) { mutableFloatStateOf(0f) }
    val focusRequester = remember(bridge) { FocusRequester() }
    val currentSetPlayerActive = rememberUpdatedState(setPlayerActive)
    DisposableEffect(bridge) {
        currentSetPlayerActive.value(true)
        onDispose { currentSetPlayerActive.value(false) }
    }
    DisposableEffect(bridge, player) {
        bridge.attach(player)
        onDispose {
            bridge.detach(player)
        }
    }
    CrashSafeLaunchedEffect(bridge) {
        runCatching { focusRequester.requestFocus() }
    }
    CrashSafeLaunchedEffect(bridge, player) {
        while (!player.hasMedia && player.error == null) delay(50L)
        while (!bridge.resumeWhenReady() && player.error == null) delay(50L)
    }
    CrashSafeLaunchedEffect(bridge, player.isPlaying) {
        while (player.isPlaying) {
            delay(PROGRESS_INTERVAL_MILLIS)
            if (persistProgress(controller, bridge)) progressChanged()
        }
    }
    CrashSafeLaunchedEffect(
        bridge,
        controlsVisible,
        controlsActivity,
        player.isPlaying,
        locked,
        customMenu,
        advancedMenu,
    ) {
        if (controlsVisible && player.isPlaying && !locked && !customMenu && !advancedMenu) {
            delay(CONTROLS_HIDE_DELAY_MILLIS)
            controlsVisible = false
        }
    }

    fun revealControls() {
        if (!controlsVisible) controlsVisible = true
        controlsActivity++
    }

    fun seekBy(deltaMillis: Long) {
        revealControls()
        val state = runCatching { bridge.snapshot() }.getOrNull() ?: return
        val maximum = if (state.durationMillis() > 0) state.durationMillis() else Long.MAX_VALUE
        controller.seekTo((state.positionMillis() + deltaMillis).coerceIn(0L, maximum))
    }

    fun togglePlayback() {
        revealControls()
        if (player.isPlaying) controller.pause() else controller.play()
    }

    fun cycleSpeed() {
        revealControls()
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val current = runCatching { bridge.snapshot().playbackSpeed() }.getOrNull() ?: return
        val index = speeds.indexOfFirst { it > current + 0.01f }
        controller.setPlaybackSpeed(if (index < 0) speeds.first() else speeds[index])
    }

    fun execute(action: PlayerCustomAction) {
        revealControls()
        when (action) {
            PlayerCustomAction.SEEK_BACK -> seekBy(-SEEK_INTERVAL_MILLIS)
            PlayerCustomAction.SEEK_FORWARD -> seekBy(SEEK_INTERVAL_MILLIS)
            PlayerCustomAction.PLAY_PAUSE -> togglePlayback()
            PlayerCustomAction.SPEED -> cycleSpeed()
            PlayerCustomAction.MUTE -> {
                volume = if (volume > 0f) 0f else 1f
                controller.setVolume(volume)
            }
        }
    }

    Box(
        modifier = (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (
                    event.type != KeyEventType.KeyDown ||
                    locked ||
                    customMenu ||
                    advancedMenu
                ) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            seekBy(-SEEK_INTERVAL_MILLIS)
                            true
                        }
                        Key.DirectionRight -> {
                            seekBy(SEEK_INTERVAL_MILLIS)
                            true
                        }
                        else -> false
                    }
                }
            }
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        runCatching { focusRequester.requestFocus() }
                        if (!locked) {
                            if (controlsVisible) controlsVisible = false else revealControls()
                        }
                    },
                    onDoubleTap = { position ->
                        if (!locked) {
                            when {
                                position.x < size.width / 3f -> seekBy(-SEEK_INTERVAL_MILLIS)
                                position.x > size.width * 2f / 3f -> seekBy(SEEK_INTERVAL_MILLIS)
                                else -> togglePlayback()
                            }
                        }
                    },
                )
            }
            .pointerInput(locked) {
                awaitPointerEventScope {
                    while (true) {
                        if (
                            awaitPointerEvent().type == PointerEventType.Move &&
                            !locked &&
                            !controlsVisible
                        ) {
                            revealControls()
                        }
                    }
                }
            }
            .pointerInput(locked, brightness, volume) {
                detectDragGestures(
                    onDragStart = {
                        if (!locked) revealControls()
                        drag = Offset.Zero
                        dragStartX = it.x
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        if (!locked && abs(drag.x) > abs(drag.y) && abs(drag.x) >= 48f) {
                            seekBy(if (drag.x < 0f) -SEEK_INTERVAL_MILLIS else SEEK_INTERVAL_MILLIS)
                        } else if (!locked && abs(drag.y) >= 48f) {
                            val delta = -drag.y / size.height.coerceAtLeast(1)
                            if (dragStartX < size.width / 2f) {
                                brightness = (brightness + delta).coerceIn(0.25f, 1.5f)
                            } else {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                controller.setVolume(volume)
                            }
                        }
                    },
                )
            },
    ) {
        VideoPlayerSurface(playerState = player, modifier = Modifier.fillMaxSize())
        if (brightness < 1f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - brightness)))
        } else if (brightness > 1f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (brightness - 1f) * 0.5f)))
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (player.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            if (locked) {
                IconButton(
                    onClick = {
                        locked = false
                        revealControls()
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(Icons.Default.LockOpen, "Unlock controls", tint = Color.White)
                }
            } else {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { previousEpisode?.invoke() },
                                enabled = previousEpisode != null,
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = UiTranslations.translate(
                                        "ui.previous.episode",
                                        LocalLanguagePack.current,
                                    ),
                                    tint = Color.White.copy(alpha = if (previousEpisode == null) 0.3f else 1f),
                                )
                            }
                            IconButton(onClick = { seekBy(-SEEK_INTERVAL_MILLIS) }) {
                                Icon(Icons.Default.Replay10, "Seek back", tint = Color.White)
                            }
                            IconButton(onClick = ::togglePlayback, modifier = Modifier.size(64.dp)) {
                                Icon(
                                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                            IconButton(onClick = { seekBy(SEEK_INTERVAL_MILLIS) }) {
                                Icon(Icons.Default.Forward10, "Seek forward", tint = Color.White)
                            }
                            IconButton(
                                onClick = { nextEpisode?.invoke() },
                                enabled = nextEpisode != null,
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = UiTranslations.translate(
                                        "ui.next.episode",
                                        LocalLanguagePack.current,
                                    ),
                                    tint = Color.White.copy(alpha = if (nextEpisode == null) 0.3f else 1f),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                        ) {
                    Slider(
                        value = player.sliderPos.coerceIn(0f, 1000f),
                        onValueChange = {
                            revealControls()
                            player.seekStart(it)
                        },
                        onValueChangeFinished = {
                            player.seekFinished()
                            persistenceScope.launch {
                                if (persistProgress(controller, bridge)) progressChanged()
                            }
                        },
                        valueRange = 0f..1000f,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ui.volume", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = volume,
                            onValueChange = {
                                revealControls()
                                volume = it
                                controller.setVolume(it)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${player.positionText} / ${player.durationText}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val currentPlaybackState = runCatching { bridge.snapshot() }.getOrNull()
                        if (
                            preferences.introEndMillis() > 0L &&
                            currentPlaybackState != null &&
                            currentPlaybackState.positionMillis() < preferences.introEndMillis()
                        ) {
                            TextButton(onClick = {
                                revealControls()
                                controller.seekTo(preferences.introEndMillis())
                            }) {
                                Text("ui.skip.intro", color = Color.White)
                            }
                        }
                        val playbackState = currentPlaybackState
                        if (
                            preferences.outroDurationMillis() > 0L &&
                            playbackState != null &&
                            playbackState.durationMillis() > 0L &&
                            playbackState.positionMillis() >=
                            playbackState.durationMillis() - preferences.outroDurationMillis()
                        ) {
                            TextButton(onClick = {
                                revealControls()
                                controller.seekTo(playbackState.durationMillis())
                                controller.markCompleted()
                            }) {
                                Text("ui.skip.outro", color = Color.White)
                            }
                        }
                        TextButton(onClick = { execute(leftAction) }) {
                            Text(
                                UiTranslations.format(
                                    "dynamic.left.short",
                                    LocalLanguagePack.current,
                                    UiTranslations.translate(leftAction.labelKey, LocalLanguagePack.current),
                                ),
                                color = Color.White,
                            )
                        }
                        TextButton(onClick = { execute(rightAction) }) {
                            Text(
                                UiTranslations.format(
                                    "dynamic.right.short",
                                    LocalLanguagePack.current,
                                    UiTranslations.translate(rightAction.labelKey, LocalLanguagePack.current),
                                ),
                                color = Color.White,
                            )
                        }
                        TextButton(onClick = ::cycleSpeed) {
                            Text("${currentPlaybackState?.playbackSpeed() ?: 1f}×", color = Color.White)
                        }
                        if (controller.advancedCapabilities().isNotEmpty()) {
                            TextButton(onClick = {
                                revealControls()
                                advancedMenu = true
                            }) {
                                Text("ui.advanced", color = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            revealControls()
                            customMenu = true
                        }) {
                            Icon(Icons.Default.Tune, "Custom buttons", tint = Color.White)
                        }
                        IconButton(onClick = {
                            controlsVisible = false
                            locked = true
                        }) {
                            Icon(Icons.Default.Lock, "Lock controls", tint = Color.White)
                        }
                        IconButton(onClick = {
                            revealControls()
                            setFullscreen(!fullscreen)
                        }) {
                            Icon(
                                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                UiTranslations.translate(
                                    if (fullscreen) "ui.exit.fullscreen" else "ui.fullscreen",
                                    LocalLanguagePack.current,
                                ),
                                tint = Color.White,
                            )
                        }
                    }
                }
                    }
                }
            }
            player.error?.let {
                Text(
                    it.toString(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
        }
        if (customMenu) {
            PlayerCustomButtonDialog(
                left = leftAction,
                right = rightAction,
                updateLeft = { leftAction = it },
                updateRight = { rightAction = it },
                close = { customMenu = false },
            )
        }
        if (advancedMenu) {
            PlayerAdvancedDialog(controller, close = { advancedMenu = false })
        }
    }
}

@Composable
private fun PlayerAdvancedDialog(controller: PlayerController, close: () -> Unit) {
    var revision by remember(controller) { mutableStateOf(0) }
    var error by remember(controller) { mutableStateOf<String?>(null) }
    val capabilities = controller.advancedCapabilities()
    val state = remember(controller, revision) {
        controller.advancedState().orElse(PlayerAdvancedState.defaults())
    }
    val command: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess {
                error = null
                revision++
            }
            .onFailure { error = it.message ?: "Advanced player command failed." }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.desktop.player.controls") },
        text = {
            Column {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (PlayerAdvancedCapability.LOOP in capabilities) {
                    TextButton(onClick = { command { controller.setLoop(!state.loop()) } }) {
                        Text(if (state.loop()) "Loop: On" else "Loop: Off")
                    }
                }
                if (PlayerAdvancedCapability.RESTART in capabilities) {
                    TextButton(onClick = { command(controller::restart) }) { Text("ui.restart") }
                }
                if (PlayerAdvancedCapability.FRAME_STEP in capabilities) {
                    TextButton(onClick = { command(controller::frameStep) }) { Text("ui.next.frame") }
                }
                if (PlayerAdvancedCapability.AUDIO_DELAY in capabilities) {
                    DelayControl("Audio delay", state.audioDelayMillis()) {
                        command { controller.setAudioDelay(it) }
                    }
                }
                if (PlayerAdvancedCapability.SUBTITLE_DELAY in capabilities) {
                    DelayControl("Subtitle delay", state.subtitleDelayMillis()) {
                        command { controller.setSubtitleDelay(it) }
                    }
                }
                if (PlayerAdvancedCapability.ASPECT_RATIO in capabilities) {
                    TextButton(onClick = {
                        command { controller.setAspectRatio(nextAspectRatio(state.aspectRatio())) }
                    }) {
                        Text(
                            UiTranslations.format(
                                "dynamic.aspect.ratio",
                                LocalLanguagePack.current,
                                state.aspectRatio().orElse(
                                    UiTranslations.translate("ui.automatic", LocalLanguagePack.current),
                                ),
                            ),
                        )
                    }
                }
                if (PlayerAdvancedCapability.DEINTERLACE in capabilities) {
                    TextButton(onClick = {
                        command { controller.setDeinterlace(!state.deinterlace()) }
                    }) {
                        Text(if (state.deinterlace()) "Deinterlace: On" else "Deinterlace: Off")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.done") } },
    )
}

@Composable
private fun DelayControl(label: String, delayMillis: Long, update: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            UiTranslations.format("dynamic.delay.milliseconds", LocalLanguagePack.current, label, delayMillis),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { update((delayMillis - 50L).coerceAtLeast(-600_000L)) }) {
            Text("-50")
        }
        TextButton(onClick = { update((delayMillis + 50L).coerceAtMost(600_000L)) }) {
            Text("+50")
        }
    }
}

private fun nextAspectRatio(current: Optional<String>): Optional<String> {
    val values = listOf(null, "16:9", "4:3", "2.35:1", "1:1")
    val next = values[(values.indexOf(current.orElse(null)) + 1) % values.size]
    return Optional.ofNullable(next)
}

private enum class PlayerCustomAction(val labelKey: String) {
    SEEK_BACK("ui.seek.back.ten.seconds"),
    SEEK_FORWARD("ui.seek.forward.ten.seconds"),
    PLAY_PAUSE("ui.play.pause"),
    SPEED("ui.speed"),
    MUTE("ui.mute"),
}

@Composable
private fun PlayerCustomButtonDialog(
    left: PlayerCustomAction,
    right: PlayerCustomAction,
    updateLeft: (PlayerCustomAction) -> Unit,
    updateRight: (PlayerCustomAction) -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ui.custom.player.buttons") },
        text = {
            Column {
                TextButton(onClick = { updateLeft(nextCustomAction(left)) }) {
                    Text(
                        UiTranslations.format(
                            "dynamic.left.button",
                            LocalLanguagePack.current,
                            UiTranslations.translate(left.labelKey, LocalLanguagePack.current),
                        ),
                    )
                }
                TextButton(onClick = { updateRight(nextCustomAction(right)) }) {
                    Text(
                        UiTranslations.format(
                            "dynamic.right.button",
                            LocalLanguagePack.current,
                            UiTranslations.translate(right.labelKey, LocalLanguagePack.current),
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ui.done") } },
    )
}

private fun nextCustomAction(value: PlayerCustomAction): PlayerCustomAction {
    val values = PlayerCustomAction.entries
    return values[(value.ordinal + 1) % values.size]
}

@Composable
private fun UnavailablePlayerSurface() {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text("ui.no.media.backend.is.available", color = Color.White)
    }
}

private suspend fun persistProgress(
    controller: PlayerController,
    playback: ComposePlayerPlayback,
): Boolean {
    val state = runCatching { playback.snapshot() }.getOrNull() ?: return false
    if (state.durationMillis() <= 0) return false
    return withContext(Dispatchers.IO) {
        saveProgress(controller, state.positionMillis(), state.durationMillis())
    }
}

private fun saveProgress(
    controller: PlayerController,
    positionMillis: Long,
    durationMillis: Long,
): Boolean = runCatching {
    controller.updatePlayback(positionMillis, durationMillis)
}.isSuccess
