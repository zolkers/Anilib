package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.library.LibraryCategory
import fr.vriege.anilib.feature.library.LibraryCategoryScope
import fr.vriege.anilib.feature.library.LibraryProgress
import fr.vriege.anilib.feature.library.LibraryTitleMetadata
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.PublicationStatus
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryDetails
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.source.SourceContentUnitId
import fr.vriege.anilib.feature.source.SourceCatalogueItemId
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceEpisodeId
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.source.SourceWebPage
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.tracker.TrackerAiringSchedule
import fr.vriege.anilib.feature.tracker.TrackerMediaMetadata
import fr.vriege.anilib.framework.http.HttpCookieJar
import fr.vriege.anilib.feature.player.EpisodeSnapshot
import java.net.URI
import java.time.Instant
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DetailsDestination(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    detailPlatform: DetailPlatform,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    tracking: TrackerPresentation,
    destination: LibraryNavigationState,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    goBackOverride: (() -> Unit)?,
) {
    val id = destination.selectedTitle().orElse(null)
    val scope = rememberCrashSafeCoroutineScope()
    var relatedBackStack by remember { mutableStateOf<List<LibraryItemId>>(emptyList()) }
    var revision by remember(id) { mutableStateOf(0) }
    var trackerRevision by remember(id) { mutableStateOf(0) }
    var refreshing by remember(id) { mutableStateOf(false) }
    var browserPage by remember(id) {
        mutableStateOf<SourceWebPage?>(null)
    }
    var chapters by remember(id) { mutableStateOf(listOf<SourceContentUnit>()) }
    var episodes by remember(id) { mutableStateOf(listOf<EpisodeSnapshot>()) }
    var unitError by remember(id) { mutableStateOf<String?>(null) }
    var readChapterIds by remember(id) { mutableStateOf(setOf<String>()) }
    ObserveTracking(tracking) { trackerRevision++ }
    val details = remember(id, revision) { id?.let { presentation.details(it).orElse(null) } }
    val navigateBack: () -> Unit = {
        val previous = relatedBackStack.lastOrNull()
        if (previous != null) {
            relatedBackStack = relatedBackStack.dropLast(1)
            navigate { it.openDetails(previous) }
        } else {
            (goBackOverride ?: { navigate(LibraryNavigator::back) })()
        }
    }
    if (browserPage != null) {
        BrowserScreen(
            browserPage!!,
            browserCookies,
            browserRuntimeStatus,
            close = { browserPage = null },
        )
        return
    }
    if (details == null) {
        MissingDetails(navigateBack)
    } else {
        val sourceId = details.origin()
            .map { origin -> SourceId.of(origin.sourceId()) }
            .orElse(null)
        val trackedEntries = remember(details.id(), trackerRevision) {
            tracking.entries(details.id())
        }
        val nextAiring = trackedEntries.asSequence()
            .mapNotNull { it.metadata().nextAiring().orElse(null) }
            .filter { it.airingAt().isAfter(Instant.now()) }
            .minByOrNull(TrackerAiringSchedule::airingAt)
        CrashSafeLaunchedEffect(details.id()) {
            val metadataNeedsRefresh = trackedEntries.any { entry ->
                val metadata = entry.metadata()
                val nextAiring = metadata.nextAiring().orElse(null)
                metadata == TrackerMediaMetadata.empty()
                    || metadata.publishingStatus().orElse("") == "RELEASING" &&
                    (nextAiring == null || !nextAiring.airingAt().isAfter(Instant.now()))
            }
            if (metadataNeedsRefresh) {
                trackedEntries.forEach { entry ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            tracking.refresh(details.id(), entry.trackerId())
                        }
                    }
                }
            }
        }
        val reloadContent: suspend () -> Unit = {
            unitError = null
            if (details.kind() == MediaKind.ANIME) {
                runCatching { withContext(Dispatchers.IO) { player.episodes(details.id()) } }
                    .onSuccess { episodes = it }
                    .onFailure { unitError = it.message ?: "Unable to load episodes." }
            } else {
                runCatching { withContext(Dispatchers.IO) { reader.contentUnits(details.id()) } }
                    .onSuccess { chapters = it }
                    .onFailure { unitError = it.message ?: "Unable to load chapters." }
                readChapterIds = withContext(Dispatchers.IO) {
                    runCatching { reader.readContentIds(details.id()) }.getOrDefault(emptySet())
                }
            }
        }
        CrashSafeLaunchedEffect(details.id(), revision) {
            reloadContent()
        }
        val titlePage = details.origin().flatMap { origin ->
            runCatching {
                discovery.titleWebPage(
                    SourceCatalogueItemId(
                        SourceId.of(origin.sourceId()),
                        origin.sourceItemKey(),
                    ),
                )
            }.getOrDefault(Optional.empty())
        }.orElse(null)
        val sourcePage = details.origin().flatMap { origin ->
            runCatching { discovery.sourceWebPage(SourceId.of(origin.sourceId())) }
                .getOrDefault(Optional.empty())
        }.orElse(null)
        val sourceName = details.origin().map { origin ->
            runCatching {
                discovery.source(SourceId.of(origin.sourceId())).orElse(null)?.displayName()
            }.getOrNull() ?: origin.sourceId()
        }.orElse("Local")
        val categories = remember(details.id(), revision) {
            presentation.library().categoryConfigurations()
                .filter { it.scope().supports(details.kind()) }
        }
        DetailsPage(
            details = details,
            categories = categories,
            sourceName = sourceName,
            artwork = { modifier -> RemoteArtwork(details.artwork().orElse(null), details.title(), modifier) },
            chapters = chapters,
            readChapterIds = readChapterIds,
            chapterProgress = details.progress().orElse(null),
            episodes = episodes,
            unitError = unitError,
            related = presentation.relatedTitles(details.id()),
            canRead = runCatching { reader.canOpen(details.id()) }.getOrDefault(false),
            canWatch = runCatching { player.canOpen(details.id()) }.getOrDefault(false),
            canDownload = runCatching { downloads.canEnqueue(details.id()) }.getOrDefault(false),
            canTrack = true,
            trackingCount = trackedEntries.size,
            nextAiring = nextAiring,
            readerError = readerError,
            downloadError = downloadError,
            read = { chapter -> openReader(details.id(), chapter?.id()) },
            watch = { openPlayer(details.id(), null) },
            watchEpisode = { episode -> openPlayer(details.id(), episode.episode().id()) },
            download = { enqueueDownload(details.id()) },
            downloadChapter = { chapter ->
                runCatching { downloads.enqueue(details.id(), chapter.id()) }
                    .onSuccess { unitError = null }
                    .onFailure { unitError = it.message ?: "The chapter could not be queued." }
            },
            downloadEpisode = { episode ->
                runCatching { downloads.enqueue(details.id(), episode.episode().id().value()) }
                    .onSuccess { unitError = null }
                    .onFailure { unitError = it.message ?: "The episode could not be queued." }
            },
            markChapters = { contentIds, read ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            reader.setRead(details.id(), contentIds, read)
                        }
                    }.onSuccess {
                        unitError = null
                        readChapterIds = if (read) {
                            readChapterIds + contentIds
                        } else {
                            readChapterIds - contentIds
                        }
                    }.onFailure {
                        unitError = it.message ?: "The chapter state could not be updated."
                    }
                }
            },
            markEpisodes = { episodeIds, completed ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            player.setEpisodesCompleted(details.id(), episodeIds, completed)
                        }
                    }.onSuccess {
                        unitError = null
                        episodes = it
                    }.onFailure {
                        unitError = it.message ?: "The episode state could not be updated."
                    }
                }
            },
            track = { openTracking(details.id()) },
            refreshing = refreshing,
            refresh = sourceId?.let { refreshSourceId ->
                {
                    if (!refreshing) {
                        refreshing = true
                        unitError = null
                        scope.launch {
                            val refreshFailure = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (discovery.supportsRefresh(refreshSourceId)) {
                                        discovery.refresh(refreshSourceId)
                                    }
                                }.exceptionOrNull()
                            }
                            if (refreshFailure == null) {
                                reloadContent()
                            } else {
                                unitError = refreshFailure.message ?: "The title could not be refreshed."
                            }
                            refreshing = false
                        }
                    }
                }
            },
            favorite = {
                presentation.setFavorite(setOf(details.id()), !details.favorite())
                revision++
            },
            edit = { title, metadata ->
                presentation.editTitle(details.id(), title, metadata)
                revision++
            },
            createCategory = { value ->
                try {
                    presentation.createCategory(
                        value,
                        if (details.kind() == MediaKind.ANIME) {
                            LibraryCategoryScope.ANIME
                        } else {
                            LibraryCategoryScope.MANGA
                        },
                    )
                    presentation.addToCategory(setOf(details.id()), value)
                } finally {
                    revision++
                }
            },
            addCategory = { value ->
                try {
                    presentation.addToCategory(setOf(details.id()), value)
                } finally {
                    revision++
                }
            },
            removeCategory = { value ->
                try {
                    presentation.removeFromCategory(setOf(details.id()), value)
                } finally {
                    revision++
                }
            },
            deleteCategory = { value ->
                try {
                    presentation.deleteCategory(value)
                } finally {
                    revision++
                }
            },
            openTitleWeb = titlePage?.let { page -> ({ browserPage = page }) },
            openSourceWeb = sourcePage?.let { page -> ({ browserPage = page }) },
            share = {
                detailPlatform.shareController.share(
                    details.title(),
                    titlePage?.location()?.toString() ?: details.title(),
                )
            },
            openRelated = { relatedId ->
                relatedBackStack = relatedBackStack + details.id()
                navigate { it.openDetails(relatedId) }
            },
            goBack = navigateBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsPage(
    details: LibraryDetails,
    categories: List<LibraryCategory>,
    sourceName: String,
    artwork: @Composable (Modifier) -> Unit,
    chapters: List<SourceContentUnit>,
    readChapterIds: Set<String>,
    chapterProgress: LibraryProgress?,
    episodes: List<EpisodeSnapshot>,
    unitError: String?,
    related: List<LibraryCard>,
    canRead: Boolean,
    canWatch: Boolean,
    canDownload: Boolean,
    canTrack: Boolean,
    trackingCount: Int,
    nextAiring: TrackerAiringSchedule?,
    readerError: String?,
    downloadError: String?,
    read: (SourceContentUnit?) -> Unit,
    watch: () -> Unit,
    watchEpisode: (EpisodeSnapshot) -> Unit,
    download: () -> Unit,
    downloadChapter: (SourceContentUnit) -> Unit,
    downloadEpisode: (EpisodeSnapshot) -> Unit,
    markChapters: (Set<String>, Boolean) -> Unit,
    markEpisodes: (Set<SourceEpisodeId>, Boolean) -> Unit,
    track: () -> Unit,
    refreshing: Boolean,
    refresh: (() -> Unit)?,
    favorite: () -> Unit,
    edit: (String, LibraryTitleMetadata) -> Unit,
    createCategory: (String) -> Unit,
    addCategory: (String) -> Unit,
    removeCategory: (String) -> Unit,
    deleteCategory: (String) -> Unit,
    openTitleWeb: (() -> Unit)?,
    openSourceWeb: (() -> Unit)?,
    share: () -> Unit,
    openRelated: (LibraryItemId) -> Unit,
    goBack: () -> Unit,
) {
    var editing by remember(details.id()) { mutableStateOf(false) }
    var editingCategories by remember(details.id()) { mutableStateOf(false) }
    var selectingChapters by remember(details.id()) { mutableStateOf(false) }
    var selectedChapterIds by remember(details.id()) { mutableStateOf<Set<String>>(emptySet()) }
    var selectingEpisodes by remember(details.id()) { mutableStateOf(false) }
    var selectedEpisodeIds by remember(details.id()) { mutableStateOf<Set<SourceEpisodeId>>(emptySet()) }
    val languagePack = LocalLanguagePack.current
    val chaptersLabel = UiTranslations.format(
        "dynamic.chapters.count",
        languagePack,
        chapters.size,
    )
    val episodesLabel = UiTranslations.format(
        "dynamic.episodes.count",
        languagePack,
        episodes.size,
    )
    MediaDetailsScreen(
        model = MediaDetailsUiModel(
            title = details.title(),
            authors = details.authors(),
            status = formatEnum(details.publicationStatus()),
            sourceName = sourceName,
            description = details.description(),
            genres = details.genres(),
            nextAiring = nextAiring,
        ),
        artwork = artwork,
        favorite = details.favorite(),
        contentLabel = when (details.kind()) {
            MediaKind.ANIME -> UiTranslations.format(
                "dynamic.episodes.count",
                LocalLanguagePack.current,
                episodes.size,
            )
            MediaKind.MANGA, MediaKind.NOVEL, MediaKind.OTHER -> UiTranslations.format(
                "dynamic.chapters.count",
                LocalLanguagePack.current,
                chapters.size,
            )
        },
        canTrack = canTrack,
        trackingCount = trackingCount,
        canOpenWeb = openTitleWeb != null || openSourceWeb != null,
        canDownload = canDownload,
        canShare = true,
        primaryLabel = if (canWatch) "ui.watch" else "ui.read",
        canOpenPrimary = canWatch || canRead,
        errors = listOfNotNull(readerError, downloadError, unitError),
        toggleFavorite = favorite,
        refreshing = refreshing,
        refresh = refresh,
        track = track,
        openWeb = openTitleWeb ?: openSourceWeb ?: {},
        download = download,
        share = share,
        manageCategories = { editingCategories = true },
        edit = { editing = true },
        openPrimary = if (canWatch) watch else ({ read(null) }),
        goBack = goBack,
    ) {
            mediaUnitsSection(
                label = chaptersLabel,
                units = chapters,
                key = { it.id().value() },
                title = SourceContentUnit::title,
                summary = { chapter ->
                    val chapterId = chapter.id().value()
                    val chapterRead = chapterId in readChapterIds
                    val resumeAt = chapterProgress?.takeIf {
                        it.contentId() == chapterId && !chapterRead
                    }
                    listOfNotNull(
                        chapter.publishedAt().map(mediaDateTimeFormatter::format).orElse(null),
                        when {
                            chapterRead -> UiTranslations.translate("ui.read", languagePack)
                            resumeAt != null -> UiTranslations.format(
                                "dynamic.page.of",
                                languagePack,
                                resumeAt.position() + 1,
                                resumeAt.extent() + 1,
                            )
                            else -> null
                        },
                    ).ifEmpty { listOf("Available") }.joinToString(" · ")
                },
                muted = { it.id().value() in readChapterIds },
                selection = MediaUnitSelectionUiModel(
                    selecting = selectingChapters,
                    selectedKeys = selectedChapterIds,
                    positiveLabel = "ui.mark.read",
                    negativeLabel = "ui.mark.unread",
                    toggle = {
                        selectingChapters = !selectingChapters
                        if (!selectingChapters) selectedChapterIds = emptySet()
                    },
                    selectAll = { selectedChapterIds = chapters.map { it.id().value() }.toSet() },
                    select = { chapterId ->
                        selectedChapterIds = if (chapterId in selectedChapterIds) {
                            selectedChapterIds - chapterId
                        } else {
                            selectedChapterIds + chapterId
                        }
                    },
                    markPositive = {
                        markChapters(selectedChapterIds, true)
                        selectedChapterIds = emptySet()
                        selectingChapters = false
                    },
                    markNegative = {
                        markChapters(selectedChapterIds, false)
                        selectedChapterIds = emptySet()
                        selectingChapters = false
                    },
                ),
                open = read,
                download = if (canDownload) downloadChapter else null,
            )
            mediaUnitsSection(
                label = episodesLabel,
                units = episodes,
                key = { it.episode().id().value() },
                title = { it.episode().title() },
                summary = { episode ->
                    val playback = episode.playback().orElse(null)
                    listOfNotNull(
                        episode.episode().uploadedAt().map(mediaDateTimeFormatter::format).orElse(null),
                        episode.episode().scanlator().orElse(null),
                        playback?.let {
                            if (it.completed()) {
                                UiTranslations.translate("ui.watched", languagePack)
                            } else {
                                UiTranslations.format(
                                    "dynamic.progress",
                                    languagePack,
                                    formatMediaPosition(it.positionMillis()),
                                )
                            }
                        },
                    ).ifEmpty { listOf("Available") }.joinToString(" · ")
                },
                muted = { it.playback().map { state -> state.completed() }.orElse(false) },
                selection = MediaUnitSelectionUiModel(
                    selecting = selectingEpisodes,
                    selectedKeys = selectedEpisodeIds.map { it.value() }.toSet(),
                    positiveLabel = "ui.mark.watched",
                    negativeLabel = "ui.mark.unwatched",
                    toggle = {
                        selectingEpisodes = !selectingEpisodes
                        if (!selectingEpisodes) selectedEpisodeIds = emptySet()
                    },
                    selectAll = { selectedEpisodeIds = episodes.map { it.episode().id() }.toSet() },
                    select = { episodeId ->
                        val id = episodes.first { it.episode().id().value() == episodeId }.episode().id()
                        selectedEpisodeIds = if (id in selectedEpisodeIds) {
                            selectedEpisodeIds - id
                        } else {
                            selectedEpisodeIds + id
                        }
                    },
                    markPositive = {
                        markEpisodes(selectedEpisodeIds, true)
                        selectedEpisodeIds = emptySet()
                        selectingEpisodes = false
                    },
                    markNegative = {
                        markEpisodes(selectedEpisodeIds, false)
                        selectedEpisodeIds = emptySet()
                        selectingEpisodes = false
                    },
                ),
                open = watchEpisode,
                download = if (canDownload) downloadEpisode else null,
            )
            if (related.isNotEmpty()) {
                item {
                    RelatedTitlesSection(related) { card -> openRelated(card.id()) }
                }
            }
    }
    if (editing) {
        EditLibraryTitleDialog(
            details,
            dismiss = { editing = false },
            save = { title, metadata ->
                edit(title, metadata)
                editing = false
            },
        )
    }
    if (editingCategories) {
        TitleCategoriesDialog(
            details = details,
            categories = categories,
            dismiss = { editingCategories = false },
            create = createCategory,
            add = addCategory,
            remove = removeCategory,
            delete = deleteCategory,
        )
    }
}

@Composable
internal fun TitleCategoriesDialog(
    details: LibraryDetails,
    categories: List<LibraryCategory>,
    dismiss: () -> Unit,
    create: (String) -> Unit,
    add: (String) -> Unit,
    remove: (String) -> Unit,
    delete: (String) -> Unit,
) {
    CategoryAssignmentDialog(
        categories = categories.map(LibraryCategory::name),
        assignedCategories = details.categories(),
        dismiss = dismiss,
        create = create,
        add = add,
        remove = remove,
        delete = delete,
    )
}

@Composable
internal fun RelatedTitlesSection(
    titles: List<LibraryCard>,
    open: (LibraryCard) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ui.related.titles",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                titles.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(titles, key = { it.id().value() }) { card ->
                RelatedTitleCard(card) { open(card) }
            }
        }
    }
}

@Composable
internal fun RelatedTitleCard(card: LibraryCard, open: () -> Unit) {
    Card(
        modifier = Modifier.width(148.dp).clickable(onClick = open),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            Box {
                RemoteArtwork(
                    card.artwork().orElse(null),
                    card.title(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
                )
                if (card.favorite()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "ui.in.library",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(6.dp).size(16.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    card.title(),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatEnum(card.kind()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}


@Composable
internal fun EditLibraryTitleDialog(
    details: LibraryDetails,
    dismiss: () -> Unit,
    save: (String, LibraryTitleMetadata) -> Unit,
) {
    var title by remember(details.id()) { mutableStateOf(details.title()) }
    var description by remember(details.id()) { mutableStateOf(details.description()) }
    var authors by remember(details.id()) { mutableStateOf(details.authors().joinToString(", ")) }
    var artists by remember(details.id()) { mutableStateOf(details.artists().joinToString(", ")) }
    var genres by remember(details.id()) { mutableStateOf(details.genres().joinToString(", ")) }
    var artwork by remember(details.id()) {
        mutableStateOf(details.artwork().map(URI::toString).orElse(""))
    }
    var status by remember(details.id()) { mutableStateOf(details.publicationStatus()) }
    var error by remember(details.id()) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ui.edit.title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("ui.title") })
                OutlinedTextField(description, { description = it }, label = { Text("ui.description") })
                OutlinedTextField(authors, { authors = it }, label = { Text("ui.authors") })
                OutlinedTextField(artists, { artists = it }, label = { Text("ui.artists") })
                OutlinedTextField(genres, { genres = it }, label = { Text("ui.genres") })
                OutlinedTextField(artwork, { artwork = it }, label = { Text("ui.artwork.url") })
                TextButton(onClick = { status = status.next() }) {
                    Text(
                        UiTranslations.format(
                            "dynamic.status",
                            LocalLanguagePack.current,
                            formatEnum(status),
                        ),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    runCatching {
                        val metadata = LibraryTitleMetadata(
                            description,
                            commaSeparated(authors),
                            commaSeparated(artists),
                            status,
                            artwork.trim().takeIf(String::isNotEmpty)
                                ?.let { Optional.of(URI.create(it)) }
                                ?: Optional.empty(),
                            commaSeparated(genres),
                        )
                        save(title.trim(), metadata)
                    }.onFailure { failure ->
                        error = failure.message ?: "Unable to edit this title."
                    }
                },
            ) { Text("ui.save") }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("ui.cancel") }
        },
    )
}

internal fun commaSeparated(value: String): List<String> = value.split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

internal fun PublicationStatus.next(): PublicationStatus =
    PublicationStatus.entries[(ordinal + 1) % PublicationStatus.entries.size]

@Composable
internal fun MissingDetails(openLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ui.this.title.is.no.longer.in.the.library")
            Spacer(Modifier.height(12.dp))
            Button(onClick = openLibrary) { Text("ui.back.to.library") }
        }
    }
}
