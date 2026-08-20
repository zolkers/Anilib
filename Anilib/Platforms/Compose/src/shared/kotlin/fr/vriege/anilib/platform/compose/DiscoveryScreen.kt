package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.SourceDescriptor
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
import fr.vriege.anilib.framework.http.HttpCookieJar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues

private enum class BrowseSection(val label: String, val kind: SourceContentKind?) {
    ANIME_SOURCES("Anime sources", SourceContentKind.ANIME),
    MANGA_SOURCES("Manga sources", SourceContentKind.MANGA),
    ANIME_EXTENSIONS("Anime extensions", SourceContentKind.ANIME),
    MANGA_EXTENSIONS("Manga extensions", SourceContentKind.MANGA),
    MIGRATE_ANIME("Migrate anime", SourceContentKind.ANIME),
    MIGRATE_MANGA("Migrate manga", SourceContentKind.MANGA),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryScreen(
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    initialSource: SourceDescriptor?,
    initialListing: SourceListing,
    openDetails: (LibraryItemId, MediaKind, SourceDescriptor, SourceListing) -> Unit,
    returnTargetConsumed: () -> Unit,
    navigationVisibilityChanged: (Boolean) -> Unit,
    manageExtensions: () -> Unit,
) {
    var section by remember { mutableStateOf(BrowseSection.ANIME_SOURCES) }
    var selectedSource by remember { mutableStateOf(initialSource) }
    var listing by remember { mutableStateOf(initialListing) }
    var globalSearch by remember { mutableStateOf(false) }
    var globalQuery by remember { mutableStateOf("") }
    var sourceBrowseRevision by remember { mutableIntStateOf(0) }
    var filteringSourceLanguages by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var browserPage by remember { mutableStateOf<SourceWebPage?>(null) }
    var extensionRevision by remember { mutableIntStateOf(0) }
    var updatingSources by remember { mutableStateOf<Set<SourceId>>(emptySet()) }
    val mainDestination = selectedSource == null && browserPage == null
    val scope = rememberCrashSafeCoroutineScope()
    DisposableEffect(initialSource, initialListing) {
        if (initialSource != null) returnTargetConsumed()
        onDispose { }
    }
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
            presentation = presentation,
            library = library,
            openWebPage = { browserPage = it },
            openDetails = openDetails,
            navigateUp = { selectedSource = null },
        )
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
                            modifier = Modifier.fillMaxWidth(),
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
                GlobalSearchContent(presentation, section.kind!!, globalQuery)
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
    presentation: DiscoveryPresentation,
    library: LibraryPresentation,
    openWebPage: (SourceWebPage) -> Unit,
    openDetails: (LibraryItemId, MediaKind, SourceDescriptor, SourceListing) -> Unit,
    navigateUp: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    var selectedListing by remember(source.id(), listing) { mutableStateOf(listing) }
    var query by remember(source.id()) { mutableStateOf("") }
    var searchActive by remember(source.id()) { mutableStateOf(false) }
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
    var notice by remember(source.id()) { mutableStateOf<String?>(null) }
    val definitions = remember(source.id()) { presentation.filters(source.id()) }
    val preferenceDefinitions = remember(source.id(), preferenceRevision) {
        presentation.preferences(source.id())
    }
    val sourceWebPage = remember(source.id()) { presentation.sourceWebPage(source.id()).orElse(null) }
    val supportsRefresh = remember(source.id()) { presentation.supportsRefresh(source.id()) }
    var result by remember(source.id(), selectedListing, query, page, filterValues, preferenceRevision) {
        mutableStateOf<Result<SourcePage>?>(null)
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
        result = null
        result = withContext(Dispatchers.IO) {
            runCatching {
                val filters = filterValues.map { SourceFilterValue(it.key, it.value) }
                if (query.isBlank()) {
                    presentation.browse(source.id(), selectedListing, page, 30, filters)
                } else {
                    presentation.search(source.id(), query, page, 30, filters)
                }
            }
        }
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(source.displayName())
                            Text(
                                if (selectedListing == SourceListing.POPULAR) "Popular" else "Latest",
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
                    if (supportsRefresh) {
                        IconButton(onClick = {
                            notice = "Rescanning local folders…"
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { presentation.refresh(source.id()) }
                                }.onSuccess {
                                    page = 1
                                    requestRevision++
                                    notice = "Local folders rescanned"
                                }.onFailure {
                                    notice = it.message ?: "Local rescan failed"
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "ui.rescan.source")
                        }
                    }
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
                                notice = if (grid) "Grid view selected" else "List view selected"
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
                    if (definitions.isNotEmpty()) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.Tune, contentDescription = "ui.filters")
                        }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedListing == SourceListing.POPULAR,
                    onClick = { selectedListing = SourceListing.POPULAR; page = 1 },
                    label = { Text("ui.popular") },
                )
                if (presentation.supportsLatest(source.id())) {
                    FilterChip(
                        selected = selectedListing == SourceListing.LATEST,
                        onClick = { selectedListing = SourceListing.LATEST; page = 1 },
                        label = { Text("ui.latest") },
                    )
                }
                if (definitions.isNotEmpty()) {
                    FilterChip(
                        selected = showFilters,
                        onClick = { showFilters = !showFilters },
                        label = { Text("ui.filter") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    )
                }
            }
            if (showFilters) {
                FilterPanel(definitions, filterValues) {
                    filterValues = it
                    page = 1
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
                CatalogueContent(
                    page = sourcePage,
                    grid = grid,
                    open = { item ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { presentation.addToLibrary(item) }
                            }.onSuccess { id ->
                                notice = null
                                openDetails(
                                    id,
                                    if (item.contentKind() == SourceContentKind.ANIME) {
                                        MediaKind.ANIME
                                    } else {
                                        MediaKind.MANGA
                                    },
                                    source,
                                    selectedListing,
                                )
                            }.onFailure {
                                notice = it.message ?: "The title could not be opened"
                            }
                        }
                    },
                    add = { item ->
                        val id = presentation.addToLibrary(item)
                        library.setFavorite(setOf(id), true)
                        notice = "${item.title()} added to Library"
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

@Composable
private fun ColumnScope.CatalogueContent(
    page: SourcePage,
    grid: Boolean,
    open: (SourceCatalogueItem) -> Unit,
    add: (SourceCatalogueItem) -> Unit,
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
            items(page.items(), key = { it.id().toString() }) { item ->
                CatalogueCard(item, open, add, webPage(item), openWebPage)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(page.items(), key = { it.id().toString() }) { item ->
                CatalogueRow(item, open, add, webPage(item), openWebPage)
            }
        }
    }
}

@Composable
private fun CatalogueCard(
    item: SourceCatalogueItem,
    open: (SourceCatalogueItem) -> Unit,
    add: (SourceCatalogueItem) -> Unit,
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
                CatalogueItemMenu(item, add, webPage, openWebPage)
            }
        }
    }
}

@Composable
private fun CatalogueRow(
    item: SourceCatalogueItem,
    open: (SourceCatalogueItem) -> Unit,
    add: (SourceCatalogueItem) -> Unit,
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
        CatalogueItemMenu(item, add, webPage, openWebPage)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun CatalogueItemMenu(
    item: SourceCatalogueItem,
    add: (SourceCatalogueItem) -> Unit,
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
                text = { Text("ui.add.to.library") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    add(item)
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

@Composable
private fun FilterPanel(
    definitions: List<SourceFilterDefinition>,
    values: Map<String, String>,
    update: (Map<String, String>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("ui.filters", fontWeight = FontWeight.SemiBold)
        definitions.forEach { definition ->
            val value = values[definition.id()] ?: definition.defaultValue()
            when (definition.type()) {
                SourceFilterType.HEADER -> Text(definition.label(), fontWeight = FontWeight.Medium)
                SourceFilterType.SEPARATOR -> HorizontalDivider()
                SourceFilterType.TEXT -> OutlinedTextField(
                    value = value,
                    onValueChange = { update(values + (definition.id() to it)) },
                    label = { Text(definition.label()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SourceFilterType.CHECKBOX -> SwitchRow(definition.label(), value == "true") {
                    update(values + (definition.id() to it.toString()))
                }
                SourceFilterType.TRI_STATE -> OptionChips(
                    definition,
                    listOf("ignore", "include", "exclude"),
                    value,
                    values,
                    update,
                )
                SourceFilterType.SELECT,
                SourceFilterType.SORT,
                -> OptionChips(definition, definition.options(), value, values, update)
            }
        }
        TextButton(onClick = { update(emptyMap()) }) { Text("ui.reset") }
    }
    HorizontalDivider()
}

@Composable
private fun OptionChips(
    definition: SourceFilterDefinition,
    options: List<String>,
    value: String,
    values: Map<String, String>,
    update: (Map<String, String>) -> Unit,
) {
    Column {
        Text(definition.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { update(values + (definition.id() to option)) },
                    label = { Text(option) },
                )
            }
        }
    }
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
    kind: SourceContentKind,
    query: String,
) {
    var revision by remember(kind, query) { mutableIntStateOf(0) }
    var result by remember(kind, query) {
        mutableStateOf<Result<Map<SourceId, SourcePage>>?>(null)
    }
    CrashSafeLaunchedEffect(kind, query, revision) {
        result = null
        result = withContext(Dispatchers.IO) {
            runCatching { presentation.globalSearch(kind, query, 10) }
        }
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                        open = {},
                        add = { presentation.addToLibrary(it) },
                        webPage = null,
                        openWebPage = {},
                    )
                }
            }
        }
    }
}

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
