package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.discovery.ui.DiscoverySourceSection
import fr.vriege.anilib.feature.library.MediaKind
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
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
) {
    var section by remember { mutableStateOf(BrowseSection.ANIME_SOURCES) }
    var selectedSource by remember { mutableStateOf<SourceDescriptor?>(null) }
    var listing by remember { mutableStateOf(SourceListing.POPULAR) }
    var globalSearch by remember { mutableStateOf(false) }
    var globalQuery by remember { mutableStateOf("") }
    var sourceBrowseRevision by remember { mutableIntStateOf(0) }
    var filteringSourceLanguages by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var browserPage by remember { mutableStateOf<SourceWebPage?>(null) }

    browserPage?.let { page ->
        BrowserScreen(page, browserCookies, browserRuntimeStatus) { browserPage = null }
        return
    }

    val source = selectedSource
    if (source != null) {
        SourceCatalogueScreen(
            source = source,
            listing = listing,
            presentation = presentation,
            openWebPage = { browserPage = it },
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
                            placeholder = { Text("Search all sources") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Browse")
                    }
                },
                navigationIcon = {
                    if (globalSearch) {
                        IconButton(onClick = {
                            globalSearch = false
                            globalQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    }
                },
                actions = {
                    if (section.sourceTab() && !globalSearch) {
                        TextButton(onClick = { filteringSourceLanguages = true }) {
                            Text("Languages")
                        }
                    }
                    if (section.searchable() && !globalSearch) {
                        IconButton(onClick = { globalSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Global search")
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
                        text = { Text(tab.label) },
                    )
                }
            }
            browseError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (globalSearch && globalQuery.isNotBlank() && section.sourceTab()) {
                GlobalSearchContent(presentation, section.kind!!, globalQuery)
            } else if (globalSearch && section.extensionTab()) {
                ExtensionList(presentation.extensions(section.kind!!), globalQuery)
            } else {
                when (section) {
                    BrowseSection.ANIME_SOURCES,
                    BrowseSection.MANGA_SOURCES,
                    -> SourceList(
                        sections = remember(section, sourceBrowseRevision) {
                            presentation.sourceSections(section.kind!!)
                        },
                        supportsLatest = presentation::supportsLatest,
                        pinnedSources = remember(sourceBrowseRevision) { presentation.pinnedSources() },
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
                    )
                    BrowseSection.ANIME_EXTENSIONS -> ExtensionList(
                        presentation.extensions(SourceContentKind.ANIME),
                        "",
                    )
                    BrowseSection.MANGA_EXTENSIONS -> ExtensionList(
                        presentation.extensions(SourceContentKind.MANGA),
                        "",
                    )
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
    togglePinned: (SourceId) -> Unit,
    open: (SourceDescriptor, SourceListing) -> Unit,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open(source, SourceListing.POPULAR) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    if (supportsLatest(source.id())) {
                        TextButton(onClick = { open(source, SourceListing.LATEST) }) {
                            Text("Latest")
                        }
                    }
                    IconButton(onClick = { togglePinned(source.id()) }) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (source.id() in pinnedSources) "Unpin source" else "Pin source",
                            tint = if (source.id() in pinnedSources) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
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
        title = { Text("Source languages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                available.forEach { language ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = language in enabled,
                            onCheckedChange = { selected -> toggle(language, selected) },
                        )
                        Text(languageName(language))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
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
    openWebPage: (SourceWebPage) -> Unit,
    navigateUp: () -> Unit,
) {
    var query by remember(source.id()) { mutableStateOf("") }
    var searchActive by remember(source.id()) { mutableStateOf(false) }
    var page by remember(source.id(), listing) { mutableIntStateOf(1) }
    var grid by remember(source.id()) { mutableStateOf(true) }
    var showFilters by remember(source.id()) { mutableStateOf(false) }
    var showPreferences by remember(source.id()) { mutableStateOf(false) }
    var filterValues by remember(source.id()) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var preferenceRevision by remember(source.id()) { mutableIntStateOf(0) }
    var notice by remember(source.id()) { mutableStateOf<String?>(null) }
    val definitions = remember(source.id()) { presentation.filters(source.id()) }
    val preferenceDefinitions = remember(source.id(), preferenceRevision) {
        presentation.preferences(source.id())
    }
    val sourceWebPage = remember(source.id()) { presentation.sourceWebPage(source.id()).orElse(null) }
    val result = remember(source.id(), listing, query, page, filterValues, preferenceRevision) {
        runCatching {
            val filters = filterValues.map { SourceFilterValue(it.key, it.value) }
            if (query.isBlank()) {
                presentation.browse(source.id(), listing, page, 30, filters)
            } else {
                presentation.search(source.id(), query, page, 30, filters)
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
                            placeholder = { Text("Search in ${source.displayName()}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(source.displayName())
                            Text(
                                if (listing == SourceListing.POPULAR) "Popular" else "Latest",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sourceWebPage != null) {
                        IconButton(onClick = { openWebPage(sourceWebPage) }) {
                            Icon(Icons.Default.Public, contentDescription = "Open source website")
                        }
                    }
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = { grid = !grid }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Display mode",
                        )
                    }
                    if (definitions.isNotEmpty()) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.Tune, contentDescription = "Filters")
                        }
                    }
                    if (preferenceDefinitions.isNotEmpty()) {
                        IconButton(onClick = { showPreferences = !showPreferences }) {
                            Icon(Icons.Default.Settings, contentDescription = "Source settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            val sourcePage = result.getOrNull()
            if (sourcePage == null) {
                EmptyDiscovery(result.exceptionOrNull()?.message ?: "Unable to load this source")
            } else {
                CatalogueContent(
                    page = sourcePage,
                    grid = grid,
                    add = { item ->
                        presentation.addToLibrary(item)
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

@Composable
private fun ColumnScope.CatalogueContent(
    page: SourcePage,
    grid: Boolean,
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(page.items(), key = { it.id().toString() }) { item ->
                CatalogueCard(item, add, webPage(item), openWebPage)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(page.items(), key = { it.id().toString() }) { item ->
                CatalogueRow(item, add, webPage(item), openWebPage)
            }
        }
    }
}

@Composable
private fun CatalogueCard(
    item: SourceCatalogueItem,
    add: (SourceCatalogueItem) -> Unit,
    webPage: SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(item.title(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                item.description(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { add(item) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Library")
            }
            if (webPage != null) {
                TextButton(onClick = { openWebPage(webPage) }) {
                    Icon(Icons.Default.Public, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("WebView")
                }
            }
        }
    }
}

@Composable
private fun CatalogueRow(
    item: SourceCatalogueItem,
    add: (SourceCatalogueItem) -> Unit,
    webPage: SourceWebPage?,
    openWebPage: (SourceWebPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title(), fontWeight = FontWeight.Medium)
            Text(item.description(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { add(item) }) {
            Icon(Icons.Default.Add, contentDescription = "Add to Library")
        }
        if (webPage != null) {
            IconButton(onClick = { openWebPage(webPage) }) {
                Icon(Icons.Default.Public, contentDescription = "Open title in WebView")
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun Pagination(page: Int, hasNext: Boolean, select: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { select(page - 1) }, enabled = page > 1) { Text("Previous") }
        Text("Page $page", modifier = Modifier.padding(horizontal = 16.dp))
        TextButton(onClick = { select(page + 1) }, enabled = hasNext) { Text("Next") }
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
        Text("Filters", fontWeight = FontWeight.SemiBold)
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
        TextButton(onClick = { update(emptyMap()) }) { Text("Reset") }
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
        Text("Source settings", fontWeight = FontWeight.SemiBold)
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
    val result = remember(kind, query) { runCatching { presentation.globalSearch(kind, query, 10) } }
    val sourceNames = remember(kind) {
        presentation.sourceSections(kind)
            .flatMap { it.sources() }
            .associate { it.id() to it.displayName() }
    }
    val sources = result.getOrNull()
    if (sources == null) {
        EmptyDiscovery(result.exceptionOrNull()?.message ?: "Global search failed")
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
private fun ExtensionList(extensions: List<InstalledSourceExtension>, query: String) {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visible = extensions.filter { extension ->
        normalizedQuery.isEmpty()
                || extension.manifest().component().displayName().lowercase(Locale.ROOT).contains(normalizedQuery)
                || extension.source().displayName().lowercase(Locale.ROOT).contains(normalizedQuery)
    }
    var expanded by remember(extensions) { mutableStateOf<Set<SourceId>>(emptySet()) }
    if (visible.isEmpty()) {
        EmptyDiscovery(
            if (query.isBlank()) {
                "No extensions installed in this product configuration."
            } else {
                "No extensions match your search."
            },
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Installed",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        items(visible, key = { it.source().id().toString() }) { extension ->
            val source = extension.source()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = if (source.id() in expanded) {
                            expanded - source.id()
                        } else {
                            expanded + source.id()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceBadge(source)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(extension.manifest().component().displayName(), fontWeight = FontWeight.Medium)
                    Text(
                        "${languageName(source.languageTag())} - v${source.extensionVersion()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Installed", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            if (source.id() in expanded) {
                ExtensionDetails(extension)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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
                "Bundle ${manifest.component().id()} - ${manifest.component().version()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Permissions", fontWeight = FontWeight.Medium)
            if (manifest.permissions().isEmpty()) {
                Text("No sensitive permissions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (SourcePermission.NETWORK in manifest.permissions()) {
                    Text("Network", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    manifest.networkOrigins().sorted().forEach { origin ->
                        Text("  $origin", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (SourcePermission.CLEARTEXT_NETWORK in manifest.permissions()) {
                    Text("Cleartext network", color = MaterialTheme.colorScheme.error)
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
    var selected by remember(kind) { mutableStateOf<LibraryCard?>(null) }
    var selectedTarget by remember(kind) { mutableStateOf<SourceDescriptor?>(null) }
    var candidates by remember(kind) { mutableStateOf<List<SourceCatalogueItem>>(emptyList()) }
    var message by remember(kind) { mutableStateOf<String?>(null) }
    if (titles.isEmpty()) {
        EmptyDiscovery("Add ${kind.name.lowercase(Locale.ROOT)} titles to your Library before migrating.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        message?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
        }
        items(titles, key = { it.id().value() }) { title ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(title.title(), fontWeight = FontWeight.Medium)
                TextButton(
                    enabled = targets.isNotEmpty(),
                    onClick = {
                        selected = title
                        selectedTarget = null
                        candidates = emptyList()
                    },
                ) {
                    Text(if (targets.isEmpty()) "No target source" else "Select target source")
                }
            }
            if (selected?.id() == title.id()) {
                targets.forEach { target ->
                    TextButton(
                        onClick = {
                            selectedTarget = target
                            candidates = presentation.migrationCandidates(title.id(), target.id(), 20)
                        },
                        modifier = Modifier.padding(start = 28.dp),
                    ) {
                        Text(
                            if (selectedTarget?.id() == target.id()) {
                                "${target.displayName()} (selected)"
                            } else {
                                target.displayName()
                            },
                        )
                    }
                }
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(candidate.title(), modifier = Modifier.weight(1f))
                        Button(onClick = {
                            presentation.migrate(title.id(), candidate)
                            message = "${title.title()} migrated to ${candidate.title()}"
                            selected = null
                            selectedTarget = null
                            candidates = emptyList()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text("Migrate")
                        }
                    }
                }
            }
            HorizontalDivider()
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

private fun BrowseSection.searchable(): Boolean = sourceTab() || extensionTab()
