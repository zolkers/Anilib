package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.player.PlaybackState
import fr.vriege.anilib.feature.player.PlayerOrientationPolicy
import fr.vriege.anilib.feature.player.PlayerDecoderPolicy
import fr.vriege.anilib.feature.player.PlayerPreferences
import fr.vriege.anilib.feature.player.PlayerQualityPolicy
import fr.vriege.anilib.feature.player.PlayerSubtitlePolicy
import fr.vriege.anilib.feature.player.ui.PlayerController
import java.util.Optional

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSelectionScreen(
    controller: PlayerController,
    applyOrientationPolicy: (PlayerOrientationPolicy) -> Unit,
    requestPictureInPicture: () -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    setBackgroundAudio: (Boolean) -> Unit,
    enableAndroidControls: Boolean,
    enableDesktopControls: Boolean,
    goBack: () -> Unit,
) {
    var revision by remember(controller) { mutableIntStateOf(0) }
    var commandError by remember(controller) { mutableStateOf<String?>(null) }
    var preferenceDialog by remember(controller) { mutableStateOf(false) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    val result = remember(controller, revision) { runCatching { controller.snapshot() } }
    val snapshot = result.getOrNull()
    if (snapshot == null) {
        PlayerSessionError(
            result.exceptionOrNull()?.message ?: "The player session is unavailable.",
            goBack,
        )
        return
    }
    val livePlayback = remember(controller) { mutableStateOf(snapshot.playback()) }
    val command: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess {
                commandError = null
                livePlayback.value = controller.snapshot().playback()
                revision++
            }
            .onFailure { commandError = it.message ?: "The player command failed." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(snapshot.episode().title()) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PlayerVideoSurface(
                    controller,
                    controller.playback(),
                    applyOrientationPolicy,
                    requestPictureInPicture,
                    setPlayerActive,
                    setBackgroundAudio,
                    enableAndroidControls,
                    enableDesktopControls,
                    progressChanged = {
                        livePlayback.value = controller.snapshot().playback()
                    },
                )
            }
            item {
                Text(snapshot.title(), fontWeight = FontWeight.Bold)
                LivePlaybackLabel(livePlayback)
                commandError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { preferenceDialog = true }) {
                    Text(
                        if (controller.hasPreferenceOverride()) "Player preferences · This title"
                        else "Player preferences · Global",
                    )
                }
            }
            item { Text("Video quality", fontWeight = FontWeight.SemiBold) }
            items(snapshot.streams(), key = { it.id() }) { stream ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        command { controller.selectStream(stream.id()) }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (stream.id() == snapshot.selectedStreamId()) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(stream.quality(), fontWeight = FontWeight.SemiBold)
                        Text(
                            stream.location().toString(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stream.format().name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
            item {
                Text("Subtitles", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = snapshot.selectedSubtitleId().isEmpty,
                            onClick = { command { controller.selectSubtitle(Optional.empty()) } },
                            label = { Text("Off") },
                        )
                    }
                    items(snapshot.selectedStream().subtitles(), key = { it.id() }) { subtitle ->
                        FilterChip(
                            selected = snapshot.selectedSubtitleId().orElse(null) == subtitle.id(),
                            onClick = {
                                command { controller.selectSubtitle(Optional.of(subtitle.id())) }
                            },
                            label = { Text(subtitle.label()) },
                        )
                    }
                }
            }
            item {
                WatchedAction(livePlayback) { command(controller::markCompleted) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (preferenceDialog) {
        PlayerPreferenceDialog(
            controller = controller,
            qualities = snapshot.streams().map { it.quality() },
            save = { preferences, titleOverride ->
                command { controller.setPreferences(preferences, titleOverride) }
                preferenceDialog = false
            },
            clearOverride = {
                command(controller::clearPreferenceOverride)
                preferenceDialog = false
            },
            close = { preferenceDialog = false },
        )
    }
}

@Composable
private fun LivePlaybackLabel(playback: State<PlaybackState>) {
    Text(
        playbackLabel(playback.value),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WatchedAction(playback: State<PlaybackState>, markCompleted: () -> Unit) {
    val state = playback.value
    TextButton(onClick = markCompleted, enabled = !state.completed()) {
        Text(if (state.completed()) "Watched" else "Mark as watched")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerLoadingScreen(title: String, goBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            Text("Resolving playable streams…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerPreferenceDialog(
    controller: PlayerController,
    qualities: List<String>,
    save: (PlayerPreferences, Boolean) -> Unit,
    clearOverride: () -> Unit,
    close: () -> Unit,
) {
    val initial = remember(controller) { controller.preferences() }
    var decoder by remember(controller) { mutableStateOf(initial.decoderPolicy()) }
    var audioLanguage by remember(controller) {
        mutableStateOf(initial.preferredAudioLanguage().orElse(""))
    }
    var subtitlePolicy by remember(controller) { mutableStateOf(initial.subtitlePolicy()) }
    var subtitleLanguage by remember(controller) {
        mutableStateOf(initial.preferredSubtitleLanguage().orElse(""))
    }
    var qualityPolicy by remember(controller) { mutableStateOf(initial.qualityPolicy()) }
    var preferredQuality by remember(controller) {
        mutableStateOf(initial.preferredQuality().orElse(qualities.firstOrNull().orEmpty()))
    }
    var introSeconds by remember(controller) { mutableStateOf((initial.introEndMillis() / 1000L).toString()) }
    var outroSeconds by remember(controller) {
        mutableStateOf((initial.outroDurationMillis() / 1000L).toString())
    }
    var completionThreshold by remember(controller) {
        mutableStateOf(initial.completionThresholdPercent().toString())
    }
    var titleOverride by remember(controller) { mutableStateOf(controller.hasPreferenceOverride()) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Player preferences") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { decoder = nextValue(decoder, PlayerDecoderPolicy.entries) }) {
                    Text("Decoder: ${decoder.name.lowercase().replaceFirstChar(Char::uppercase)}")
                }
                OutlinedTextField(
                    value = audioLanguage,
                    onValueChange = { audioLanguage = it },
                    label = { Text("Preferred audio language") },
                    singleLine = true,
                )
                TextButton(onClick = {
                    subtitlePolicy = nextValue(subtitlePolicy, PlayerSubtitlePolicy.entries)
                }) {
                    Text("Subtitles: ${subtitlePolicy.name.lowercase().replace('_', ' ')}")
                }
                OutlinedTextField(
                    value = subtitleLanguage,
                    onValueChange = { subtitleLanguage = it },
                    label = { Text("Preferred subtitle language") },
                    singleLine = true,
                )
                TextButton(onClick = {
                    qualityPolicy = nextValue(qualityPolicy, PlayerQualityPolicy.entries)
                }) {
                    Text("Quality: ${qualityPolicy.name.lowercase()}")
                }
                if (qualityPolicy == PlayerQualityPolicy.PREFERRED) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(qualities.distinct()) { quality ->
                            FilterChip(
                                selected = preferredQuality == quality,
                                onClick = { preferredQuality = quality },
                                label = { Text(quality) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = introSeconds,
                    onValueChange = { introSeconds = it.filter(Char::isDigit).take(4) },
                    label = { Text("Intro ends after (seconds)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = outroSeconds,
                    onValueChange = { outroSeconds = it.filter(Char::isDigit).take(4) },
                    label = { Text("Outro duration (seconds)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = completionThreshold,
                    onValueChange = { completionThreshold = it.filter(Char::isDigit).take(3) },
                    label = { Text("Mark watched at (%)") },
                    supportingText = { Text("An episode is completed automatically at this percentage") },
                    singleLine = true,
                )
                FilterChip(
                    selected = titleOverride,
                    onClick = { titleOverride = !titleOverride },
                    label = { Text("Use only for this title") },
                )
                if (controller.hasPreferenceOverride()) {
                    TextButton(onClick = clearOverride) { Text("Clear title override") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quality = Optional.ofNullable(preferredQuality.takeIf { it.isNotBlank() })
                save(
                    PlayerPreferences(
                        decoder,
                        Optional.ofNullable(audioLanguage.trim().takeIf { it.isNotEmpty() }),
                        subtitlePolicy,
                        Optional.ofNullable(subtitleLanguage.trim().takeIf { it.isNotEmpty() }),
                        qualityPolicy,
                        quality,
                        introSeconds.toLongOrNull()?.coerceIn(0L, 1800L)?.times(1000L) ?: 0L,
                        outroSeconds.toLongOrNull()?.coerceIn(0L, 1800L)?.times(1000L) ?: 0L,
                        completionThreshold.toIntOrNull()?.coerceIn(1, 100) ?: 85,
                    ),
                    titleOverride,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

private fun <T> nextValue(value: T, values: List<T>): T =
    values[(values.indexOf(value) + 1) % values.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSessionError(message: String, goBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun playbackLabel(state: PlaybackState): String = when {
    state.completed() -> "Watched"
    state.positionMillis() > 0 -> "Resume at ${formatMediaPosition(state.positionMillis())}"
    else -> "Not started"
}
