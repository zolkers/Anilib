package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.player.PlayerPlayback
import fr.vriege.anilib.feature.player.PlayerAdvancedCapability
import fr.vriege.anilib.feature.player.PlayerAdvancedState
import fr.vriege.anilib.feature.player.PlayerOrientationPolicy
import fr.vriege.anilib.feature.player.ui.PlayerController
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlin.math.abs

private const val PROGRESS_INTERVAL_MILLIS = 2_000L

@Composable
internal fun PlayerVideoSurface(
    controller: PlayerController,
    playback: PlayerPlayback,
    applyOrientationPolicy: (PlayerOrientationPolicy) -> Unit,
    requestPictureInPicture: () -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    setBackgroundAudio: (Boolean) -> Unit,
    enableAndroidControls: Boolean,
    enableDesktopControls: Boolean,
) {
    val bridge = playback as? ComposePlayerPlayback
    if (bridge == null) {
        UnavailablePlayerSurface()
        return
    }
    val player = rememberVideoPlayerState()
    val preferences = controller.preferences()
    var controlsVisible by remember(bridge) { mutableStateOf(true) }
    var locked by remember(bridge) { mutableStateOf(false) }
    var brightness by remember(bridge) { mutableFloatStateOf(1f) }
    var volume by remember(bridge) { mutableFloatStateOf(bridge.snapshot().volume()) }
    var orientation by remember(bridge) { mutableStateOf(PlayerOrientationPolicy.SYSTEM) }
    var customMenu by remember(bridge) { mutableStateOf(false) }
    var advancedMenu by remember(bridge) { mutableStateOf(false) }
    var backgroundAudio by remember(bridge) { mutableStateOf(false) }
    var leftAction by remember(bridge) { mutableStateOf(PlayerCustomAction.SEEK_BACK) }
    var rightAction by remember(bridge) { mutableStateOf(PlayerCustomAction.SEEK_FORWARD) }
    var drag by remember(bridge) { mutableStateOf(Offset.Zero) }
    var dragStartX by remember(bridge) { mutableFloatStateOf(0f) }
    DisposableEffect(orientation, applyOrientationPolicy) {
        applyOrientationPolicy(orientation)
        onDispose { applyOrientationPolicy(PlayerOrientationPolicy.SYSTEM) }
    }
    DisposableEffect(bridge, setPlayerActive, setBackgroundAudio) {
        setPlayerActive(true)
        onDispose {
            setBackgroundAudio(false)
            setPlayerActive(false)
        }
    }
    DisposableEffect(bridge, player) {
        bridge.attach(player)
        onDispose {
            persistProgress(controller, bridge)
            bridge.detach(player)
        }
    }
    LaunchedEffect(bridge, player) {
        while (!player.hasMedia && player.error == null) withFrameNanos { }
        bridge.resumeWhenReady()
    }
    LaunchedEffect(bridge, player.isPlaying) {
        var lastPersistence = 0L
        while (true) {
            withFrameNanos { frameTime ->
                if (frameTime - lastPersistence >= PROGRESS_INTERVAL_MILLIS * 1_000_000L) {
                    persistProgress(controller, bridge)
                    lastPersistence = frameTime
                }
            }
        }
    }

    fun seekBy(deltaMillis: Long) {
        val state = bridge.snapshot()
        val maximum = if (state.durationMillis() > 0) state.durationMillis() else Long.MAX_VALUE
        controller.seekTo((state.positionMillis() + deltaMillis).coerceIn(0L, maximum))
    }

    fun togglePlayback() {
        if (player.isPlaying) controller.pause() else controller.play()
    }

    fun cycleSpeed() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val current = bridge.snapshot().playbackSpeed()
        val index = speeds.indexOfFirst { it > current + 0.01f }
        controller.setPlaybackSpeed(if (index < 0) speeds.first() else speeds[index])
    }

    fun cycleOrientation() {
        val values = PlayerOrientationPolicy.entries
        orientation = values[(orientation.ordinal + 1) % values.size]
    }

    fun execute(action: PlayerCustomAction) {
        when (action) {
            PlayerCustomAction.SEEK_BACK -> seekBy(-10_000L)
            PlayerCustomAction.SEEK_FORWARD -> seekBy(10_000L)
            PlayerCustomAction.PLAY_PAUSE -> togglePlayback()
            PlayerCustomAction.SPEED -> cycleSpeed()
            PlayerCustomAction.MUTE -> {
                volume = if (volume > 0f) 0f else 1f
                controller.setVolume(volume)
            }
            PlayerCustomAction.ORIENTATION -> cycleOrientation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (!locked) controlsVisible = !controlsVisible },
                    onDoubleTap = { position ->
                        if (!locked) {
                            when {
                                position.x < size.width / 3f -> seekBy(-10_000L)
                                position.x > size.width * 2f / 3f -> seekBy(10_000L)
                                else -> togglePlayback()
                            }
                        }
                    },
                )
            }
            .pointerInput(locked, brightness, volume) {
                detectDragGestures(
                    onDragStart = {
                        drag = Offset.Zero
                        dragStartX = it.x
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        if (!locked && abs(drag.x) > abs(drag.y) && abs(drag.x) >= 48f) {
                            seekBy(if (drag.x < 0f) -10_000L else 10_000L)
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
                IconButton(onClick = { locked = false }, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.LockOpen, "Unlock controls", tint = Color.White)
                }
            } else if (controlsVisible) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-10_000L) }) {
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
                    IconButton(onClick = { seekBy(10_000L) }) {
                        Icon(Icons.Default.Forward10, "Seek forward", tint = Color.White)
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                ) {
                    Slider(
                        value = player.sliderPos.coerceIn(0f, 1000f),
                        onValueChange = player::seekStart,
                        onValueChangeFinished = {
                            player.seekFinished()
                            persistProgress(controller, bridge)
                        },
                        valueRange = 0f..1000f,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = volume,
                            onValueChange = {
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
                        if (
                            preferences.introEndMillis() > 0L &&
                            bridge.snapshot().positionMillis() < preferences.introEndMillis()
                        ) {
                            TextButton(onClick = { controller.seekTo(preferences.introEndMillis()) }) {
                                Text("Skip intro", color = Color.White)
                            }
                        }
                        val playbackState = bridge.snapshot()
                        if (
                            preferences.outroDurationMillis() > 0L &&
                            playbackState.durationMillis() > 0L &&
                            playbackState.positionMillis() >=
                            playbackState.durationMillis() - preferences.outroDurationMillis()
                        ) {
                            TextButton(onClick = {
                                controller.seekTo(playbackState.durationMillis())
                                controller.markCompleted()
                            }) {
                                Text("Skip outro", color = Color.White)
                            }
                        }
                        TextButton(onClick = { execute(leftAction) }) {
                            Text("L: ${leftAction.label}", color = Color.White)
                        }
                        TextButton(onClick = { execute(rightAction) }) {
                            Text("R: ${rightAction.label}", color = Color.White)
                        }
                        TextButton(onClick = ::cycleSpeed) {
                            Text("${bridge.snapshot().playbackSpeed()}×", color = Color.White)
                        }
                        if (enableAndroidControls) {
                            TextButton(onClick = requestPictureInPicture) {
                                Text("PiP", color = Color.White)
                            }
                            TextButton(onClick = {
                                backgroundAudio = !backgroundAudio
                                setBackgroundAudio(backgroundAudio)
                            }) {
                                Text(
                                    if (backgroundAudio) "Background on" else "Background off",
                                    color = Color.White,
                                )
                            }
                        }
                        if (enableDesktopControls && controller.advancedCapabilities().isNotEmpty()) {
                            TextButton(onClick = { advancedMenu = true }) {
                                Text("Advanced", color = Color.White)
                            }
                        }
                        IconButton(onClick = ::cycleOrientation) {
                            Icon(Icons.Default.ScreenRotation, "Orientation", tint = Color.White)
                        }
                        IconButton(onClick = { customMenu = true }) {
                            Icon(Icons.Default.Tune, "Custom buttons", tint = Color.White)
                        }
                        IconButton(onClick = { locked = true }) {
                            Icon(Icons.Default.Lock, "Lock controls", tint = Color.White)
                        }
                        IconButton(onClick = player::toggleFullscreen) {
                            Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White)
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
        title = { Text("Desktop player controls") },
        text = {
            Column {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (PlayerAdvancedCapability.LOOP in capabilities) {
                    TextButton(onClick = { command { controller.setLoop(!state.loop()) } }) {
                        Text(if (state.loop()) "Loop: On" else "Loop: Off")
                    }
                }
                if (PlayerAdvancedCapability.RESTART in capabilities) {
                    TextButton(onClick = { command(controller::restart) }) { Text("Restart") }
                }
                if (PlayerAdvancedCapability.FRAME_STEP in capabilities) {
                    TextButton(onClick = { command(controller::frameStep) }) { Text("Next frame") }
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
                        Text("Aspect ratio: ${state.aspectRatio().orElse("Auto")}")
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
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
    )
}

@Composable
private fun DelayControl(label: String, delayMillis: Long, update: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${delayMillis}ms", modifier = Modifier.weight(1f))
        TextButton(onClick = { update((delayMillis - 50L).coerceAtLeast(-600_000L)) }) {
            Text("-50")
        }
        TextButton(onClick = { update((delayMillis + 50L).coerceAtMost(600_000L)) }) {
            Text("+50")
        }
    }
}

private fun nextAspectRatio(current: java.util.Optional<String>): java.util.Optional<String> {
    val values = listOf(null, "16:9", "4:3", "2.35:1", "1:1")
    val next = values[(values.indexOf(current.orElse(null)) + 1) % values.size]
    return java.util.Optional.ofNullable(next)
}

private enum class PlayerCustomAction(val label: String) {
    SEEK_BACK("-10s"),
    SEEK_FORWARD("+10s"),
    PLAY_PAUSE("Play"),
    SPEED("Speed"),
    MUTE("Mute"),
    ORIENTATION("Rotate"),
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
        title = { Text("Custom player buttons") },
        text = {
            Column {
                TextButton(onClick = { updateLeft(nextCustomAction(left)) }) {
                    Text("Left button: ${left.label}")
                }
                TextButton(onClick = { updateRight(nextCustomAction(right)) }) {
                    Text("Right button: ${right.label}")
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
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
        Text("No media backend is available.", color = Color.White)
    }
}

private fun persistProgress(controller: PlayerController, playback: ComposePlayerPlayback) {
    runCatching {
        val state = playback.snapshot()
        if (state.durationMillis() > 0) {
            controller.updatePlayback(state.positionMillis(), state.durationMillis())
        }
    }
}
