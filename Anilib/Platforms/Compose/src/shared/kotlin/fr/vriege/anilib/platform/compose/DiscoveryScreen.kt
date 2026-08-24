package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot
import fr.vriege.anilib.feature.discovery.DiscoveryCatalogueDisplayMode
import fr.vriege.anilib.feature.discovery.MigrationOptions
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.discovery.ui.DiscoverySourceSection
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateCandidate
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryPage
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.player.EpisodeSnapshot
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceContentUnitId
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.SourceDescriptor
import fr.vriege.anilib.feature.source.SourceEpisode
import fr.vriege.anilib.feature.source.SourceEpisodeId
import fr.vriege.anilib.feature.source.SourceFilterDefinition
import fr.vriege.anilib.feature.source.SourceFilterType
import fr.vriege.anilib.feature.source.SourceFilterValue
import fr.vriege.anilib.feature.source.InstalledSourceExtension
import fr.vriege.anilib.feature.source.SourceListing
import fr.vriege.anilib.feature.source.SourcePage
import fr.vriege.anilib.feature.source.SourcePermission
import fr.vriege.anilib.feature.source.SourcePreferenceType
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.source.SourceWebPage
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.framework.http.HttpCookieJar
import java.util.Locale
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues

internal enum class BrowseSection(val label: String, val kind: SourceContentKind?) {
    ANIME_SOURCES("Anime sources", SourceContentKind.ANIME),
    MANGA_SOURCES("Manga sources", SourceContentKind.MANGA),
    ANIME_EXTENSIONS("Anime extensions", SourceContentKind.ANIME),
    MANGA_EXTENSIONS("Manga extensions", SourceContentKind.MANGA),
    MIGRATE_ANIME("Migrate anime", SourceContentKind.ANIME),
    MIGRATE_MANGA("Migrate manga", SourceContentKind.MANGA),
}

internal class DiscoveryRouteState {
    val section = mutableStateOf(BrowseSection.ANIME_SOURCES)
    val selectedSource = mutableStateOf<SourceDescriptor?>(null)
    val selectedSourceItem = mutableStateOf<SourceCatalogueItem?>(null)
    val selectedGlobalItem = mutableStateOf<SourceCatalogueItem?>(null)
    val listing = mutableStateOf(SourceListing.POPULAR)
    val globalSearch = mutableStateOf(false)
    val globalQuery = mutableStateOf("")
    val browserPage = mutableStateOf<SourceWebPage?>(null)
}

@Composable
internal fun rememberDiscoveryRouteState(): DiscoveryRouteState = remember { DiscoveryRouteState() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryScreen(
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    routeState: DiscoveryRouteState,
    detailPlatform: DetailPlatform,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    tracking: TrackerPresentation,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    navigationVisibilityChanged: (Boolean) -> Unit,
    manageExtensions: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var section by routeState.section
    var selectedSource by routeState.selectedSource
    var selectedSourceItem by routeState.selectedSourceItem
    var selectedGlobalItem by routeState.selectedGlobalItem
    var listing by routeState.listing
    var globalSearch by routeState.globalSearch
    var globalQuery by routeState.globalQuery
    var globalSearchRevision by remember { mutableIntStateOf(0) }
    val globalSearchFocus = rememberSearchFocusRequester(globalSearch)
    var sourceBrowseRevision by remember { mutableIntStateOf(0) }
    var filteringSourceLanguages by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var browserPage by routeState.browserPage
    var extensionRevision by remember { mutableIntStateOf(0) }
    var updatingSources by remember { mutableStateOf<Set<SourceId>>(emptySet()) }
    val mainDestination = selectedSource == null && selectedGlobalItem == null && browserPage == null
    DisposableEffect(mainDestination, navigationVisibilityChanged) {
        navigationVisibilityChanged(mainDestination)
        onDispose { }
    }
    DisposableEffect(extensionRepositories) {
        val observation = extensionRepositories.observe { extensionRevision++ }
        onDispose { runCatching { observation.close() } }
    }
    val extensionView = remember(extensionRepositories, extensionRevision) {
        extensionRepositories.snapshot()
    }
    val updatesBySource = remember(extensionView) {
        extensionView.updates().flatMap { candidate ->
            candidate.available().sources().flatMap { source ->
                source.runtimeSourceIds().map { sourceId -> sourceId to candidate }
            }
        }.toMap()
    }
    val packagesBySource = remember(extensionView) {
        extensionView.packages().flatMap { extension ->
            extension.sources().flatMap { source ->
                source.runtimeSourceIds().map { sourceId -> sourceId to extension }
            }
        }.toMap()
    }
    fun updateSource(sourceId: SourceId) {
        val candidate = updatesBySource[sourceId.toString()] ?: return
        updatingSources = updatingSources + sourceId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    extensionRepositories.update(candidate.available()).get()
                }
            }.onSuccess {
                browseError = null
            }.onFailure {
                browseError = it.message ?: "Extension update failed."
            }
            updatingSources = updatingSources - sourceId
        }
    }

    browserPage?.let { page ->
        BrowserScreen(
            page,
            browserCookies,
            browserRuntimeStatus,
            close = { browserPage = null },
            challengeComplete = {
                browserPage = null
                sourceBrowseRevision++
            },
        )
        return
    }

    val source = selectedSource
    if (source != null) {
        SourceCatalogueScreen(
            source = source,
            listing = listing,
            selectedItem = selectedSourceItem,
            selectedItemChanged = { selectedSourceItem = it },
            presentation = presentation,
            library = library,
            openWebPage = { browserPage = it },
            browserCookies = browserCookies,
            browserRuntimeStatus = browserRuntimeStatus,
            detailPlatform = detailPlatform,
            reader = reader,
            player = player,
            downloads = downloads,
            tracking = tracking,
            openLibraryReader = openReader,
            readerError = readerError,
            openLibraryPlayer = openPlayer,
            enqueueDownload = enqueueDownload,
            downloadError = downloadError,
            openTracking = openTracking,
            navigateUp = {
                selectedSourceItem = null
                selectedSource = null
            },
        )
        return
    }

    val globalItem = selectedGlobalItem
    if (globalItem != null) {
        val globalSource = presentation.source(globalItem.id().sourceId()).orElse(null)
        if (globalSource == null) {
            DiscoveryFailure("This source is no longer installed") { selectedGlobalItem = null }
        } else {
            SourceTitleScreen(
                item = globalItem,
                presentation = presentation,
                library = library,
                browserCookies = browserCookies,
                browserRuntimeStatus = browserRuntimeStatus,
                detailPlatform = detailPlatform,
                reader = reader,
                player = player,
                downloads = downloads,
                tracking = tracking,
                openLibraryReader = openReader,
                readerError = readerError,
                openLibraryPlayer = openPlayer,
                enqueueDownload = enqueueDownload,
                downloadError = downloadError,
                openTracking = openTracking,
                navigateUp = { selectedGlobalItem = null },
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (globalSearch) {
                        OutlinedTextField(
                            value = globalQuery,
                            onValueChange = { globalQuery = it },
                            singleLine = true,
                            placeholder = { Text("ui.search.all.sources") },
                            keyboardOptions = searchKeyboardOptions(),
                            keyboardActions = searchKeyboardActions { globalSearchRevision++ },
                            modifier = Modifier.fillMaxWidth().searchFocus(globalSearchFocus),
                        )
                    } else {
                        Text("ui.browse")
                    }
                },
                navigationIcon = {
                    if (globalSearch) {
                        IconButton(onClick = {
                            globalSearch = false
                            globalQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.close.search")
                        }
                    }
                },
                actions = {
                    if (section.extensionTab() && !globalSearch) {
                        IconButton(onClick = manageExtensions) {
                            Icon(Icons.Default.Settings, contentDescription = "ui.manage.extension.repositories")
                        }
                    }
                    if (section.sourceTab() && !globalSearch) {
                        TextButton(onClick = { filteringSourceLanguages = true }) {
                            Text("ui.languages")
                        }
                    }
                    if (section.sourceTab() && !globalSearch) {
                        IconButton(onClick = { globalSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "ui.global.search")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 12.dp) {
                BrowseSection.entries.forEach { tab ->
                    Tab(
                        selected = section == tab,
                        onClick = {
                            section = tab
                            globalSearch = false
                            globalQuery = ""
                        },
                            text = {
                                val count = extensionView.updates().count { candidate ->
                                    candidate.available().contentKind().matches(tab.kind)
                                }
                                Text(if (count > 0 && (tab.sourceTab() || tab.extensionTab())) {
                                    "${tab.label} ($count)"
                                } else {
                                    tab.label
                                })
                            },
                    )
                }
            }
            if (section.extensionTab()) {
                OutlinedTextField(
                    value = globalQuery,
                    onValueChange = { globalQuery = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (globalQuery.isNotEmpty()) {
                            IconButton(onClick = { globalQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "ui.close.search")
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            if (section.kind == SourceContentKind.ANIME) {
                                "Search anime extensions"
                            } else {
                                "Search manga extensions"
                            },
                        )
                    },
                    keyboardOptions = searchKeyboardOptions(),
                    keyboardActions = searchKeyboardActions(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            browseError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (section.extensionTab()) {
                ExtensionDiscoveryList(
                    extensionRepositories,
                    apkExtensionPlatform,
                    presentation,
                    if (section.kind == SourceContentKind.ANIME) {
                        ExtensionContentKind.ANIME
                    } else {
                        ExtensionContentKind.MANGA
                    },
                    globalQuery,
                    manageExtensions,
                    onSourcesChanged = {
                        extensionRevision++
                        sourceBrowseRevision++
                        section = if (section.kind == SourceContentKind.ANIME) {
                            BrowseSection.ANIME_SOURCES
                        } else {
                            BrowseSection.MANGA_SOURCES
                        }
                    },
                    onSourcePreferenceChanged = { sourceBrowseRevision++ },
                )
            } else if (globalSearch && globalQuery.isNotBlank() && section.sourceTab()) {
                GlobalSearchContent(
                    presentation,
                    library,
                    section.kind!!,
                    globalQuery,
                    globalSearchRevision,
                    open = { item -> selectedGlobalItem = item },
                )
            } else {
                when (section) {
                    BrowseSection.ANIME_SOURCES,
                    BrowseSection.MANGA_SOURCES,
                    -> {
                        val sectionsResult = remember(section, sourceBrowseRevision, extensionRevision) {
                            runCatching { presentation.sourceSections(section.kind!!) }
                        }
                        CrashSafeLaunchedEffect(sectionsResult) {
                            sectionsResult.exceptionOrNull()?.let {
                                browseError = it.message ?: "Sources could not be loaded."
                            }
                        }
                        SourceList(
                            sections = sectionsResult.getOrDefault(emptyList()),
                            supportsLatest = presentation::supportsLatest,
                            pinnedSources = remember(sourceBrowseRevision) {
                                presentation.pinnedSources()
                            },
                            updatesBySource = updatesBySource,
                            updatingSources = updatingSources,
                            togglePinned = { sourceId ->
                                runCatching {
                                    val pinned = sourceId in presentation.pinnedSources()
                                    presentation.setSourcePinned(sourceId, !pinned)
                                }.onSuccess {
                                    browseError = null
                                    sourceBrowseRevision++
                                }.onFailure {
                                    browseError = it.message ?: "Source pinning failed."
                                }
                            },
                            open = { descriptor, nextListing ->
                                selectedSource = descriptor
                                listing = nextListing
                            },
                            openWeb = { descriptor ->
                                presentation.sourceWebPage(descriptor.id())
                                    .ifPresent { browserPage = it }
                            },
                            update = ::updateSource,
                        )
                    }
                    BrowseSection.ANIME_EXTENSIONS,
                    BrowseSection.MANGA_EXTENSIONS,
                    -> Unit
                    BrowseSection.MIGRATE_ANIME -> MigrationContent(
                        SourceContentKind.ANIME,
                        presentation,
                        library,
                    )
                    BrowseSection.MIGRATE_MANGA -> MigrationContent(
                        SourceContentKind.MANGA,
                        presentation,
                        library,
                    )
                }
            }
        }
    }
    if (filteringSourceLanguages && section.sourceTab()) {
        val kind = section.kind!!
        SourceLanguageDialog(
            available = remember(kind, sourceBrowseRevision) {
                presentation.availableSourceLanguages(kind)
            },
            enabled = remember(kind, sourceBrowseRevision) {
                presentation.enabledSourceLanguages(kind)
            },
            dismiss = { filteringSourceLanguages = false },
            toggle = { language, enabled ->
                runCatching { presentation.setSourceLanguageEnabled(kind, language, enabled) }
                    .onSuccess {
                        browseError = null
                        sourceBrowseRevision++
                    }.onFailure {
                        browseError = it.message ?: "Source language selection failed."
                    }
            },
        )
    }
}

@Composable
private fun SourceList(
    sections: List<DiscoverySourceSection>,
    supportsLatest: (SourceId) -> Boolean,
    pinnedSources: Set<SourceId>,
    updatesBySource: Map<String, ExtensionUpdateCandidate>,
    updatingSources: Set<SourceId>,
    togglePinned: (SourceId) -> Unit,
    open: (SourceDescriptor, SourceListing) -> Unit,
    openWeb: (SourceDescriptor) -> Unit,
    update: (SourceId) -> Unit,
) {
    if (sections.isEmpty()) {
        EmptyDiscovery("No sources installed")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            item(key = "language-${section.languageTag()}") {
                Text(
                    text = languageName(section.languageTag()),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
            val orderedSources = section.sources().sortedByDescending { it.id() in pinnedSources }
            items(orderedSources, key = { it.id().toString() }) { source ->
                var menuExpanded by remember(source.id()) { mutableStateOf(false) }
                val updateAvailable = updatesBySource[source.id().toString()] != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { open(source, SourceListing.POPULAR) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceBadge(source)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.displayName(), fontWeight = FontWeight.Medium)
                        Text(
                            text = "v${source.extensionVersion()} · ${languageName(source.languageTag())}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (updateAvailable) {
                        Text(
                            if (source.id() in updatingSources) "Updating…" else "Update",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "ui.source.actions")
                        }
                        DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("ui.popular") },
                                onClick = {
                                    menuExpanded = false
                                    open(source, SourceListing.POPULAR)
                                },
                            )
                            if (supportsLatest(source.id())) {
                                DropdownMenuItem(
                                    text = { Text("ui.latest") },
                                    onClick = {
                                        menuExpanded = false
                                        open(source, SourceListing.LATEST)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("ui.open.in.webview") },
                                onClick = {
                                    menuExpanded = false
                                    openWeb(source)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (source.id() in pinnedSources) "Unpin" else "Pin")
                                },
                                onClick = {
                                    menuExpanded = false
                                    togglePinned(source.id())
                                },
                            )
                            if (updateAvailable) {
                                DropdownMenuItem(
                                    text = { Text("ui.update.extension") },
                                    enabled = source.id() !in updatingSources,
                                    onClick = {
                                        menuExpanded = false
                                        update(source.id())
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun SourceLanguageDialog(
    available: List<String>,
    enabled: Set<String>,
    dismiss: () -> Unit,
    toggle: (String, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ui.source.languages") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(available, key = { it }) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(language, language !in enabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = language in enabled,
                            onCheckedChange = null,
                        )
                        Text(languageName(language))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("ui.done") } },
    )
}

@Composable
private fun SourceBadge(source: SourceDescriptor) {
    Card(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                source.displayName().take(1).uppercase(Locale.ROOT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCatalogueScreen(
    source: SourceDescriptor,
    listing: SourceListing,
    selectedItem: SourceCatalogueItem?,
    selectedItemChanged: (SourceCatalogueItem?) -> Unit,
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    openWebPage: (SourceWebPage) -> Unit,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    detailPlatform: DetailPlatform,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    tracking: TrackerPresentation,
    openLibraryReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openLibraryPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    navigateUp: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var selectedListing by remember(source.id(), listing) { mutableStateOf(listing) }
    var query by remember(source.id()) { mutableStateOf("") }
    var searchActive by remember(source.id()) { mutableStateOf(false) }
    val searchFocus = rememberSearchFocusRequester(searchActive)
    var page by remember(source.id(), selectedListing) { mutableIntStateOf(1) }
    var grid by remember(source.id()) {
        mutableStateOf(
            presentation.catalogueDisplayMode(source.id()) == DiscoveryCatalogueDisplayMode.GRID,
        )
    }
    var showFilters by remember(source.id()) { mutableStateOf(false) }
    var showPreferences by remember(source.id()) { mutableStateOf(false) }
    var filterValues by remember(source.id()) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var preferenceRevision by remember(source.id()) { mutableIntStateOf(0) }
    var requestRevision by remember(source.id()) { mutableIntStateOf(0) }
    var libraryRevision by remember(source.id()) { mutableIntStateOf(0) }
    var notice by remember(source.id()) { mutableStateOf<String?>(null) }
    var definitions by remember(source.id()) {
        mutableStateOf<List<SourceFilterDefinition>>(emptyList())
    }
    val supportsLatest = remember(source.id()) { presentation.supportsLatest(source.id()) }
    val preferenceDefinitions = remember(source.id(), preferenceRevision) {
        presentation.preferences(source.id())
    }
    val sourceWebPage = remember(source.id()) { presentation.sourceWebPage(source.id()).orElse(null) }
    var result by remember(source.id(), selectedListing, query, page, filterValues, preferenceRevision) {
        mutableStateOf<Result<SourcePage>?>(null)
    }

    DisposableEffect(library, source.id()) {
        val observation = library.observe { libraryRevision++ }
        onDispose { observation.close() }
    }

    selectedItem?.let { item ->
        SourceTitleScreen(
            item = item,
            presentation = presentation,
            library = library,
            browserCookies = browserCookies,
            browserRuntimeStatus = browserRuntimeStatus,
            detailPlatform = detailPlatform,
            reader = reader,
            player = player,
            downloads = downloads,
            tracking = tracking,
            openLibraryReader = openLibraryReader,
            readerError = readerError,
            openLibraryPlayer = openLibraryPlayer,
            enqueueDownload = enqueueDownload,
            downloadError = downloadError,
            openTracking = openTracking,
            navigateUp = { selectedItemChanged(null) },
        )
        return
    }
    CrashSafeLaunchedEffect(source.id(), preferenceRevision) {
        val discovered = withContext(Dispatchers.IO) {
            runCatching { presentation.filters(source.id()) }
        }
        discovered
            .onSuccess { definitions = it }
            .onFailure { notice = it.message ?: "Source filters could not be loaded" }
    }
    CrashSafeLaunchedEffect(
        source.id(),
        selectedListing,
        query,
        page,
        filterValues,
        preferenceRevision,
        requestRevision,
    ) {
        if (query.isNotBlank()) {
            delay(SOURCE_SEARCH_DEBOUNCE_MILLIS)
        }
        result = null
        result = withContext(Dispatchers.IO) {
            runCatching {
                val filters = filterValues.map { SourceFilterValue(it.key, it.value) }
                if (query.isBlank() && filterValues.isEmpty()) {
                    presentation.browse(source.id(), selectedListing, page, 30, filters)
                } else {
                    presentation.search(source.id(), query, page, 30, filters)
                }
            }
        }
    }

    if (showFilters) {
        SourceFilterSheet(
            definitions = definitions,
            selected = filterValues,
            dismiss = { showFilters = false },
            apply = {
                filterValues = it
                page = 1
                showFilters = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                page = 1
                            },
                            placeholder = {
                                Text(
                                    UiTranslations.format(
                                        "dynamic.search.in",
                                        LocalLanguagePack.current,
                                        source.displayName(),
                                    ),
                                )
                            },
                            singleLine = true,
                            keyboardOptions = searchKeyboardOptions(),
                            keyboardActions = searchKeyboardActions { requestRevision++ },
                            modifier = Modifier.fillMaxWidth().searchFocus(searchFocus),
                        )
                    } else {
                        Column {
                            Text(source.displayName())
                            Text(
                                when {
                                    filterValues.isNotEmpty() -> "ui.filters"
                                    selectedListing == SourceListing.POPULAR -> "ui.popular"
                                    else -> "ui.latest"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            query = ""
                        } else {
                            navigateUp()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    if (sourceWebPage != null) {
                        IconButton(onClick = { openWebPage(sourceWebPage) }) {
                            Icon(Icons.Default.Public, contentDescription = "ui.open.source.website")
                        }
                    }
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "ui.search")
                        }
                    }
                    IconButton(onClick = {
                        val nextMode = if (grid) {
                            DiscoveryCatalogueDisplayMode.LIST
                        } else {
                            DiscoveryCatalogueDisplayMode.GRID
                        }
                        runCatching { presentation.setCatalogueDisplayMode(source.id(), nextMode) }
                            .onSuccess {
                                grid = nextMode == DiscoveryCatalogueDisplayMode.GRID
                            }
                            .onFailure {
                                notice = it.message ?: "Display choice could not be saved"
                            }
                    }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "ui.display.mode",
                        )
                    }
                    if (preferenceDefinitions.isNotEmpty()) {
                        IconButton(onClick = { showPreferences = !showPreferences }) {
                            Icon(Icons.Default.Settings, contentDescription = "ui.source.settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val filterTabIndex = if (supportsLatest) 2 else 1
            val selectedTabIndex = if (filterValues.isNotEmpty() && definitions.isNotEmpty()) {
                filterTabIndex
            } else if (selectedListing == SourceListing.LATEST && supportsLatest) {
                1
            } else {
                0
            }
            PrimaryScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 12.dp) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        selectedListing = SourceListing.POPULAR
                        filterValues = emptyMap()
                        query = ""
                        searchActive = false
                        page = 1
                    },
                    text = { Text("ui.popular") },
                )
                if (supportsLatest) {
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            selectedListing = SourceListing.LATEST
                            filterValues = emptyMap()
                            query = ""
                            searchActive = false
                            page = 1
                        },
                        text = { Text("ui.latest") },
                    )
                }
                if (definitions.isNotEmpty()) {
                    Tab(
                        selected = selectedTabIndex == filterTabIndex,
                        onClick = { showFilters = true },
                        text = {
                            val label = UiTranslations.translate("ui.filters", LocalLanguagePack.current)
                            Text(if (filterValues.isEmpty()) label else "$label (${filterValues.size})")
                        },
                    )
                }
            }
            if (showPreferences) {
                PreferencePanel(preferenceDefinitions) { id, value ->
                    presentation.setPreference(source.id(), id, value)
                    preferenceRevision++
                    notice = "Source preferences updated"
                }
            }
            notice?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            val sourcePage = result?.getOrNull()
            when {
                result == null -> DiscoveryLoading("Loading ${source.displayName()}…")
                sourcePage == null -> DiscoveryFailure(
                    result?.exceptionOrNull()?.message ?: "Unable to load this source",
                ) { requestRevision++ }
                else -> {
                val memberships = remember(sourcePage, libraryRevision) {
                    sourcePage.items().associate { item ->
                        item.id() to presentation.libraryItem(item.id()).orElse(null)
                    }
                }
                CatalogueContent(
                    page = sourcePage,
                    grid = grid,
                    open = { item ->
                        selectedItemChanged(item)
                    },
                    libraryItem = { item -> memberships[item.id()] },
                    toggleLibraryMembership = { item, existingId ->
                        scope.launch {
                            val changed = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (existingId == null) {
                                        presentation.addToLibrary(item)
                                        true
                                    } else {
                                        presentation.removeFromLibrary(item.id())
                                        false
                                    }
                                }
                            }
                            changed.onSuccess { added ->
                                notice = if (added) {
                                    "${item.title()} added to Library"
                                } else {
                                    "${item.title()} removed from Library"
                                }
                            }.onFailure {
                                notice = it.message ?: "The library could not be updated"
                            }
                        }
                    },
                    webPage = { item -> presentation.titleWebPage(item.id()).orElse(null) },
                    openWebPage = openWebPage,
                )
                Pagination(page, sourcePage.hasNextPage()) { next -> page = next }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTitleScreen(
    item: SourceCatalogueItem,
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    detailPlatform: DetailPlatform,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    tracking: TrackerPresentation,
    openLibraryReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openLibraryPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    navigateUp: () -> Unit,
) {
    var indexedItemId by remember(item.id()) {
        mutableStateOf(presentation.indexedItem(item.id()).orElse(null))
    }
    var indexFailure by remember(item.id()) { mutableStateOf<String?>(null) }
    var indexRevision by remember(item.id()) { mutableIntStateOf(0) }

    CrashSafeLaunchedEffect(item.id(), indexedItemId, indexRevision) {
        if (indexedItemId == null) {
            runCatching {
                withContext(Dispatchers.IO) { presentation.index(item) }
            }.onSuccess {
                indexedItemId = it
                indexFailure = null
            }.onFailure {
                indexFailure = it.message ?: "The source title could not be indexed"
            }
        }
    }

    val canonicalId = indexedItemId
    if (canonicalId == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(item.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (indexFailure == null) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(indexFailure!!)
                        TextButton(onClick = {
                            indexFailure = null
                            indexRevision++
                        }) { Text("ui.retry") }
                    }
                }
            }
        }
        return
    }

    DetailsDestination(
        presentation = library,
        discovery = presentation,
        browserCookies = browserCookies,
        browserRuntimeStatus = browserRuntimeStatus,
        detailPlatform = detailPlatform,
        reader = reader,
        player = player,
        downloads = downloads,
        tracking = tracking,
        destination = LibraryNavigationState(LibraryPage.DETAILS, Optional.of(canonicalId)),
        navigate = { transition ->
            val navigator = LibraryNavigator()
            transition(navigator)
            navigator.state().selectedTitle().ifPresent { indexedItemId = it }
        },
        openReader = openLibraryReader,
        readerError = readerError,
        openPlayer = openLibraryPlayer,
        enqueueDownload = enqueueDownload,
        downloadError = downloadError,
        openTracking = openTracking,
        goBackOverride = navigateUp,
        removedFromLibrary = {},
    )
}

@Composable
private fun ColumnScope.CatalogueContent(
    page: SourcePage,
    grid: Boolean,
    open: (SourceCatalogueItem) -> Unit,
    libraryItem: (SourceCatalogueItem) -> LibraryItemId?,
    toggleLibraryMembership: (SourceCatalogueItem, LibraryItemId?) -> Unit,
    webPage: (SourceCatalogueItem) -> SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    if (page.items().isEmpty()) {
        EmptyDiscovery("No results found")
        return
    }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                page.items(),
                key = { it.id().toString() },
                contentType = { "catalogue-cover" },
            ) { item ->
                CatalogueCard(
                    item,
                    open,
                    libraryItem(item),
                    toggleLibraryMembership,
                    webPage(item),
                    openWebPage,
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(page.items(), key = { it.id().toString() }) { item ->
                CatalogueRow(
                    item,
                    open,
                    libraryItem(item),
                    toggleLibraryMembership,
                    webPage(item),
                    openWebPage,
                )
            }
        }
    }
}

@Composable
private fun CatalogueCard(
    item: SourceCatalogueItem,
    open: (SourceCatalogueItem) -> Unit,
    libraryItemId: LibraryItemId?,
    toggleLibraryMembership: (SourceCatalogueItem, LibraryItemId?) -> Unit,
    webPage: SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { open(item) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            RemoteArtwork(
                item.thumbnail().orElse(null),
                item.title(),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CatalogueItemMenu(item, libraryItemId, toggleLibraryMembership, webPage, openWebPage)
            }
        }
    }
}

@Composable
private fun CatalogueRow(
    item: SourceCatalogueItem,
    open: (SourceCatalogueItem) -> Unit,
    libraryItemId: LibraryItemId?,
    toggleLibraryMembership: (SourceCatalogueItem, LibraryItemId?) -> Unit,
    webPage: SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open(item) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteArtwork(
            item.thumbnail().orElse(null),
            item.title(),
            modifier = Modifier.size(width = 64.dp, height = 92.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title(), fontWeight = FontWeight.Medium)
            Text(
                item.description(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CatalogueItemMenu(item, libraryItemId, toggleLibraryMembership, webPage, openWebPage)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun CatalogueItemMenu(
    item: SourceCatalogueItem,
    libraryItemId: LibraryItemId?,
    toggleLibraryMembership: (SourceCatalogueItem, LibraryItemId?) -> Unit,
    webPage: SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    var expanded by remember(item.id()) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = UiTranslations.format(
                    "dynamic.actions.for",
                    LocalLanguagePack.current,
                    item.title(),
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(if (libraryItemId == null) "ui.add.to.library" else "ui.remove.from.library")
                },
                leadingIcon = {
                    Icon(
                        if (libraryItemId == null) Icons.Default.Add else Icons.Default.Delete,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    toggleLibraryMembership(item, libraryItemId)
                },
            )
            webPage?.let { page ->
                DropdownMenuItem(
                    text = { Text("ui.open.in.webview") },
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                    onClick = {
                        expanded = false
                        openWebPage(page)
                    },
                )
            }
        }
    }
}

@Composable
private fun Pagination(page: Int, hasNext: Boolean, select: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { select(page - 1) }, enabled = page > 1) { Text("ui.previous") }
        Text(
            UiTranslations.format("dynamic.page", LocalLanguagePack.current, page),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(onClick = { select(page + 1) }, enabled = hasNext) { Text("ui.next") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SourceFilterSheet(
    definitions: List<SourceFilterDefinition>,
    selected: Map<String, String>,
    dismiss: () -> Unit,
    apply: (Map<String, String>) -> Unit,
) {
    var draft by remember(definitions, selected) { mutableStateOf(selected) }
    val definitionsById = remember(definitions) { definitions.associateBy(SourceFilterDefinition::id) }
    fun update(definition: SourceFilterDefinition, value: String) {
        draft = if (value == definition.defaultValue()) {
            draft - definition.id()
        } else {
            draft + (definition.id() to value)
        }
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ui.filters", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "ui.source.filters.provided.by.extension",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { draft = emptyMap() }) { Text("ui.reset") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                items(definitions, key = SourceFilterDefinition::id) { definition ->
                    val value = draft[definition.id()] ?: definition.defaultValue()
                    Box(
                        modifier = Modifier.padding(
                            start = (sourceFilterDepth(definition, definitionsById) * 14).dp,
                        ),
                    ) {
                        SourceFilterControl(
                            definition = definition,
                            value = value,
                            update = { update(definition, it) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = dismiss) { Text("ui.cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { apply(draft) }) { Text("ui.apply") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFilterControl(
    definition: SourceFilterDefinition,
    value: String,
    update: (String) -> Unit,
) {
    when (definition.type()) {
        SourceFilterType.HEADER -> if (definition.label().isNotBlank()) {
            Text(
                definition.label(),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        SourceFilterType.SEPARATOR -> HorizontalDivider()
        SourceFilterType.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = update,
            label = { Text(definition.label()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SourceFilterType.CHECKBOX -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { update((value != "true").toString()) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value == "true", onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(definition.label(), modifier = Modifier.weight(1f))
        }
        SourceFilterType.TRI_STATE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(definition.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ignore", "include", "exclude").forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { update(option) },
                        label = {
                            Text(
                                when (option) {
                                    "include" -> "ui.filter.include"
                                    "exclude" -> "ui.filter.exclude"
                                    else -> "ui.filter.ignore"
                                },
                            )
                        },
                        leadingIcon = if (value == option) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        SourceFilterType.SELECT,
        SourceFilterType.SORT,
        -> SourceOptionFilter(definition, value, update)
    }
}

@Composable
private fun SourceOptionFilter(
    definition: SourceFilterDefinition,
    value: String,
    update: (String) -> Unit,
) {
    var expanded by remember(definition.id()) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    definition.label(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp),
        ) {
            definition.options().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    leadingIcon = if (option == value) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        update(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun sourceFilterDepth(
    definition: SourceFilterDefinition,
    definitions: Map<String, SourceFilterDefinition>,
): Int {
    var depth = 0
    var groupId = definition.groupId()
    val visited = mutableSetOf<String>()
    while (groupId.isNotBlank() && visited.add(groupId)) {
        depth++
        groupId = definitions[groupId]?.groupId().orEmpty()
    }
    return depth.coerceAtMost(3)
}

@Composable
private fun PreferencePanel(
    preferences: List<SourcePreferenceSnapshot>,
    update: (String, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("ui.source.settings", fontWeight = FontWeight.SemiBold)
        preferences.forEach { snapshot ->
            when (snapshot.definition().type()) {
                SourcePreferenceType.SWITCH -> SwitchRow(
                    snapshot.definition().title(),
                    snapshot.value() == "true",
                ) { update(snapshot.definition().id(), it.toString()) }
                SourcePreferenceType.TEXT -> OutlinedTextField(
                    value = snapshot.value(),
                    onValueChange = { update(snapshot.definition().id(), it) },
                    label = { Text(snapshot.definition().title()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                SourcePreferenceType.SELECT -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    snapshot.definition().options().forEach { option ->
                        FilterChip(
                            selected = snapshot.value() == option,
                            onClick = { update(snapshot.definition().id(), option) },
                            label = { Text(option) },
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, update: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = update)
    }
}

@Composable
private fun GlobalSearchContent(
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    kind: SourceContentKind,
    query: String,
    requestRevision: Int,
    open: (SourceCatalogueItem) -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var revision by remember(kind, query) { mutableIntStateOf(0) }
    var libraryRevision by remember(kind, query) { mutableIntStateOf(0) }
    var notice by remember(kind, query) { mutableStateOf<String?>(null) }
    var result by remember(kind, query) {
        mutableStateOf<Result<Map<SourceId, SourcePage>>?>(null)
    }
    CrashSafeLaunchedEffect(kind, query, revision, requestRevision) {
        delay(SOURCE_SEARCH_DEBOUNCE_MILLIS)
        result = null
        result = withContext(Dispatchers.IO) {
            runCatching { presentation.globalSearch(kind, query, 10) }
        }
    }
    DisposableEffect(library, kind, query) {
        val observation = library.observe { libraryRevision++ }
        onDispose { observation.close() }
    }
    val sourceNames = remember(kind) {
        presentation.sourceSections(kind)
            .flatMap { it.sources() }
            .associate { it.id() to it.displayName() }
    }
    val sources = result?.getOrNull()
    if (result == null) {
        DiscoveryLoading("Searching installed sources…")
    } else if (sources == null) {
        DiscoveryFailure(result?.exceptionOrNull()?.message ?: "Global search failed") {
            revision++
        }
    } else {
        val memberships = remember(sources, libraryRevision) {
            sources.values.asSequence()
                .flatMap { page -> page.items().asSequence() }
                .associate { item -> item.id() to presentation.libraryItem(item.id()).orElse(null) }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            notice?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            sources.forEach { (sourceId, page) ->
                item {
                    Text(
                        sourceNames[sourceId] ?: sourceId.toString(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(page.items(), key = { it.id().toString() }) { item ->
                    CatalogueRow(
                        item = item,
                        open = open,
                        libraryItemId = memberships[item.id()],
                        toggleLibraryMembership = { selected, existingId ->
                            scope.launch {
                                val changed = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (existingId == null) {
                                            presentation.addToLibrary(selected)
                                            true
                                        } else {
                                            presentation.removeFromLibrary(selected.id())
                                            false
                                        }
                                    }
                                }
                                changed.onSuccess { added ->
                                    notice = if (added) {
                                        "${selected.title()} added to Library"
                                    } else {
                                        "${selected.title()} removed from Library"
                                    }
                                }.onFailure {
                                    notice = it.message ?: "The library could not be updated"
                                }
                            }
                        },
                        webPage = null,
                        openWebPage = {},
                    )
                }
            }
        }
    }
}

private const val SOURCE_SEARCH_DEBOUNCE_MILLIS = 400L

@Composable
private fun ExtensionList(
    extensions: List<InstalledSourceExtension>,
    query: String,
    updatesBySource: Map<String, ExtensionUpdateCandidate>,
    packagesBySource: Map<String, ExtensionPackageMetadata>,
    pinnedPackages: Set<String>,
    updatingSources: Set<SourceId>,
    togglePinned: (ExtensionPackageMetadata) -> Unit,
    update: (SourceId) -> Unit,
    manage: () -> Unit,
) {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visible = extensions.filter { extension ->
        normalizedQuery.isEmpty()
                || extension.manifest().component().displayName().lowercase(Locale.ROOT).contains(normalizedQuery)
                || extension.source().displayName().lowercase(Locale.ROOT).contains(normalizedQuery)
    }
    var expanded by remember(extensions) { mutableStateOf<Set<SourceId>>(emptySet()) }
    if (visible.isEmpty()) {
        if (query.isBlank()) {
            EmptyExtensions(manage)
        } else {
            EmptyDiscovery("No extensions match your search.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "extension.installed",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        items(visible, key = { it.source().id().toString() }) { extension ->
            val source = extension.source()
            val updateAvailable = updatesBySource[source.id().toString()] != null
            val extensionPackage = packagesBySource[source.id().toString()]
            var menuExpanded by remember(source.id()) { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable {
                        expanded = if (source.id() in expanded) {
                            expanded - source.id()
                        } else {
                            expanded + source.id()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtensionIcon(
                    extensionPackage?.icon()?.orElse(null),
                    extension.manifest().component().displayName(),
                    size = 40.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(extension.manifest().component().displayName(), fontWeight = FontWeight.Medium)
                    Text(
                        "${languageName(source.languageTag())} - v${source.extensionVersion()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when {
                        source.id() in updatingSources -> "Updating…"
                        updateAvailable -> "Update"
                        else -> "Installed"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "extension.actions")
                    }
                    DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (source.id() in expanded) "Hide details" else "Details") },
                            onClick = {
                                menuExpanded = false
                                expanded = if (source.id() in expanded) {
                                    expanded - source.id()
                                } else {
                                    expanded + source.id()
                                }
                            },
                        )
                        if (extensionPackage != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (extensionPackage.packageName() in pinnedPackages) "Unpin" else "Pin")
                                },
                                onClick = {
                                    menuExpanded = false
                                    togglePinned(extensionPackage)
                                },
                            )
                        }
                        if (updateAvailable) {
                            DropdownMenuItem(
                                text = { Text("ui.update") },
                                enabled = source.id() !in updatingSources,
                                onClick = {
                                    menuExpanded = false
                                    update(source.id())
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("repositories.manage") },
                            onClick = {
                                menuExpanded = false
                                manage()
                            },
                        )
                    }
                }
            }
            if (source.id() in expanded) {
                ExtensionDetails(extension)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun EmptyExtensions(manage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text("extensions.installed.empty", style = MaterialTheme.typography.titleMedium)
            Text(
                "ui.add.a.compatible.repository.then.install.an.extension.for.this.platform",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = manage) { Text("extensions.browse") }
        }
    }
}

@Composable
private fun ExtensionDetails(extension: InstalledSourceExtension) {
    val manifest = extension.manifest()
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(extension.source().displayName(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                UiTranslations.format(
                    "dynamic.bundle.identity",
                    LocalLanguagePack.current,
                    manifest.component().id(),
                    manifest.component().version(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("ui.permissions", fontWeight = FontWeight.Medium)
            if (manifest.permissions().isEmpty()) {
                Text("ui.no.sensitive.permissions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (SourcePermission.NETWORK in manifest.permissions()) {
                    Text("ui.network", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    manifest.networkOrigins().sorted().forEach { origin ->
                        Text("  $origin", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (SourcePermission.CLEARTEXT_NETWORK in manifest.permissions()) {
                    Text("ui.cleartext.network", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MigrationContent(
    kind: SourceContentKind,
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
) {
    val mediaKind = if (kind == SourceContentKind.ANIME) MediaKind.ANIME else MediaKind.MANGA
    val titles = library.library().titles().filter { it.kind() == mediaKind }
    val targets = presentation.sourceSections(kind).flatMap { it.sources() }
    val scope = rememberCrashSafeCoroutineScope()
    val cancellation = remember(kind) { AtomicBoolean(false) }
    var selectedIds by remember(kind) { mutableStateOf<Set<LibraryItemId>>(emptySet()) }
    var selectedTarget by remember(kind) { mutableStateOf<SourceDescriptor?>(null) }
    var preserveTitle by remember(kind) { mutableStateOf(false) }
    var seasonalSearch by remember(kind) { mutableStateOf(kind == SourceContentKind.ANIME) }
    var previews by remember(kind) { mutableStateOf<List<MigrationPreviewEntry>>(emptyList()) }
    var outcomes by remember(kind) { mutableStateOf<List<MigrationOutcome>>(emptyList()) }
    var targetMenuExpanded by remember(kind) { mutableStateOf(false) }
    var working by remember(kind) { mutableStateOf(false) }
    var completed by remember(kind) { mutableIntStateOf(0) }
    var total by remember(kind) { mutableIntStateOf(0) }
    var operation by remember(kind) { mutableStateOf<String?>(null) }
    var message by remember(kind) { mutableStateOf<String?>(null) }

    fun options() = MigrationOptions(preserveTitle, seasonalSearch)

    fun buildPreview() {
        val target = selectedTarget ?: return
        val selectedTitles = titles.filter { it.id() in selectedIds }
        val selectedOptions = options()
        cancellation.set(false)
        previews = emptyList()
        outcomes = emptyList()
        completed = 0
        total = selectedTitles.size
        operation = "Building migration preview"
        message = null
        working = true
        scope.launch {
            for (title in selectedTitles) {
                if (cancellation.get()) {
                    break
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        presentation.migrationCandidates(title.id(), target.id(), selectedOptions, 20)
                    }
                }
                previews = previews + MigrationPreviewEntry(
                    title = title,
                    currentSource = library.details(title.id()).flatMap { it.origin() }
                        .map { it.sourceId() }
                        .orElse("Local or unknown source"),
                    targetSource = target.displayName(),
                    candidates = result.getOrDefault(emptyList()),
                    selectedIndex = 0,
                    error = result.exceptionOrNull()?.message,
                )
                completed++
            }
            working = false
            operation = null
            message = if (cancellation.get()) {
                "Preview cancelled after $completed of $total titles."
            } else {
                "Preview ready for ${previews.count { it.candidates.isNotEmpty() }} of $total titles."
            }
        }
    }

    fun migrate(entries: List<MigrationPreviewEntry>) {
        val selectedOptions = options()
        cancellation.set(false)
        outcomes = emptyList()
        completed = 0
        total = entries.size
        operation = "Migrating titles"
        message = null
        working = true
        scope.launch {
            for (entry in entries) {
                if (cancellation.get()) {
                    break
                }
                val candidate = entry.candidates.getOrNull(entry.selectedIndex)
                if (candidate == null) {
                    outcomes = outcomes + MigrationOutcome(entry, "No migration candidate selected")
                    completed++
                    continue
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching { presentation.migrate(entry.title.id(), candidate, selectedOptions) }
                }
                outcomes = outcomes + MigrationOutcome(entry, result.exceptionOrNull()?.message)
                completed++
            }
            working = false
            operation = null
            val failures = outcomes.count { it.error != null }
            message = when {
                cancellation.get() -> "Migration cancelled after $completed of $total titles."
                failures == 0 -> "$completed titles migrated successfully."
                else -> "${completed - failures} migrated; $failures failed and can be retried."
            }
            if (!cancellation.get() && failures == 0) {
                selectedIds = emptySet()
                previews = emptyList()
            }
        }
    }

    if (titles.isEmpty()) {
        EmptyDiscovery("Add ${kind.name.lowercase(Locale.ROOT)} titles to your Library before migrating.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ui.batch.migration", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == titles.size) {
                                emptySet()
                            } else {
                                titles.mapTo(linkedSetOf()) { it.id() }
                            }
                            previews = emptyList()
                        },
                    ) {
                        Text(if (selectedIds.size == titles.size) "Clear selection" else "Select all")
                    }
                    Box {
                        OutlinedButton(onClick = { targetMenuExpanded = true }) {
                            Text(selectedTarget?.displayName() ?: "Target source")
                        }
                        DropdownMenu(
                            expanded = targetMenuExpanded,
                            onDismissRequest = { targetMenuExpanded = false },
                        ) {
                            targets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text("${target.displayName()} · ${languageName(target.languageTag())}") },
                                    onClick = {
                                        selectedTarget = target
                                        targetMenuExpanded = false
                                        previews = emptyList()
                                    },
                                )
                            }
                        }
                    }
                }
                SwitchRow("Preserve original titles", preserveTitle) {
                    preserveTitle = it
                    previews = emptyList()
                }
                if (kind == SourceContentKind.ANIME) {
                    SwitchRow("Match seasonal anime titles", seasonalSearch) {
                        seasonalSearch = it
                        previews = emptyList()
                    }
                }
                Button(
                    onClick = { buildPreview() },
                    enabled = !working && selectedIds.isNotEmpty() && selectedTarget != null,
                ) {
                    Text(
                        UiTranslations.format(
                            "dynamic.preview.selected",
                            LocalLanguagePack.current,
                            selectedIds.size,
                        ),
                    )
                }
            }
            HorizontalDivider()
        }
        if (working) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(operation ?: "Migration in progress", fontWeight = FontWeight.Medium)
                        Text("$completed / $total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { cancellation.set(true) }) { Text("ui.cancel") }
                    }
                }
            }
        }
        message?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
        }
        items(titles, key = { it.id().value() }) { title ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = title.id() in selectedIds,
                    enabled = !working && targets.isNotEmpty(),
                    onCheckedChange = { checked ->
                        selectedIds = if (checked) selectedIds + title.id() else selectedIds - title.id()
                        previews = emptyList()
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title.title(), fontWeight = FontWeight.Medium)
                    Text(
                        library.details(title.id()).flatMap { it.origin() }
                            .map { it.sourceId() }
                            .orElse("Local or unknown source"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            HorizontalDivider()
        }
        if (previews.isNotEmpty()) {
            item {
                Text(
                    "ui.migration.preview",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(previews, key = { it.title.id().value() }) { preview ->
                MigrationPreviewCard(preview) { selectedCandidate ->
                    previews = previews.map {
                        if (it.title.id() == preview.title.id()) {
                            it.copy(selectedIndex = selectedCandidate)
                        } else {
                            it
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { migrate(previews.filter { it.candidates.isNotEmpty() }) },
                    enabled = !working && previews.any { it.candidates.isNotEmpty() },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ui.migrate.previewed.titles")
                }
            }
        }
        if (outcomes.isNotEmpty()) {
            item {
                Text("ui.migration.results", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            }
            items(outcomes, key = { it.preview.title.id().value() }) { outcome ->
                Text(
                    outcome.error?.let { "${outcome.preview.title.title()}: $it" }
                        ?: "${outcome.preview.title.title()}: migrated",
                    color = if (outcome.error == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            val failed = outcomes.filter { it.error != null }.map { it.preview }
            if (failed.isNotEmpty()) {
                item {
                    TextButton(onClick = { migrate(failed) }, enabled = !working) {
                        Text(UiTranslations.format("dynamic.retry.failed", LocalLanguagePack.current, failed.size))
                    }
                }
            }
        }
    }
}

private data class MigrationPreviewEntry(
    val title: LibraryCard,
    val currentSource: String,
    val targetSource: String,
    val candidates: List<SourceCatalogueItem>,
    val selectedIndex: Int,
    val error: String?,
)

private data class MigrationOutcome(
    val preview: MigrationPreviewEntry,
    val error: String?,
)

@Composable
private fun MigrationPreviewCard(
    preview: MigrationPreviewEntry,
    selectCandidate: (Int) -> Unit,
) {
    var expanded by remember(preview.title.id()) { mutableStateOf(false) }
    val candidate = preview.candidates.getOrNull(preview.selectedIndex)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(preview.title.title(), fontWeight = FontWeight.Medium)
            Text(
                UiTranslations.format("dynamic.current", LocalLanguagePack.current, preview.currentSource),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                UiTranslations.format("dynamic.target", LocalLanguagePack.current, preview.targetSource),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate == null) {
                Text(
                    preview.error ?: UiTranslations.translate(
                        "ui.no.compatible.candidate",
                        LocalLanguagePack.current,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    UiTranslations.format("dynamic.match", LocalLanguagePack.current, candidate.title()),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    candidate.description(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.candidates.size > 1) {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                UiTranslations.format(
                                    "dynamic.choose.match",
                                    LocalLanguagePack.current,
                                    preview.candidates.size,
                                ),
                            )
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            preview.candidates.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option.title()) },
                                    onClick = {
                                        expanded = false
                                        selectCandidate(index)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiscovery(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(28.dp),
        )
    }
}

@Composable
private fun DiscoveryLoading(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiscoveryFailure(message: String, retry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = retry) { Text("ui.retry") }
        }
    }
}

private fun languageName(tag: String): String = when (tag.lowercase(Locale.ROOT)) {
    "und" -> "Local"
    "en" -> "English"
    "fr" -> "French"
    else -> tag
}

private fun BrowseSection.sourceTab(): Boolean =
    this == BrowseSection.ANIME_SOURCES || this == BrowseSection.MANGA_SOURCES

private fun BrowseSection.extensionTab(): Boolean =
    this == BrowseSection.ANIME_EXTENSIONS || this == BrowseSection.MANGA_EXTENSIONS

private fun ExtensionContentKind.matches(kind: SourceContentKind?): Boolean = when (this) {
    ExtensionContentKind.ANIME -> kind == SourceContentKind.ANIME
    ExtensionContentKind.MANGA -> kind == SourceContentKind.MANGA
    ExtensionContentKind.MIXED -> kind != null
    ExtensionContentKind.UNKNOWN -> false
}
