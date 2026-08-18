package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vriege.anilib.feature.library.LibraryProgress
import fr.vriege.anilib.feature.library.LibraryTitleMetadata
import fr.vriege.anilib.feature.library.LibraryDisplayDensity
import fr.vriege.anilib.feature.library.LibraryDisplayMode
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.LibrarySort
import fr.vriege.anilib.feature.library.PublicationStatus
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
import fr.vriege.anilib.feature.settings.AccentColor
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.settings.NavigationStyle
import fr.vriege.anilib.feature.settings.StartScreen
import fr.vriege.anilib.feature.settings.ThemeFamily
import fr.vriege.anilib.feature.settings.ThemeMode
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.player.PlayerOrientationPolicy
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.updates.ui.UpdatePresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import fr.vriege.anilib.framework.http.HttpCookieJar
import fr.vriege.anilib.framework.http.AnilibHttpClient
import fr.vriege.anilib.framework.http.HttpCachePolicy
import fr.vriege.anilib.framework.http.HttpRequest
import fr.vriege.anilib.feature.player.EpisodeSnapshot
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val dateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private data class DetailPlatform(
    val httpClient: AnilibHttpClient,
    val shareController: ShareController,
    val pageDecoder: (ByteArray) -> ImageBitmap?,
)

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
    browserPlatformController: BrowserPlatformController,
    settingsPresentation: SettingsPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    downloads: DownloadPresentation,
    backup: BackupPresentation,
    backupImportPicker: BackupImportPicker,
    tracking: TrackerPresentation,
    updates: UpdatePresentation,
    applicationUpdates: ApplicationUpdatePresentation,
    applicationUpdatePlatformController: ApplicationUpdatePlatformController,
    httpClient: AnilibHttpClient,
    shareController: ShareController,
    pageDecoder: (ByteArray) -> ImageBitmap?,
    applyReaderOrientationPolicy: (ReaderOrientationPolicy) -> Unit,
    applyPlayerOrientationPolicy: (PlayerOrientationPolicy) -> Unit,
    requestPlayerPictureInPicture: () -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    setPlayerBackgroundAudio: (Boolean) -> Unit,
    enableAndroidPlayerControls: Boolean,
    enableDesktopPlayerControls: Boolean,
    componentCount: Int,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val detailPlatform = remember(httpClient, shareController, pageDecoder) {
        DetailPlatform(httpClient, shareController, pageDecoder)
    }
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
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            currentDensity.density,
            currentDensity.fontScale * settings.typographyScale().multiplier(),
        ),
        LocalBrowserPolicy provides settings.browserPolicy(),
        LocalBrowserPlatformController provides browserPlatformController,
        LocalApplicationUpdatePlatformController provides applicationUpdatePlatformController,
        LocalReducedMotion provides settings.reducedMotion(),
    ) {
        MaterialTheme(colorScheme = appColorScheme(settings, useDarkTheme)) {
            Surface(modifier = Modifier.fillMaxSize()) {
            val controller = activeReader
            val playerTitle = activePlayerTitle
            val trackingTitle = activeTrackingTitle
            if (controller != null) {
                DisposableEffect(controller) {
                    onDispose { controller.close() }
                }
                ReaderScreen(
                    controller,
                    pageDecoder,
                    applyReaderOrientationPolicy,
                    downloads::enqueue,
                ) { activeReader = null }
            } else if (playerTitle != null) {
                EpisodeScreen(
                    player,
                    playerTitle,
                    applyPlayerOrientationPolicy,
                    requestPlayerPictureInPicture,
                    setPlayerActive,
                    setPlayerBackgroundAudio,
                    enableAndroidPlayerControls,
                    enableDesktopPlayerControls,
                ) {
                    activePlayerTitle = null
                }
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
                    val useNavigationRail = when (settings.navigationStyle()) {
                        NavigationStyle.ADAPTIVE -> maxWidth >= 720.dp
                        NavigationStyle.BOTTOM_BAR -> false
                        NavigationStyle.NAVIGATION_RAIL -> true
                    }
                    if (useNavigationRail) {
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
                            detailPlatform,
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
                            detailPlatform,
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
    detailPlatform: DetailPlatform,
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
        AnilibNavigationRail(section, settings.languagePack(), openSection)
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
                detailPlatform,
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
    detailPlatform: DetailPlatform,
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
                detailPlatform,
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
        AnilibNavigationBar(section, settings.languagePack(), openSection)
    }
}

@Composable
private fun AnilibNavigationRail(
    section: AppSection,
    settingsLanguage: LanguagePack,
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
                label = { Text(item.label(settingsLanguage)) },
            )
        }
    }
}

@Composable
private fun AnilibNavigationBar(
    section: AppSection,
    settingsLanguage: LanguagePack,
    openSection: (AppSection) -> Unit,
) {
    NavigationBar {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = section == item,
                onClick = { openSection(item) },
                icon = { Icon(item.icon(), contentDescription = null) },
                label = { Text(item.label(settingsLanguage)) },
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
    detailPlatform: DetailPlatform,
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
                discovery,
                browserCookies,
                browserRuntimeStatus,
                detailPlatform,
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
            else -> LibraryPageContent(
                presentation,
                discovery,
                downloads,
                componentCount,
                navigate,
            )
        }
        AppSection.UPDATES -> UpdatesScreen(updates, downloads)
        AppSection.HISTORY -> HistoryPage(
            presentation,
            openReader,
            openPlayer,
            readerError ?: playerError,
        ) { transition ->
            navigate(transition)
            openSection(AppSection.LIBRARY)
        }
        AppSection.BROWSE -> DiscoveryScreen(
            discovery,
            presentation,
            extensionRepositories,
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
            MoreDestination.STATISTICS -> StatisticsScreen(
                presentation,
                discovery,
                player,
                tracking,
                closeMore,
            )
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
                backupImportPicker,
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
    discovery: DiscoveryPresentation,
    downloads: DownloadPresentation,
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
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<LibraryItemId>()) }
    var categoryAction by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var migrating by remember { mutableStateOf(false) }
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
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Text(if (selectionMode) "Done" else "Select")
                    }
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
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${selected.size} selected", fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.TextButton(
                        enabled = titles.isNotEmpty(),
                        onClick = {
                            selected = if (selected.size == titles.size) {
                                emptySet()
                            } else {
                                titles.mapTo(linkedSetOf()) { it.id() }
                            }
                        },
                    ) { Text("All") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { update { presentation.setFavorite(selected, true) } },
                    ) { Text("Favorite") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { update { presentation.setFavorite(selected, false) } },
                    ) { Text("Unfavorite") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty() && overview.categories().isNotEmpty(),
                        onClick = { categoryAction = true },
                    ) { Text("Category") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            selected.forEach { id ->
                                runCatching {
                                    if (downloads.canEnqueue(id)) downloads.enqueue(id)
                                }.onFailure { failure ->
                                    error = failure.message ?: "Unable to enqueue a download."
                                }
                            }
                        },
                    ) { Text("Download") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { migrating = true },
                    ) { Text("Migrate") }
                    androidx.compose.material3.TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { confirmingDelete = true },
                    ) { Text("Delete") }
                }
            }
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
                            LibraryTitleCard(
                                card,
                                card.id() in selected,
                                selectionMode,
                                { selected = selected.toggle(card.id()) },
                                { navigate { it.openDetails(card.id()) } },
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(titles, key = { it.id().value() }) { card ->
                            LibraryTitleCard(
                                card,
                                card.id() in selected,
                                selectionMode,
                                { selected = selected.toggle(card.id()) },
                                { navigate { it.openDetails(card.id()) } },
                            )
                        }
                    }
                }
            }
        }
    }
    if (categoryAction) {
        BulkCategoryDialog(
            categories = overview.categories(),
            dismiss = { categoryAction = false },
            add = { value ->
                update { presentation.addToCategory(selected, value) }
                categoryAction = false
            },
            remove = { value ->
                update { presentation.removeFromCategory(selected, value) }
                categoryAction = false
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${selected.size} titles?") },
            text = { Text("This removes the selected titles from your library.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    update { presentation.deleteTitles(selected) }
                    selected = emptySet()
                    selectionMode = false
                    confirmingDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (migrating) {
        BulkMigrationDialog(
            cards = overview.titles().filter { it.id() in selected },
            discovery = discovery,
            dismiss = { migrating = false },
            completed = {
                migrating = false
                selected = emptySet()
                selectionMode = false
                revision++
            },
        )
    }
}

private fun Set<LibraryItemId>.toggle(id: LibraryItemId): Set<LibraryItemId> =
    if (id in this) this - id else this + id

@Composable
private fun BulkCategoryDialog(
    categories: List<String>,
    dismiss: () -> Unit,
    add: (String) -> Unit,
    remove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Update categories") },
        text = {
            Column {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { add(category) }) {
                            Text("Add")
                        }
                        androidx.compose.material3.TextButton(onClick = { remove(category) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = dismiss) { Text("Close") }
        },
    )
}

@Composable
private fun BulkMigrationDialog(
    cards: List<LibraryCard>,
    discovery: DiscoveryPresentation,
    dismiss: () -> Unit,
    completed: () -> Unit,
) {
    var index by remember(cards) { mutableStateOf(0) }
    var candidates by remember(cards) { mutableStateOf(listOf<SourceCatalogueItem>()) }
    var targetSource by remember(cards) { mutableStateOf<SourceId?>(null) }
    var loading by remember(cards) { mutableStateOf(false) }
    var error by remember(cards) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val current = cards.getOrNull(index)
    if (current == null) {
        completed()
        return
    }
    val contentKind = if (current.kind() == MediaKind.ANIME) {
        SourceContentKind.ANIME
    } else {
        SourceContentKind.MANGA
    }
    val sources = remember(current.id()) {
        discovery.sourceSections(contentKind).flatMap { it.sources() }
    }
    fun selectSource(sourceId: SourceId) {
        targetSource = sourceId
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    discovery.migrationCandidates(current.id(), sourceId, 20)
                }
            }.onSuccess { values ->
                candidates = values
                if (values.isEmpty()) error = "No migration candidates found."
            }.onFailure { failure ->
                error = failure.message ?: "Unable to load migration candidates."
            }
            loading = false
        }
    }
    fun migrate(target: SourceCatalogueItem) {
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { discovery.migrate(current.id(), target) }
            }.onSuccess {
                index++
                targetSource = null
                candidates = emptyList()
                if (index >= cards.size) completed()
            }.onFailure { failure ->
                error = failure.message ?: "Unable to migrate ${current.title()}."
            }
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Migrate ${index + 1} of ${cards.size}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current.title(), fontWeight = FontWeight.SemiBold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) {
                    Text("Loading…")
                } else if (targetSource == null) {
                    Text("Choose a target source")
                    sources.take(12).forEach { source ->
                        androidx.compose.material3.TextButton(onClick = { selectSource(source.id()) }) {
                            Text("${source.displayName()} · ${source.languageTag()}")
                        }
                    }
                } else {
                    androidx.compose.material3.TextButton(onClick = {
                        targetSource = null
                        candidates = emptyList()
                    }) { Text("Change source") }
                    candidates.take(12).forEach { candidate ->
                        androidx.compose.material3.TextButton(onClick = { migrate(candidate) }) {
                            Text(candidate.title())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = dismiss) { Text("Cancel") }
        },
    )
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
private fun LibraryTitleCard(
    card: LibraryCard,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    openDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = if (selectionMode) select else openDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(selected, onCheckedChange = { select() })
            }
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
    openReader: (LibraryItemId) -> Unit,
    openPlayer: (LibraryItemId) -> Unit,
    resumeError: String?,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    val history = remember(revision) { presentation.history() }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<MediaKind?>(null) }
    val entries = history.entries().filter {
        (kind == null || it.kind() == kind) &&
            (query.isBlank() || it.title().contains(query, ignoreCase = true) ||
                it.contentId().contains(query, ignoreCase = true))
    }
    val groups = entries.groupBy { row ->
        row.openedAt().atZone(ZoneId.systemDefault()).toLocalDate()
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
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(kind == null, { kind = null }, label = { Text("All") })
                FilterChip(kind == MediaKind.ANIME, { kind = MediaKind.ANIME }, label = {
                    Text("Anime")
                })
                FilterChip(kind == MediaKind.MANGA, { kind = MediaKind.MANGA }, label = {
                    Text("Manga")
                })
            }
            resumeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            if (history.entries().isEmpty()) {
                EmptyPage("Titles you open will appear here.")
            } else if (entries.isEmpty()) {
                EmptyPage("No history entries match your search.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groups.forEach { (date, rows) ->
                        item(key = "history-date-$date") {
                            Text(
                                historyDateLabel(date),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(
                            rows,
                            key = { row ->
                                "${row.libraryItemId().value()}-${row.contentId()}-${row.openedAt()}"
                            },
                        ) { row ->
                            HistoryCard(
                                row,
                                resume = {
                                    if (row.kind() == MediaKind.ANIME) {
                                        openPlayer(row.libraryItemId())
                                    } else {
                                        openReader(row.libraryItemId())
                                    }
                                },
                                remove = {
                                    presentation.removeHistoryEntry(
                                        row.libraryItemId(),
                                        row.contentId(),
                                        row.openedAt(),
                                    )
                                    revision++
                                },
                                openDetails = {
                                    navigate { it.openDetails(row.libraryItemId()) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    row: LibraryHistoryRow,
    resume: () -> Unit,
    remove: () -> Unit,
    openDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(row.title(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${formatEnum(row.kind())} | ${row.contentId()} | Position ${row.position()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dateTimeFormatter.format(row.openedAt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.TextButton(onClick = resume) {
                    Text(if (row.kind() == MediaKind.ANIME) "Watch" else "Read")
                }
                androidx.compose.material3.TextButton(onClick = remove) { Text("Remove") }
            }
        }
    }
}

private fun historyDateLabel(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date)
    }
}

@Composable
private fun DetailsDestination(
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
    openReader: (LibraryItemId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId) -> Unit,
    playerError: String?,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
) {
    val id = destination.selectedTitle().orElse(null)
    var revision by remember(id) { mutableStateOf(0) }
    var browserPage by remember(id) {
        mutableStateOf<fr.vriege.anilib.feature.source.SourceWebPage?>(null)
    }
    var chapters by remember(id) { mutableStateOf(listOf<SourceContentUnit>()) }
    var episodes by remember(id) { mutableStateOf(listOf<EpisodeSnapshot>()) }
    var unitError by remember(id) { mutableStateOf<String?>(null) }
    val details = remember(id, revision) { id?.let { presentation.details(it).orElse(null) } }
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
        MissingDetails { navigate(LibraryNavigator::openLibrary) }
    } else {
        androidx.compose.runtime.LaunchedEffect(details.id(), revision) {
            unitError = null
            if (details.kind() == MediaKind.ANIME) {
                runCatching { withContext(Dispatchers.IO) { player.episodes(details.id()) } }
                    .onSuccess { episodes = it }
                    .onFailure { unitError = it.message ?: "Unable to load episodes." }
            } else {
                runCatching { withContext(Dispatchers.IO) { reader.contentUnits(details.id()) } }
                    .onSuccess { chapters = it }
                    .onFailure { unitError = it.message ?: "Unable to load chapters." }
            }
        }
        val titlePage = details.origin().flatMap { origin ->
            runCatching {
                discovery.titleWebPage(
                    fr.vriege.anilib.feature.source.SourceCatalogueItemId(
                        SourceId.of(origin.sourceId()),
                        origin.sourceItemKey(),
                    ),
                )
            }.getOrDefault(java.util.Optional.empty())
        }.orElse(null)
        val sourcePage = details.origin().flatMap { origin ->
            runCatching { discovery.sourceWebPage(SourceId.of(origin.sourceId())) }
                .getOrDefault(java.util.Optional.empty())
        }.orElse(null)
        DetailsPage(
            details = details,
            artwork = { Artwork(details, detailPlatform) },
            chapters = chapters,
            episodes = episodes,
            unitError = unitError,
            related = presentation.relatedTitles(details.id()),
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
            edit = { title, metadata ->
                presentation.editTitle(details.id(), title, metadata)
                revision++
            },
            openTitleWeb = titlePage?.let { page -> ({ browserPage = page }) },
            openSourceWeb = sourcePage?.let { page -> ({ browserPage = page }) },
            share = {
                detailPlatform.shareController.share(
                    details.title(),
                    titlePage?.location()?.toString() ?: details.title(),
                )
            },
            openRelated = { relatedId -> navigate { it.openDetails(relatedId) } },
            goBack = { navigate(LibraryNavigator::back) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsPage(
    details: LibraryDetails,
    artwork: @Composable () -> Unit,
    chapters: List<SourceContentUnit>,
    episodes: List<EpisodeSnapshot>,
    unitError: String?,
    related: List<LibraryCard>,
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
    edit: (String, LibraryTitleMetadata) -> Unit,
    openTitleWeb: (() -> Unit)?,
    openSourceWeb: (() -> Unit)?,
    share: () -> Unit,
    openRelated: (LibraryItemId) -> Unit,
    goBack: () -> Unit,
) {
    var editing by remember(details.id()) { mutableStateOf(false) }
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
            item { artwork() }
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
            if (!unitError.isNullOrBlank()) {
                item { Text(unitError, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { editing = true }) { Text("Edit") }
                    Button(onClick = share) { Text("Share") }
                    openTitleWeb?.let { action ->
                        Button(onClick = action) { Text("Open title web") }
                    }
                    openSourceWeb?.let { action ->
                        Button(onClick = action) { Text("Open source web") }
                    }
                }
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
            if (chapters.isNotEmpty()) {
                item { Text("Chapters", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
                items(chapters, key = { it.id().value() }) { chapter ->
                    ContentUnitCard(
                        chapter.title(),
                        chapter.publishedAt().map(dateTimeFormatter::format).orElse("Unknown date"),
                        read,
                    )
                }
            }
            if (episodes.isNotEmpty()) {
                item { Text("Episodes", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
                items(episodes, key = { it.episode().id().value() }) { episode ->
                    val playback = episode.playback().orElse(null)
                    ContentUnitCard(
                        episode.episode().title(),
                        playback?.let { "${it.positionMillis()} ms watched" } ?: "Unwatched",
                        watch,
                    )
                }
            }
            if (related.isNotEmpty()) {
                item { Text("Related titles", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
                items(related, key = { it.id().value() }) { card ->
                    LibraryTitleCard(card, false, false, {}, { openRelated(card.id()) })
                }
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
}

@Composable
private fun ContentUnitCard(title: String, summary: String, open: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = open),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Artwork(details: LibraryDetails, platform: DetailPlatform) {
    val location = details.artwork().orElse(null)
    var image by remember(location) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(location) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(location) {
        image = null
        failed = false
        if (location == null || !(location.scheme.equals("http", true)
                    || location.scheme.equals("https", true))) {
            failed = location != null
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val response = platform.httpClient.execute(
                    HttpRequest.builder(location)
                        .cache(HttpCachePolicy.preferCache(Duration.ofDays(7)))
                        .build(),
                )
                check(response.statusCode() in 200..299) {
                    "Artwork request failed with HTTP ${response.statusCode()}"
                }
                platform.pageDecoder(response.body())
                    ?: error("Artwork format is not supported")
            }
        }.onSuccess { image = it }.onFailure { failed = true }
    }
    when {
        image != null -> Image(
            image!!,
            contentDescription = "${details.title()} artwork",
            modifier = Modifier.fillMaxWidth().height(280.dp),
            contentScale = ContentScale.Fit,
        )
        location == null -> ArtworkPlaceholder("No artwork")
        failed -> ArtworkPlaceholder("Artwork unavailable")
        else -> ArtworkPlaceholder("Loading artwork…")
    }
}

@Composable
private fun ArtworkPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditLibraryTitleDialog(
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
        title = { Text("Edit title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(authors, { authors = it }, label = { Text("Authors") })
                OutlinedTextField(artists, { artists = it }, label = { Text("Artists") })
                OutlinedTextField(genres, { genres = it }, label = { Text("Genres") })
                OutlinedTextField(artwork, { artwork = it }, label = { Text("Artwork URL") })
                androidx.compose.material3.TextButton(onClick = { status = status.next() }) {
                    Text("Status: ${formatEnum(status)}")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    runCatching {
                        val metadata = LibraryTitleMetadata(
                            description,
                            commaSeparated(authors),
                            commaSeparated(artists),
                            status,
                            artwork.trim().takeIf(String::isNotEmpty)
                                ?.let { java.util.Optional.of(URI.create(it)) }
                                ?: java.util.Optional.empty(),
                            commaSeparated(genres),
                        )
                        save(title.trim(), metadata)
                    }.onFailure { failure ->
                        error = failure.message ?: "Unable to edit this title."
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = dismiss) { Text("Cancel") }
        },
    )
}

private fun commaSeparated(value: String): List<String> = value.split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun PublicationStatus.next(): PublicationStatus =
    PublicationStatus.entries[(ordinal + 1) % PublicationStatus.entries.size]

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
            Fact("Genres", joined(details.genres()))
            Fact("Source", details.origin().map { it.sourceId() }.orElse("Local"))
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

private enum class AppSection {
    LIBRARY,
    UPDATES,
    HISTORY,
    BROWSE,
    MORE,
}

private fun AppSection.label(language: LanguagePack): String {
    val selected = if (language == LanguagePack.SYSTEM) {
        when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "fr" -> LanguagePack.FRENCH
            "de" -> LanguagePack.GERMAN
            "es" -> LanguagePack.SPANISH
            "ja" -> LanguagePack.JAPANESE
            else -> LanguagePack.ENGLISH
        }
    } else {
        language
    }
    return when (selected) {
        LanguagePack.FRENCH -> when (this) {
            AppSection.LIBRARY -> "Bibliothèque"
            AppSection.UPDATES -> "Mises à jour"
            AppSection.HISTORY -> "Historique"
            AppSection.BROWSE -> "Parcourir"
            AppSection.MORE -> "Plus"
        }
        LanguagePack.GERMAN -> when (this) {
            AppSection.LIBRARY -> "Bibliothek"
            AppSection.UPDATES -> "Updates"
            AppSection.HISTORY -> "Verlauf"
            AppSection.BROWSE -> "Entdecken"
            AppSection.MORE -> "Mehr"
        }
        LanguagePack.SPANISH -> when (this) {
            AppSection.LIBRARY -> "Biblioteca"
            AppSection.UPDATES -> "Novedades"
            AppSection.HISTORY -> "Historial"
            AppSection.BROWSE -> "Explorar"
            AppSection.MORE -> "Más"
        }
        LanguagePack.JAPANESE -> when (this) {
            AppSection.LIBRARY -> "ライブラリ"
            AppSection.UPDATES -> "更新"
            AppSection.HISTORY -> "履歴"
            AppSection.BROWSE -> "探す"
            AppSection.MORE -> "その他"
        }
        LanguagePack.SYSTEM, LanguagePack.ENGLISH -> when (this) {
            AppSection.LIBRARY -> "Library"
            AppSection.UPDATES -> "Updates"
            AppSection.HISTORY -> "History"
            AppSection.BROWSE -> "Browse"
            AppSection.MORE -> "More"
        }
    }
}

private fun appColorScheme(settings: SettingsSnapshot, dark: Boolean): ColorScheme {
    val accent = when (settings.accentColor()) {
        AccentColor.DEFAULT -> if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
        AccentColor.OCEAN -> if (dark) Color(0xFF76D1FF) else Color(0xFF00658A)
        AccentColor.FOREST -> if (dark) Color(0xFF8FD49A) else Color(0xFF296B38)
        AccentColor.SAKURA -> if (dark) Color(0xFFFFB1C8) else Color(0xFF9B405F)
    }
    val scheme = if (dark) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)
    return when (settings.themeFamily()) {
        ThemeFamily.MATERIAL -> scheme
        ThemeFamily.TONAL -> scheme.copy(
            secondary = accent,
            tertiary = if (dark) Color(0xFFFFB95C) else Color(0xFF805500),
        )
        ThemeFamily.AMOLED -> if (dark) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF111111),
            )
        } else {
            scheme
        }
    }
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
