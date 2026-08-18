package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.player.PlayerPlayback
import fr.vriege.anilib.feature.player.ui.PlayerController
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

private const val PROGRESS_INTERVAL_MILLIS = 2_000L

@Composable
internal fun PlayerVideoSurface(
    controller: PlayerController,
    playback: PlayerPlayback,
) {
    val bridge = playback as? ComposePlayerPlayback
    if (bridge == null) {
        UnavailablePlayerSurface()
        return
    }
    val player = rememberVideoPlayerState()
    var controlsVisible by remember(bridge) { mutableStateOf(true) }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        VideoPlayerSurface(playerState = player, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible },
        ) {
            if (player.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            if (controlsVisible) {
                IconButton(
                    onClick = { if (player.isPlaying) controller.pause() else controller.play() },
                    modifier = Modifier.align(Alignment.Center).size(64.dp),
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${player.positionText} / ${player.durationText}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
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
    }
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
