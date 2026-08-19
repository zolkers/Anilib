package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.player.ui.PlayerController
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceEpisode
import fr.vriege.anilib.feature.source.SourceTitleDetails
import fr.vriege.anilib.feature.source.SourceWebPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SourceMediaDetails(
    val details: SourceTitleDetails,
    val episodes: List<SourceEpisode>,
    val chapters: List<SourceContentUnit>,
)

@Composable
internal fun SourceTitleScreen(
    item: SourceCatalogueItem,
    presentation: DiscoveryPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    sourceName: String,
    sourceWebPage: SourceWebPage?,
    library: LibraryPresentation,
    downloads: DownloadPresentation,
    shareController: ShareController,
    openTracking: (LibraryItemId) -> Unit,
    openWebPage: (SourceWebPage) -> Unit,
    navigateUp: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    val artworkEnvironment = LocalExtensionIconEnvironment.current
    var activeReader by remember(item.id()) { mutableStateOf<ReaderController?>(null) }
    var activePlayer by remember(item.id()) { mutableStateOf<PlayerController?>(null) }
    var loaded by remember(item.id()) { mutableStateOf<Result<SourceMediaDetails>?>(null) }
    var requestRevision by remember(item.id()) { mutableIntStateOf(0) }
    var libraryItem by remember(item.id()) {
        mutableStateOf(presentation.libraryItem(item.id()).orElse(null))
    }
    var favorite by remember(item.id()) {
        mutableStateOf(
            libraryItem?.let { library.details(it).map { details -> details.favorite() }.orElse(false) } ?: false,
        )
    }
    var notice by remember(item.id()) { mutableStateOf<String?>(null) }
    val webPage = remember(item.id(), sourceWebPage) {
        presentation.titleWebPage(item.id()).orElse(sourceWebPage)
    }

    CrashSafeLaunchedEffect(item.id(), requestRevision) {
        loaded = withContext(Dispatchers.IO) {
            runCatching {
                SourceMediaDetails(
                    presentation.titleDetails(item),
                    presentation.episodes(item.id()),
                    presentation.contentUnits(item.id()),
                )
            }
        }
    }

    activeReader?.let { controller ->
        DisposableEffect(controller) { onDispose { controller.close() } }
        ReaderScreen(
            controller,
            artworkEnvironment?.decode ?: { null },
            {},
            { _, _ -> },
        ) { activeReader = null }
        return
    }
    activePlayer?.let { controller ->
        DisposableEffect(controller) { onDispose { controller.close() } }
        PlayerSelectionScreen(
            controller,
            {},
            {},
            {},
            {},
            false,
            true,
        ) { activePlayer = null }
        return
    }

    when (val result = loaded) {
        null -> SourceMediaLoading(item.title(), navigateUp)
        else -> result.fold(
            onSuccess = { content ->
                suspend fun ensureLibraryItem(): LibraryItemId {
                    libraryItem?.let { return it }
                    return withContext(Dispatchers.IO) { presentation.addToLibrary(item) }
                        .also { libraryItem = it }
                }

                fun openEpisode(episode: SourceEpisode) {
                    runCatching { player.open(content.details.title(), episode.id()) }
                        .onSuccess {
                            notice = null
                            activePlayer = it
                        }
                        .onFailure { notice = it.message ?: "The episode could not be opened" }
                }

                fun openChapter(chapter: SourceContentUnit) {
                    runCatching { reader.open(content.details.title(), chapter.id()) }
                        .onSuccess {
                            notice = null
                            activeReader = it
                        }
                        .onFailure { notice = it.message ?: "The chapter could not be opened" }
                }

                val hasEpisodes = content.episodes.isNotEmpty()
                val hasChapters = content.chapters.isNotEmpty()
                MediaDetailsScreen(
                    model = MediaDetailsUiModel(
                        title = content.details.title(),
                        authors = content.details.authors(),
                        status = content.details.status().name.lowercase().replace('_', ' '),
                        sourceName = sourceName,
                        description = content.details.description(),
                        genres = content.details.genres(),
                    ),
                    artwork = { modifier ->
                        RemoteArtwork(
                            content.details.thumbnail().orElse(null),
                            content.details.title(),
                            modifier,
                        )
                    },
                    favorite = favorite,
                    contentLabel = if (hasEpisodes) {
                        "${content.episodes.size} episodes"
                    } else {
                        "${content.chapters.size} chapters"
                    },
                    canTrack = true,
                    canOpenWeb = webPage != null,
                    canDownload = hasEpisodes || hasChapters,
                    primaryLabel = if (hasEpisodes) "Watch" else "Read",
                    canOpenPrimary = hasEpisodes || hasChapters,
                    errors = listOfNotNull(notice),
                    toggleFavorite = {
                        scope.launch {
                            runCatching {
                                val id = ensureLibraryItem()
                                val next = !favorite
                                library.setFavorite(setOf(id), next)
                                next
                            }.onSuccess {
                                favorite = it
                                notice = if (it) "Added to Library" else "Removed from favorites"
                            }.onFailure { notice = it.message ?: "Favorite could not be updated" }
                        }
                    },
                    track = {
                        scope.launch {
                            runCatching { ensureLibraryItem() }
                                .onSuccess {
                                    notice = null
                                    openTracking(it)
                                }
                                .onFailure { notice = it.message ?: "Tracking could not be opened" }
                        }
                    },
                    openWeb = { webPage?.let(openWebPage) },
                    download = {
                        scope.launch {
                            runCatching {
                                val id = ensureLibraryItem()
                                check(downloads.canEnqueue(id)) { "This title cannot be downloaded" }
                                downloads.enqueue(id)
                            }.onSuccess {
                                notice = "Download queued"
                            }.onFailure { notice = it.message ?: "Download could not be queued" }
                        }
                    },
                    share = {
                        shareController.share(
                            content.details.title(),
                            webPage?.location()?.toString() ?: content.details.title(),
                        )
                    },
                    edit = null,
                    openPrimary = {
                        content.episodes.firstOrNull()?.let(::openEpisode)
                            ?: content.chapters.firstOrNull()?.let(::openChapter)
                    },
                    goBack = navigateUp,
                ) {
                    if (hasEpisodes) {
                        item { MediaContentHeading("${content.episodes.size} episodes") }
                        items(content.episodes.size, key = { content.episodes[it].id().toString() }) { index ->
                            val episode = content.episodes[index]
                            val metadata = listOfNotNull(
                                episode.uploadedAt().map(mediaDateTimeFormatter::format).orElse(null),
                                episode.scanlator().orElse(null),
                            ).ifEmpty {
                                listOf(
                                    if (episode.episodeNumber() >= 0) {
                                        "Episode ${episode.episodeNumber()}"
                                    } else {
                                        "Available"
                                    },
                                )
                            }.joinToString(" · ")
                            MediaUnitRow(
                                title = episode.title(),
                                summary = metadata,
                                open = { openEpisode(episode) },
                                download = {
                                    scope.launch {
                                        runCatching {
                                            downloads.enqueue(ensureLibraryItem(), episode.id().value())
                                        }.onSuccess {
                                            notice = "Download queued"
                                        }.onFailure {
                                            notice = it.message ?: "Download could not be queued"
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (hasChapters) {
                        item { MediaContentHeading("${content.chapters.size} chapters") }
                        items(content.chapters.size, key = { content.chapters[it].id().toString() }) { index ->
                            val chapter = content.chapters[index]
                            MediaUnitRow(
                                title = chapter.title(),
                                summary = chapter.publishedAt()
                                    .map(mediaDateTimeFormatter::format)
                                    .orElse("Available"),
                                open = { openChapter(chapter) },
                                download = {
                                    scope.launch {
                                        runCatching { downloads.enqueue(ensureLibraryItem(), chapter.id()) }
                                            .onSuccess { notice = "Download queued" }
                                            .onFailure {
                                                notice = it.message ?: "Download could not be queued"
                                            }
                                    }
                                },
                            )
                        }
                    }
                    if (!hasEpisodes && !hasChapters) {
                        item {
                            Text(
                                "No episodes or chapters available",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            onFailure = { failure ->
                SourceMediaFailure(item.title(), failure.message, navigateUp) {
                    loaded = null
                    requestRevision++
                }
            },
        )
    }
}

@Composable
private fun SourceMediaLoading(title: String, navigateUp: () -> Unit) {
    SourceMediaStateScaffold(title, navigateUp) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SourceMediaFailure(
    title: String,
    message: String?,
    navigateUp: () -> Unit,
    retry: () -> Unit,
) {
    SourceMediaStateScaffold(title, navigateUp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message ?: "Unable to load this title", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceMediaStateScaffold(
    title: String,
    navigateUp: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
