package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vriege.anilib.feature.library.LibraryProgress
import fr.vriege.anilib.feature.library.LibraryDisplayDensity
import fr.vriege.anilib.feature.library.LibraryDisplayMode
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.LibrarySort
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.backup.ui.BackupPresentation
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.downloads.DownloadStatus
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.library.ui.LibraryCard
import fr.vriege.anilib.feature.library.ui.LibraryDetails
import fr.vriege.anilib.feature.library.ui.LibraryHistoryRow
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
import fr.vriege.anilib.feature.library.ui.LibraryOverview
import fr.vriege.anilib.feature.library.ui.LibraryPage
import fr.vriege.anilib.feature.library.ui.LibraryPresentation
import fr.vriege.anilib.feature.network.NetworkMaintenance
import fr.vriege.anilib.feature.settings.SettingsSnapshot
import fr.vriege.anilib.feature.settings.StartScreen
import fr.vriege.anilib.feature.settings.ThemeMode
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.updates.ui.UpdatePresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import fr.vriege.anilib.framework.http.HttpCookieJar
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

private val dateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

@Composable
fun AnilibApp(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    networkMaintenance: NetworkMaintenance,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    browserDataController: BrowserDataController,
    settingsPresentation: SettingsPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    backup: BackupPresentation,
    backupImportPicker: BackupImportPicker,
    tracking: TrackerPresentation,
    updates: UpdatePresentation,
    applicationUpdates: ApplicationUpdatePresentation,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    componentCount: Int,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val navigator = remember { LibraryNavigator() }
    val initialSettings = remember(settingsPresentation) { settingsPresentation.snapshot() }
    var destination by remember { mutableStateOf(navigator.state()) }
    var section by remember { mutableStateOf(initialSettings.startScreen().appSection()) }
    var activeReader by remember { mutableStateOf<ReaderController?>(null) }
    var activePlayerTitle by remember { mutableStateOf<LibraryItemId?>(null) }
    var activeTrackingTitle by remember { mutableStateOf<LibraryItemId?>(null) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var moreDestination by remember { mutableStateOf<MoreDestination?>(null) }
    var settings by remember(settingsPresentation) { mutableStateOf(initialSettings) }
    DisposableEffect(settingsPresentation) {
        val observation = settingsPresentation.observe { settings = it }
        onDispose { observation.close() }
    }
    val navigate: ((LibraryNavigator) -> Unit) -> Unit = { transition ->
        transition(navigator)
        destination = navigator.state()
    }
    val openSection: (AppSection) -> Unit = { next ->
        section = next
        if (next != AppSection.MORE) moreDestination = null
    }

    val useDarkTheme = when (settings.themeMode()) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val controller = activeReader
            val playerTitle = activePlayerTitle
            val trackingTitle = activeTrackingTitle
            if (controller != null) {
                DisposableEffect(controller) {
                    onDispose { controller.close() }
                }
                ReaderScreen(controller, pageDecoder) { activeReader = null }
            } else if (playerTitle != null) {
                EpisodeScreen(player, playerTitle) { activePlayerTitle = null }
            } else if (trackingTitle != null) {
                val details = presentation.details(trackingTitle).orElse(null)
                if (details == null) {
                    activeTrackingTitle = null
                } else {
                    TitleTrackingScreen(
                        presentation = tracking,
                        itemId = trackingTitle,
                        title = details.title(),
                        kind = details.kind(),
                        goBack = { activeTrackingTitle = null },
                    )
                }
            } else {
                val openReader: (LibraryItemId) -> Unit = { id ->
                    runCatching { reader.open(id) }
                        .onSuccess {
                            readerError = null
                            activeReader = it
                        }
                        .onFailure { readerError = it.message ?: "The reader could not be opened." }
                }
                val enqueueDownload: (LibraryItemId) -> Unit = { id ->
                    runCatching { downloads.enqueue(id) }
                        .onSuccess { downloadError = null }
                        .onFailure { downloadError = it.message ?: "The download could not be queued." }
                }
                val openPlayer: (LibraryItemId) -> Unit = { id ->
                    runCatching {
                        check(player.canOpen(id)) { "This title no longer has a streaming source." }
                    }
                        .onSuccess {
                            playerError = null
                            activePlayerTitle = id
                        }
                        .onFailure { playerError = it.message ?: "The episode list could not be opened." }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= 720.dp) {
                        ExpandedShell(
                            presentation,
                            discovery,
                            extensionRepositories,
                            apkExtensionPlatform,
                            networkMaintenance,
                            browserCookies,
                            browserRuntimeStatus,
                            browserDataController,
                            settingsPresentation,
                            settings,
                            reader,
                            player,
                            downloads,
                            backup,
                            backupImportPicker,
                            tracking,
                            updates,
                            applicationUpdates,
                            destination,
                            section,
                            componentCount,
                            navigate,
                            openSection,
                            openReader,
                            readerError,
                            openPlayer,
                            playerError,
                            enqueueDownload,
                            downloadError,
                            { activeTrackingTitle = it },
                            moreDestination,
                            { moreDestination = it },
                            { moreDestination = null },
                        )
                    } else {
                        CompactShell(
                            presentation,
                            discovery,
                            extensionRepositories,
                            apkExtensionPlatform,
                            networkMaintenance,
                            browserCookies,
                            browserRuntimeStatus,
                            browserDataController,
                            settingsPresentation,
                            settings,
                            reader,
                            player,
                            downloads,
                            backup,
                            backupImportPicker,
                            tracking,
                            updates,
                            applicationUpdates,
                            destination,
                            section,
                            componentCount,
                            navigate,
                            openSection,
                            openReader,
                            readerError,
                            openPlayer,
                            playerError,
                            enqueueDownload,
                            downloadError,
                            { activeTrackingTitle = it },
                            moreDestination,
                            { moreDestination = it },
                            { moreDestination = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedShell(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    networkMaintenance: NetworkMaintenance,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    browserDataController: BrowserDataController,
    settingsPresentation: SettingsPresentation,
    settings: SettingsSnapshot,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    backup: BackupPresentation,
    backupImportPicker: BackupImportPicker,
    tracking: TrackerPresentation,
    updates: UpdatePresentation,
    applicationUpdates: ApplicationUpdatePresentation,
    destination: LibraryNavigationState,
    section: AppSection,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId) -> Unit,
    playerError: String?,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnilibNavigationRail(section, openSection)
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AppDestination(
                presentation,
                discovery,
                extensionRepositories,
                apkExtensionPlatform,
                networkMaintenance,
                browserCookies,
                browserRuntimeStatus,
                browserDataController,
                settingsPresentation,
                settings,
                reader,
                player,
                downloads,
                backup,
                backupImportPicker,
                tracking,
                updates,
                applicationUpdates,
                destination,
                section,
                componentCount,
                navigate,
                openSection,
                openReader,
                readerError,
                openPlayer,
                playerError,
                enqueueDownload,
                downloadError,
                openTracking,
                moreDestination,
                openMore,
                closeMore,
            )
        }
    }
}

@Composable
private fun CompactShell(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    networkMaintenance: NetworkMaintenance,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    browserDataController: BrowserDataController,
    settingsPresentation: SettingsPresentation,
    settings: SettingsSnapshot,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    backup: BackupPresentation,
    backupImportPicker: BackupImportPicker,
    tracking: TrackerPresentation,
    updates: UpdatePresentation,
    applicationUpdates: ApplicationUpdatePresentation,
    destination: LibraryNavigationState,
    section: AppSection,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId) -> Unit,
    playerError: String?,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AppDestination(
                presentation,
                discovery,
                extensionRepositories,
                apkExtensionPlatform,
                networkMaintenance,
                browserCookies,
                browserRuntimeStatus,
                browserDataController,
                settingsPresentation,
                settings,
                reader,
                player,
                downloads,
                backup,
                backupImportPicker,
                tracking,
                updates,
                applicationUpdates,
                destination,
                section,
                componentCount,
                navigate,
                openSection,
                openReader,
                readerError,
                openPlayer,
                playerError,
                enqueueDownload,
                downloadError,
                openTracking,
                moreDestination,
                openMore,
                closeMore,
            )
        }
        HorizontalDivider()
        AnilibNavigationBar(section, openSection)
    }
}

@Composable
private fun AnilibNavigationRail(
    section: AppSection,
    openSection: (AppSection) -> Unit,
) {
    NavigationRail(header = {
        Text(
            text = "Anilib",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 22.dp),
        )
    }) {
        AppSection.entries.forEach { item ->
            NavigationRailItem(
                selected = section == item,
                onClick = { openSection(item) },
                icon = { Icon(item.icon(), contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun AnilibNavigationBar(
    section: AppSection,
    openSection: (AppSection) -> Unit,
) {
    NavigationBar {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = section == item,
                onClick = { openSection(item) },
                icon = { Icon(item.icon(), contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun AppDestination(
    presentation: LibraryPresentation,
    discovery: DiscoveryPresentation,
    extensionRepositories: ExtensionRepositoryPresentation,
    apkExtensionPlatform: ApkExtensionPlatform,
    networkMaintenance: NetworkMaintenance,
    browserCookies: HttpCookieJar,
    browserRuntimeStatus: BrowserRuntimeStatus,
    browserDataController: BrowserDataController,
    settingsPresentation: SettingsPresentation,
    settings: SettingsSnapshot,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    backup: BackupPresentation,
    backupImportPicker: BackupImportPicker,
    tracking: TrackerPresentation,
    updates: UpdatePresentation,
    applicationUpdates: ApplicationUpdatePresentation,
    destination: LibraryNavigationState,
    section: AppSection,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId) -> Unit,
    playerError: String?,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
) {
    when (section) {
        AppSection.LIBRARY -> when (destination.page()) {
            LibraryPage.DETAILS -> DetailsDestination(
                presentation,
                reader,
                player,
                downloads,
                tracking,
                destination,
                navigate,
                openReader,
                readerError,
                openPlayer,
                playerError,
                enqueueDownload,
                downloadError,
                openTracking,
            )
            else -> LibraryPageContent(presentation, componentCount, navigate)
        }
        AppSection.UPDATES -> UpdatesScreen(updates)
        AppSection.HISTORY -> HistoryPage(presentation) { transition ->
            navigate(transition)
            openSection(AppSection.LIBRARY)
        }
        AppSection.BROWSE -> DiscoveryScreen(
            discovery,
            presentation,
            browserCookies,
            browserRuntimeStatus,
            manageExtensions = {
                openSection(AppSection.MORE)
                openMore(MoreDestination.EXTENSION_REPOSITORIES)
            },
        )
        AppSection.MORE -> when (moreDestination) {
            MoreDestination.DOWNLOADS -> DownloadsScreen(downloads, closeMore)
            MoreDestination.BACKUP -> BackupScreen(backup, backupImportPicker, closeMore)
            MoreDestination.TRACKING -> TrackerAccountsScreen(tracking, closeMore)
            MoreDestination.CATEGORIES -> CategoriesScreen(presentation, closeMore)
            MoreDestination.STATISTICS -> StatisticsScreen(presentation.library(), closeMore)
            MoreDestination.EXTENSION_REPOSITORIES -> ExtensionRepositoriesScreen(
                extensionRepositories,
                apkExtensionPlatform,
                closeMore,
            )
            MoreDestination.SETTINGS -> SettingsScreen(
                settingsPresentation,
                settings,
                networkMaintenance,
                browserDataController,
                { openMore(MoreDestination.EXTENSION_REPOSITORIES) },
                { openMore(MoreDestination.TRACKING) },
                { openMore(MoreDestination.BACKUP) },
                { openMore(MoreDestination.DOWNLOADS) },
                { openMore(MoreDestination.ABOUT) },
                closeMore,
            )
            MoreDestination.ABOUT -> AboutScreen(componentCount, applicationUpdates, closeMore)
            null -> MorePage(
                componentCount,
                settings,
                settingsPresentation::setIncognitoMode,
                downloads,
                { openMore(MoreDestination.DOWNLOADS) },
                { openMore(MoreDestination.BACKUP) },
                { openMore(MoreDestination.TRACKING) },
                { openMore(MoreDestination.CATEGORIES) },
                { openMore(MoreDestination.STATISTICS) },
                { openMore(MoreDestination.EXTENSION_REPOSITORIES) },
                { openMore(MoreDestination.SETTINGS) },
                { openMore(MoreDestination.ABOUT) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPageContent(
    presentation: LibraryPresentation,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<MediaKind?>(null) }
    var category by remember(presentation) {
        mutableStateOf(overview.displayPreferences().defaultCategory().orElse(null))
    }
    var favoritesOnly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(action: () -> Unit) {
        try {
            action()
            error = null
            revision++
        } catch (failure: RuntimeException) {
            error = failure.message ?: "Unable to update the library display."
        }
    }
    val titles = overview.titles()
        .asSequence()
        .filter { query.isBlank() || it.title().contains(query, ignoreCase = true) }
        .filter { kind == null || it.kind() == kind }
        .filter { !favoritesOnly || it.favorite() }
        .filter {
            when (category) {
                null -> true
                "" -> it.categories().isEmpty()
                else -> category in it.categories()
            }
        }
        .toList()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        update {
                            presentation.setDisplayMode(
                                if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                                    LibraryDisplayMode.LIST
                                } else {
                                    LibraryDisplayMode.GRID
                                },
                            )
                        }
                    }) {
                        Text(if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) "Grid" else "List")
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        update {
                            presentation.setDisplayDensity(overview.displayPreferences().density().next())
                        }
                    }) {
                        Text(overview.displayPreferences().density().label())
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        update { presentation.setSort(overview.displayPreferences().sort().next()) }
                    }) {
                        Text(overview.displayPreferences().sort().label())
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Text(librarySummary(overview), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search your library") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                FilterChip(
                    selected = kind == MediaKind.ANIME,
                    onClick = { kind = MediaKind.ANIME },
                    label = { Text("Anime") },
                )
                FilterChip(
                    selected = kind == MediaKind.MANGA,
                    onClick = { kind = MediaKind.MANGA },
                    label = { Text("Manga") },
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Favorites") },
                )
            }
            if (overview.categories().isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = category == null,
                        onClick = {
                            category = null
                            update { presentation.setDefaultCategory(java.util.Optional.empty()) }
                        },
                        label = { Text("All categories") },
                    )
                    FilterChip(
                        selected = category == "",
                        onClick = {
                            category = ""
                            update { presentation.setDefaultCategory(java.util.Optional.empty()) }
                        },
                        label = { Text("Default") },
                    )
                    overview.categories().forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = {
                                category = value
                                update { presentation.setDefaultCategory(java.util.Optional.of(value)) }
                            },
                            label = { Text(value) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (overview.titles().isEmpty()) {
                EmptyPage("Your library is empty. Add local content to begin.")
            } else if (titles.isEmpty()) {
                EmptyPage("No titles match the active library filters.")
            } else {
                Text(
                    text = "$componentCount feature bundles active",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(overview.displayPreferences().density().minimumCardWidth()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(titles, key = { it.id().value() }) { card ->
                            LibraryTitleCard(card) { navigate { it.openDetails(card.id()) } }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(titles, key = { it.id().value() }) { card ->
                            LibraryTitleCard(card) { navigate { it.openDetails(card.id()) } }
                        }
                    }
                }
            }
        }
    }
}

private fun LibraryDisplayDensity.next(): LibraryDisplayDensity = when (this) {
    LibraryDisplayDensity.COMPACT -> LibraryDisplayDensity.COMFORTABLE
    LibraryDisplayDensity.COMFORTABLE -> LibraryDisplayDensity.RELAXED
    LibraryDisplayDensity.RELAXED -> LibraryDisplayDensity.COMPACT
}

private fun LibraryDisplayDensity.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun LibraryDisplayDensity.minimumCardWidth() = when (this) {
    LibraryDisplayDensity.COMPACT -> 140.dp
    LibraryDisplayDensity.COMFORTABLE -> 180.dp
    LibraryDisplayDensity.RELAXED -> 240.dp
}

private fun LibrarySort.next(): LibrarySort = when (this) {
    LibrarySort.TITLE_ASCENDING -> LibrarySort.TITLE_DESCENDING
    LibrarySort.TITLE_DESCENDING -> LibrarySort.ADDED_NEWEST
    LibrarySort.ADDED_NEWEST -> LibrarySort.ADDED_OLDEST
    LibrarySort.ADDED_OLDEST -> LibrarySort.TITLE_ASCENDING
}

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.TITLE_ASCENDING -> "A-Z"
    LibrarySort.TITLE_DESCENDING -> "Z-A"
    LibrarySort.ADDED_NEWEST -> "Newest"
    LibrarySort.ADDED_OLDEST -> "Oldest"
}

@Composable
private fun LibraryTitleCard(card: LibraryCard, openDetails: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cardMetadata(card),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.favorite()) {
                Text(
                    text = "Favorite",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryPage(
    presentation: LibraryPresentation,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    val history = presentation.history()
    var query by remember { mutableStateOf("") }
    val entries = history.entries().filter {
        query.isBlank() || it.title().contains(query, ignoreCase = true) ||
            it.contentId().contains(query, ignoreCase = true)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Text(
                text = "${history.entries().size} recent entries",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search history") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (history.entries().isEmpty()) {
                EmptyPage("Titles you open will appear here.")
            } else if (entries.isEmpty()) {
                EmptyPage("No history entries match your search.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries) { row ->
                        HistoryCard(row) { navigate { it.openDetails(row.libraryItemId()) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(row: LibraryHistoryRow, openDetails: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(row.title(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${row.contentId()} | Position ${row.position()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dateTimeFormatter.format(row.openedAt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsDestination(
    presentation: LibraryPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    tracking: TrackerPresentation,
    destination: LibraryNavigationState,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openReader: (LibraryItemId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId) -> Unit,
    playerError: String?,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
) {
    val id = destination.selectedTitle().orElse(null)
    val details = id?.let { presentation.details(it).orElse(null) }
    if (details == null) {
        MissingDetails { navigate(LibraryNavigator::openLibrary) }
    } else {
        DetailsPage(
            details = details,
            canRead = runCatching { reader.canOpen(details.id()) }.getOrDefault(false),
            canWatch = runCatching { player.canOpen(details.id()) }.getOrDefault(false),
            canDownload = runCatching { downloads.canEnqueue(details.id()) }.getOrDefault(false),
            canTrack = tracking.accounts().any { it.descriptor().supportedKinds().contains(details.kind()) },
            readerError = readerError,
            playerError = playerError,
            downloadError = downloadError,
            read = { openReader(details.id()) },
            watch = { openPlayer(details.id()) },
            download = { enqueueDownload(details.id()) },
            track = { openTracking(details.id()) },
            goBack = { navigate(LibraryNavigator::back) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsPage(
    details: LibraryDetails,
    canRead: Boolean,
    canWatch: Boolean,
    canDownload: Boolean,
    canTrack: Boolean,
    readerError: String?,
    playerError: String?,
    downloadError: String?,
    read: () -> Unit,
    watch: () -> Unit,
    download: () -> Unit,
    track: () -> Unit,
    goBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(formatEnum(details.kind()), color = MaterialTheme.colorScheme.primary) }
            item { DetailsFacts(details) }
            item {
                Text("Description", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    details.description().ifBlank { "No description available." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readerError.isNullOrBlank()) {
                item { Text(readerError, color = MaterialTheme.colorScheme.error) }
            }
            if (!playerError.isNullOrBlank()) {
                item { Text(playerError, color = MaterialTheme.colorScheme.error) }
            }
            if (!downloadError.isNullOrBlank()) {
                item { Text(downloadError, color = MaterialTheme.colorScheme.error) }
            }
            if (canTrack) {
                item { Button(onClick = track) { Text("Tracking") } }
            }
            item {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (canWatch) {
                        Button(onClick = watch) { Text("Watch") }
                    }
                    if (canRead) {
                        Button(onClick = read) { Text("Read") }
                    }
                    Button(onClick = download, enabled = canDownload) {
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsFacts(details: LibraryDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Fact("Favorite", if (details.favorite()) "Yes" else "No")
            Fact("Status", formatEnum(details.publicationStatus()))
            Fact("Added", dateTimeFormatter.format(details.addedAt()))
            Fact("Categories", joined(details.categories()))
            Fact("Authors", joined(details.authors()))
            Fact("Artists", joined(details.artists()))
            Fact("Progress", details.progress().map(::progress).orElse("Not started"))
            Fact("History", "${details.historyEntryCount()} entries")
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.width(116.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyPage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderPage(title: String, message: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorePage(
    componentCount: Int,
    settings: SettingsSnapshot,
    setIncognitoMode: (Boolean) -> Unit,
    downloads: DownloadPresentation,
    openDownloads: () -> Unit,
    openBackup: () -> Unit,
    openTracking: () -> Unit,
    openCategories: () -> Unit,
    openStatistics: () -> Unit,
    openExtensionRepositories: () -> Unit,
    openSettings: () -> Unit,
    openAbout: () -> Unit,
) {
    var queue by remember(downloads) { mutableStateOf(downloads.queue()) }
    DisposableEffect(downloads) {
        val observation = downloads.observe { queue = downloads.queue() }
        onDispose { observation.close() }
    }
    val pendingDownloads = queue.jobs().count {
        it.status() != DownloadStatus.COMPLETED && it.status() != DownloadStatus.CANCELLED
    }
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                MoreSwitchRow(
                    "Downloaded only",
                    "Use downloaded content without the online fallback",
                    queue.offlineMode(),
                    downloads::setOfflineMode,
                )
            }
            item {
                MoreSwitchRow(
                    "Incognito mode",
                    "Pause reading and watching history",
                    settings.incognitoMode(),
                    setIncognitoMode,
                )
            }
            item { HorizontalDivider() }
            item {
                MoreRow(
                    "Download queue",
                    if (pendingDownloads == 0) "No pending downloads" else "$pendingDownloads pending downloads",
                    openDownloads,
                )
            }
            item { MoreRow("Categories", "Organize anime and manga in your library", openCategories) }
            item { MoreRow("Statistics", "Library and reading activity", openStatistics) }
            item { MoreRow("Backup and restore", "Create or restore a local backup", openBackup) }
            item { MoreRow("Tracking", "Manage external tracking accounts", openTracking) }
            item {
                MoreRow(
                    "Extension repositories",
                    "Bring your own Aniyomi-compatible repository URLs",
                    openExtensionRepositories,
                )
            }
            item { HorizontalDivider() }
            item { MoreRow("Settings", "Appearance, library, reader, player, and tracking", openSettings) }
            item { MoreRow("About", "$componentCount feature bundles active", openAbout) }
        }
    }
}

@Composable
private fun MoreSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MoreRow(title: String, summary: String, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissingDetails(openLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This title is no longer in the library.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = openLibrary) { Text("Back to library") }
        }
    }
}

private fun librarySummary(overview: LibraryOverview): String =
    "${overview.titles().size} titles | ${overview.favoriteCount()} favorites | " +
        "${overview.categories().size} categories"

private fun cardMetadata(card: LibraryCard): String {
    val categoryText = joined(card.categories())
    val progressText = card.progress().map(::progress).orElse("Not started")
    return "${formatEnum(card.kind())} | $categoryText | $progressText"
}

private fun progress(progress: LibraryProgress): String {
    if (progress.extent() == LibraryProgress.UNKNOWN_EXTENT) {
        return progress.position().toString()
    }
    val percentage = (progress.completion().orElse(0.0) * 100.0).roundToInt()
    return "${progress.position()} / ${progress.extent()} ($percentage%)"
}

private fun joined(values: List<String>): String =
    if (values.isEmpty()) "None" else values.joinToString(", ")

private fun formatEnum(value: Enum<*>): String = value.name
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .replaceFirstChar(Char::uppercase)

private enum class AppSection(val label: String) {
    LIBRARY("Library"),
    UPDATES("Updates"),
    HISTORY("History"),
    BROWSE("Browse"),
    MORE("More"),
}

private fun StartScreen.appSection(): AppSection = when (this) {
    StartScreen.LIBRARY -> AppSection.LIBRARY
    StartScreen.UPDATES -> AppSection.UPDATES
    StartScreen.HISTORY -> AppSection.HISTORY
    StartScreen.BROWSE -> AppSection.BROWSE
    StartScreen.MORE -> AppSection.MORE
}

private enum class MoreDestination {
    DOWNLOADS,
    BACKUP,
    TRACKING,
    CATEGORIES,
    STATISTICS,
    EXTENSION_REPOSITORIES,
    SETTINGS,
    ABOUT,
}

private fun AppSection.icon(): ImageVector = when (this) {
    AppSection.LIBRARY -> Icons.Default.CollectionsBookmark
    AppSection.UPDATES -> Icons.Default.NewReleases
    AppSection.HISTORY -> Icons.Default.History
    AppSection.BROWSE -> Icons.Default.Explore
    AppSection.MORE -> Icons.Default.MoreHoriz
}
