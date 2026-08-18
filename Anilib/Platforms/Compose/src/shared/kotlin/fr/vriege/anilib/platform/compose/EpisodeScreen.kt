package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.player.EpisodeSnapshot
import fr.vriege.anilib.feature.player.PlaybackState
import fr.vriege.anilib.feature.player.ui.PlayerController
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import java.util.Optional
import kotlin.math.roundToInt

/** Shared Aniyomi-style episode list and pre-playback stream selection surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeScreen(
    presentation: PlayerPresentation,
    libraryItemId: LibraryItemId,
    goBack: () -> Unit,
) {
    var revision by remember(presentation, libraryItemId) { mutableIntStateOf(0) }
    var activeController by remember(presentation, libraryItemId) {
        mutableStateOf<PlayerController?>(null)
    }
    var error by remember(presentation, libraryItemId) { mutableStateOf<String?>(null) }
    DisposableEffect(presentation, libraryItemId) {
        val registration = presentation.observe { revision++ }
        onDispose {
            runCatching { registration.close() }
            activeController?.close()
        }
    }
    val controller = activeController
    if (controller != null) {
        PlayerSelectionScreen(controller) { activeController = null }
        return
    }
    val episodesResult = remember(presentation, libraryItemId, revision) {
        runCatching { presentation.episodes(libraryItemId) }
    }
    val episodes = episodesResult.getOrDefault(emptyList())
    val loadError = episodesResult.exceptionOrNull()?.message

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Text(
                "${episodes.size} episodes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            (error ?: loadError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (episodes.isEmpty() && loadError == null) {
                EmptyPage("No episodes are available from this source.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(episodes, key = { it.episode().id().toString() }) { episode ->
                        EpisodeCard(episode) {
                            runCatching {
                                presentation.open(libraryItemId, episode.episode().id())
                            }.onSuccess {
                                error = null
                                activeController = it
                            }.onFailure {
                                error = it.message ?: "The episode could not be opened."
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: EpisodeSnapshot, open: () -> Unit) {
    val playback = episode.playback().orElse(null)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = open),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(episode.episode().title(), fontWeight = FontWeight.SemiBold)
                    Text(
                        episodeMetadata(episode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (playback?.completed() == true) {
                    Text("Watched", color = MaterialTheme.colorScheme.primary)
                }
            }
            val completion = playback?.completion()?.orElse(-1.0) ?: -1.0
            if (completion >= 0.0) {
                LinearProgressIndicator(
                    progress = { completion.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            if (playback != null && !playback.completed()) {
                Text(
                    "Resume at ${formatDuration(playback.positionMillis())}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSelectionScreen(
    controller: PlayerController,
    goBack: () -> Unit,
) {
    var revision by remember(controller) { mutableIntStateOf(0) }
    var commandError by remember(controller) { mutableStateOf<String?>(null) }
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
    val command: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess {
                commandError = null
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
                Text(snapshot.title(), fontWeight = FontWeight.Bold)
                Text(
                    playbackLabel(snapshot.playback()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                commandError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                TextButton(
                    onClick = { command(controller::markCompleted) },
                    enabled = !snapshot.playback().completed(),
                ) {
                    Text(if (snapshot.playback().completed()) "Watched" else "Mark as watched")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

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

private fun episodeMetadata(snapshot: EpisodeSnapshot): String {
    val episode = snapshot.episode()
    val number = if (episode.episodeNumber() < 0) {
        "Episode"
    } else {
        "Episode ${formatEpisodeNumber(episode.episodeNumber())}"
    }
    val scanlator = episode.scanlator().map { " | $it" }.orElse("")
    return number + scanlator
}

private fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.roundToInt().toString() else number.toString()

private fun playbackLabel(state: PlaybackState): String = when {
    state.completed() -> "Watched"
    state.positionMillis() > 0 -> "Resume at ${formatDuration(state.positionMillis())}"
    else -> "Not started"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    val hours = totalSeconds / 3600L
    val minutes = totalSeconds % 3600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
