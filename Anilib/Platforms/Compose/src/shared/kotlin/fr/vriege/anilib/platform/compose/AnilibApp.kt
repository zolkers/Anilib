package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import fr.vriege.anilib.feature.library.LibraryCategory
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
import fr.vriege.anilib.feature.source.SourceContentUnitId
import fr.vriege.anilib.feature.settings.StartScreen
import fr.vriege.anilib.feature.settings.ThemeFamily
import fr.vriege.anilib.feature.settings.ThemeMode
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.SourceCatalogueItem
import fr.vriege.anilib.feature.source.SourceCatalogueItemId
import fr.vriege.anilib.feature.source.SourceContentUnit
import fr.vriege.anilib.feature.source.SourceDescriptor
import fr.vriege.anilib.feature.source.SourceEpisodeId
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.source.SourceListing
import fr.vriege.anilib.feature.source.SourceWebPage
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.player.ui.PlayerController
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
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class DetailPlatform(
    val httpClient: AnilibHttpClient,
    val shareController: ShareController,
    val pageDecoder: (ByteArray) -> ImageBitmap?,
)

private data class BrowseReturnTarget(
    val source: SourceDescriptor,
    val listing: SourceListing,
)

private data class HistoryContentKey(
    val libraryItemId: LibraryItemId,
    val contentId: String,
)

private data class PendingPlayerRequest(
    val token: Any,
    val title: String,
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
    playerFullscreen: Boolean,
    setPlayerFullscreen: (Boolean) -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    setPlayerBackgroundAudio: (Boolean) -> Unit,
    enableAndroidPlayerControls: Boolean,
    enableDesktopPlayerControls: Boolean,
    componentCount: Int,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reportUiFailure: (Throwable) -> Unit = {},
) {
    val detailPlatform = remember(httpClient, shareController, pageDecoder) {
        DetailPlatform(httpClient, shareController, pageDecoder)
    }
    val navigator = remember { LibraryNavigator() }
    val initialSettings = remember(settingsPresentation) { settingsPresentation.snapshot() }
    var destination by remember { mutableStateOf(navigator.state()) }
    var section by remember { mutableStateOf(initialSettings.startScreen().appSection()) }
    var activeReader by remember { mutableStateOf<ReaderController?>(null) }
    var activePlayer by remember { mutableStateOf<PlayerController?>(null) }
    var pendingPlayer by remember { mutableStateOf<PendingPlayerRequest?>(null) }
    var activeTrackingTitle by remember { mutableStateOf<LibraryItemId?>(null) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var moreDestination by remember { mutableStateOf<MoreDestination?>(null) }
    var browseMainDestination by remember { mutableStateOf(true) }
    var settings by remember(settingsPresentation) { mutableStateOf(initialSettings) }
    var recoveredFailure by remember { mutableStateOf<String?>(null) }
    val handleUiFailure: (Throwable) -> Unit = remember(reportUiFailure) {
        { failure ->
            reportRecoverable(failure) { recoverable ->
                runCatching { reportUiFailure(recoverable) }
                recoveredFailure = uiFailureMessage(recoverable)
            }
        }
    }
    DisposableEffect(settingsPresentation) {
        val observation = settingsPresentation.observe { settings = it }
        onDispose { observation.close() }
    }
    val navigate: ((LibraryNavigator) -> Unit) -> Unit = { transition ->
        transition(navigator)
        destination = navigator.state()
    }
    val openSection: (AppSection) -> Unit = { next ->
        val changingMediaKind = section != next &&
            (section == AppSection.ANIME || section == AppSection.MANGA) &&
            (next == AppSection.ANIME || next == AppSection.MANGA)
        if (changingMediaKind && destination.page() == LibraryPage.DETAILS) {
            navigate(LibraryNavigator::openLibrary)
        }
        section = next
        if (next == AppSection.BROWSE) browseMainDestination = true
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
        LocalExtensionIconEnvironment provides ExtensionIconEnvironment(httpClient, pageDecoder),
        LocalReducedMotion provides settings.reducedMotion(),
        LocalLanguagePack provides settings.languagePack(),
        LocalUiFailureHandler provides handleUiFailure,
    ) {
        val scope = rememberCrashSafeCoroutineScope()
        MaterialTheme(colorScheme = appColorScheme(settings, useDarkTheme)) {
            Surface(modifier = Modifier.fillMaxSize()) {
            val readerController = activeReader
            val playerController = activePlayer
            val playerRequest = pendingPlayer
            val trackingTitle = activeTrackingTitle
            if (readerController != null) {
                DisposableEffect(readerController) {
                    onDispose { readerController.close() }
                }
                ReaderScreen(
                    readerController,
                    pageDecoder,
                    applyReaderOrientationPolicy,
                    downloads::enqueue,
                ) { activeReader = null }
            } else if (playerController != null) {
                PlayerSelectionScreen(
                    playerController,
                    playerFullscreen,
                    setPlayerFullscreen,
                    applyPlayerOrientationPolicy,
                    requestPlayerPictureInPicture,
                    setPlayerActive,
                    setPlayerBackgroundAudio,
                    enableAndroidPlayerControls,
                    enableDesktopPlayerControls,
                ) {
                    activePlayer = null
                }
            } else if (playerRequest != null) {
                PlayerLoadingScreen(playerRequest.title) {
                    pendingPlayer = null
                }
            } else if (trackingTitle != null) {
                val details = presentation.details(trackingTitle).orElse(null)
                if (details == null) {
                    activeTrackingTitle = null
                } else {
                    TitleTrackingScreen(
                        presentation = tracking,
                        browserRuntimeStatus = browserRuntimeStatus,
                        itemId = trackingTitle,
                        title = details.title(),
                        kind = details.kind(),
                        goBack = { activeTrackingTitle = null },
                    )
                }
            } else {
                val openReader: (LibraryItemId, SourceContentUnitId?) -> Unit = { id, contentUnitId ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                if (contentUnitId == null) reader.open(id) else reader.open(id, contentUnitId)
                            }
                        }
                            .onSuccess {
                                readerError = null
                                activeReader = it
                            }
                            .onFailure { readerError = it.message ?: "The reader could not be opened." }
                    }
                }
                val enqueueDownload: (LibraryItemId) -> Unit = { id ->
                    runCatching { downloads.enqueue(id) }
                        .onSuccess { downloadError = null }
                        .onFailure { downloadError = it.message ?: "The download could not be queued." }
                }
                val openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit = { id, episodeId ->
                    val request = PendingPlayerRequest(
                        token = Any(),
                        title = presentation.details(id).orElse(null)?.title() ?: "Player",
                    )
                    pendingPlayer = request
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                check(player.canOpen(id)) { "This title no longer has a streaming source." }
                                val selectedEpisode = episodeId ?: player.episodes(id)
                                    .firstOrNull()
                                    ?.episode()
                                    ?.id()
                                    ?: error("No episodes are available from this source.")
                                player.open(id, selectedEpisode)
                            }
                        }
                            .onSuccess {
                                if (pendingPlayer?.token === request.token) {
                                    playerError = null
                                    activePlayer = it
                                    pendingPlayer = null
                                } else {
                                    it.close()
                                }
                            }
                            .onFailure {
                                if (pendingPlayer?.token === request.token) {
                                    pendingPlayer = null
                                    playerError = it.message ?: "The episode could not be opened."
                                }
                            }
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val showGlobalNavigation = when (section) {
                        AppSection.ANIME, AppSection.MANGA -> destination.page() != LibraryPage.DETAILS
                        AppSection.UPDATES -> true
                        AppSection.BROWSE -> browseMainDestination
                        AppSection.MORE -> moreDestination == null
                    }
                    val useNavigationRail = when (settings.navigationStyle()) {
                        NavigationStyle.ADAPTIVE -> maxWidth >= 720.dp
                        NavigationStyle.BOTTOM_BAR -> false
                        NavigationStyle.NAVIGATION_RAIL -> true
                    }
                    AdaptiveShell(
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
                        showGlobalNavigation,
                        useNavigationRail,
                        { browseMainDestination = it },
                        enableDesktopPlayerControls,
                        componentCount,
                        navigate,
                        openSection,
                        openReader,
                        readerError,
                        openPlayer,
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
            recoveredFailure?.let { message ->
                CrashRecoveryDialog(
                    message = message,
                    continueApplication = { recoveredFailure = null },
                    returnToLibrary = {
                        runCatching { activeReader?.close() }
                        activeReader = null
                        activePlayer = null
                        pendingPlayer = null
                        activeTrackingTitle = null
                        moreDestination = null
                        navigate(LibraryNavigator::openLibrary)
                        if (section != AppSection.ANIME && section != AppSection.MANGA) {
                            section = AppSection.ANIME
                        }
                        recoveredFailure = null
                    },
                )
            }
            playerError?.let { message ->
                UiNoticeDialog(UiNoticeKind.ERROR, message, dismiss = { playerError = null })
            }
        }
    }
}

@Composable
private fun AdaptiveShell(
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
    showGlobalNavigation: Boolean,
    useNavigationRail: Boolean,
    browseDestinationChanged: (Boolean) -> Unit,
    supportsPlayerWindowModes: Boolean,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (showGlobalNavigation && useNavigationRail) {
            AnilibNavigationRail(section, settings.languagePack(), openSection)
            VerticalDivider()
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    supportsPlayerWindowModes,
                    componentCount,
                    navigate,
                    openSection,
                    openReader,
                    readerError,
                    openPlayer,
                    enqueueDownload,
                    downloadError,
                    openTracking,
                    moreDestination,
                    openMore,
                    closeMore,
                    browseDestinationChanged,
                )
            }
            if (showGlobalNavigation && !useNavigationRail) {
                HorizontalDivider()
                AnilibNavigationBar(section, settings.languagePack(), openSection)
            }
        }
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
    supportsPlayerWindowModes: Boolean,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
    browseDestinationChanged: (Boolean) -> Unit,
) {
    var browseReturnTarget by remember { mutableStateOf<BrowseReturnTarget?>(null) }
    when (section) {
        AppSection.ANIME,
        AppSection.MANGA,
        -> when (destination.page()) {
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
                enqueueDownload,
                downloadError,
                openTracking,
                goBackOverride = browseReturnTarget?.let {
                    {
                        navigate(LibraryNavigator::openLibrary)
                        openSection(AppSection.BROWSE)
                    }
                },
            )
            else -> LibraryPageContent(
                presentation,
                discovery,
                downloads,
                if (section == AppSection.ANIME) MediaKind.ANIME else MediaKind.MANGA,
                navigate,
            )
        }
        AppSection.UPDATES -> UpdatesScreen(updates, downloads)
        AppSection.BROWSE -> DiscoveryScreen(
            discovery,
            presentation,
            extensionRepositories,
            apkExtensionPlatform,
            browserCookies,
            browserRuntimeStatus,
            initialSource = browseReturnTarget?.source,
            initialListing = browseReturnTarget?.listing ?: SourceListing.POPULAR,
            openDetails = { id, kind, source, listing ->
                browseReturnTarget = BrowseReturnTarget(source, listing)
                navigate { it.openDetails(id) }
                openSection(if (kind == MediaKind.ANIME) AppSection.ANIME else AppSection.MANGA)
            },
            returnTargetConsumed = { browseReturnTarget = null },
            navigationVisibilityChanged = browseDestinationChanged,
            manageExtensions = {
                openSection(AppSection.MORE)
                openMore(MoreDestination.EXTENSION_REPOSITORIES)
            },
        )
        AppSection.MORE -> when (moreDestination) {
            MoreDestination.HISTORY -> HistoryPage(
                presentation,
                reader,
                player,
                openReader,
                openPlayer,
                readerError,
                closeMore,
            ) { row, transition ->
                navigate(transition)
                openSection(if (row.kind() == MediaKind.ANIME) AppSection.ANIME else AppSection.MANGA)
            }
            MoreDestination.DOWNLOADS -> DownloadsScreen(downloads, closeMore)
            MoreDestination.BACKUP -> BackupScreen(backup, backupImportPicker, closeMore)
            MoreDestination.TRACKING -> TrackerAccountsScreen(tracking, browserRuntimeStatus, closeMore)
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
                closeMore,
            )
            MoreDestination.SETTINGS -> SettingsScreen(
                settingsPresentation,
                settings,
                supportsPlayerWindowModes,
                networkMaintenance,
                browserDataController,
                backupImportPicker,
                { openMore(MoreDestination.BACKUP) },
                { openMore(MoreDestination.DOWNLOADS) },
                { openMore(MoreDestination.TRACKING) },
                { openMore(MoreDestination.ABOUT) },
                closeMore,
            )
            MoreDestination.ABOUT -> AboutScreen(componentCount, applicationUpdates, closeMore)
            null -> MorePage(
                componentCount,
                settings,
                settingsPresentation::setIncognitoMode,
                downloads,
                { openMore(MoreDestination.HISTORY) },
                { openMore(MoreDestination.DOWNLOADS) },
                { openMore(MoreDestination.BACKUP) },
                { openMore(MoreDestination.TRACKING) },
                { openMore(MoreDestination.CATEGORIES) },
                { openMore(MoreDestination.STATISTICS) },
                { openMore(MoreDestination.EXTENSION_REPOSITORIES) },
                { openMore(MoreDestination.SETTINGS) },
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
    kind: MediaKind,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember(presentation) { mutableStateOf(0) }
    val overview = remember(presentation, revision) { presentation.library() }
    val scopedCategories = overview.categoryConfigurations()
        .filter { it.scope().supports(kind) }
        .map { it.name() }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var category by remember(presentation, kind) {
        mutableStateOf(
            overview.displayPreferences().defaultCategory().orElse(null)
                ?.takeIf { it in scopedCategories },
        )
    }
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
        .filter { it.kind() == kind }
        .filter { it.favorite() }
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
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(if (kind == MediaKind.ANIME) "library.search.anime" else "library.search.manga")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (kind == MediaKind.ANIME) "ui.anime" else "ui.manga")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "ui.search.library")
                    }
                    IconButton(onClick = {
                        update { presentation.setSort(overview.displayPreferences().sort().next()) }
                    }) {
                        Icon(Icons.Default.SortByAlpha, contentDescription = "ui.sort.library")
                    }
                    IconButton(onClick = {
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
                        Icon(
                            if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = "ui.change.library.layout",
                        )
                    }
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "ui.select.library.titles")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        UiTranslations.format("dynamic.selected.count", LocalLanguagePack.current, selected.size),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        enabled = titles.isNotEmpty(),
                        onClick = {
                            selected = if (selected.size == titles.size) {
                                emptySet()
                            } else {
                                titles.mapTo(linkedSetOf()) { it.id() }
                            }
                        },
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = "ui.all")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { update { presentation.setFavorite(selected, false) } },
                    ) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "ui.remove.from.library")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty() && scopedCategories.isNotEmpty(),
                        onClick = { categoryAction = true },
                    ) {
                        Icon(Icons.Outlined.Category, contentDescription = "ui.category")
                    }
                    IconButton(
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
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = "ui.download")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { migrating = true },
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = "ui.migrate")
                    }
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { confirmingDelete = true },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "ui.delete")
                    }
                }
            }
            if (scopedCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = category == null,
                        onClick = {
                            category = null
                            update { presentation.setDefaultCategory(Optional.empty()) }
                        },
                        label = { Text("ui.all.categories") },
                    )
                    FilterChip(
                        selected = category == "",
                        onClick = {
                            category = ""
                            update { presentation.setDefaultCategory(Optional.empty()) }
                        },
                        label = { Text("ui.default") },
                    )
                    scopedCategories.forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = {
                                category = value
                                update { presentation.setDefaultCategory(Optional.of(value)) }
                            },
                            label = { Text(value) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (overview.titles().none { it.kind() == kind && it.favorite() }) {
                EmptyPage(
                    UiTranslations.format(
                        "dynamic.empty.library.kind",
                        LocalLanguagePack.current,
                        UiTranslations.translate(
                            if (kind == MediaKind.ANIME) "ui.anime" else "ui.manga",
                            LocalLanguagePack.current,
                        ).lowercase(),
                    ),
                )
            } else if (titles.isEmpty()) {
                EmptyPage("ui.no.titles.match.library.filters")
            } else {
                if (overview.displayPreferences().mode() == LibraryDisplayMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(overview.displayPreferences().density().minimumCardWidth()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(titles, key = { it.id().value() }) { card ->
                            LibraryCoverCard(
                                card,
                                null,
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
                                null,
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
            categories = scopedCategories,
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
            title = {
                Text(UiTranslations.format("dynamic.delete.titles", LocalLanguagePack.current, selected.size))
            },
            text = { Text("ui.this.removes.the.selected.titles.from.your.library") },
            confirmButton = {
                TextButton(onClick = {
                    update { presentation.deleteTitles(selected) }
                    selected = emptySet()
                    selectionMode = false
                    confirmingDelete = false
                }) { Text("ui.delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("ui.cancel")
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
        title = { Text("ui.update.categories") },
        text = {
            Column {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category, modifier = Modifier.weight(1f))
                        TextButton(onClick = { add(category) }) {
                            Text("ui.add")
                        }
                        TextButton(onClick = { remove(category) }) {
                            Text("ui.remove")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = dismiss) { Text("ui.close") }
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
    val language = LocalLanguagePack.current
    val scope = rememberCrashSafeCoroutineScope()
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
                if (values.isEmpty()) error = UiTranslations.translate("ui.no.migration.candidates", language)
            }.onFailure { failure ->
                error = failure.message ?: UiTranslations.translate("ui.unable.load.migration.candidates", language)
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
                error = failure.message ?: UiTranslations.format(
                    "dynamic.unable.migrate",
                    language,
                    current.title(),
                )
            }
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(UiTranslations.format("dynamic.migrate.sequence", language, index + 1, cards.size))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current.title(), fontWeight = FontWeight.SemiBold)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) {
                    Text("ui.loading")
                } else if (targetSource == null) {
                    Text("ui.choose.a.target.source")
                    sources.take(12).forEach { source ->
                        TextButton(onClick = { selectSource(source.id()) }) {
                            Text("${source.displayName()} · ${source.languageTag()}")
                        }
                    }
                } else {
                    TextButton(onClick = {
                        targetSource = null
                        candidates = emptyList()
                    }) { Text("ui.change.source") }
                    candidates.take(12).forEach { candidate ->
                        TextButton(onClick = { migrate(candidate) }) {
                            Text(candidate.title())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = dismiss) { Text("ui.cancel") }
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
    remainingEpisodeCount: Int?,
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
            RemoteArtwork(
                card.artwork().orElse(null),
                card.title(),
                modifier = Modifier.size(width = 58.dp, height = 82.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.width(14.dp))
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "ui.in.library",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp).size(22.dp),
                )
            }
            RemainingEpisodeBadge(remainingEpisodeCount)
        }
    }
}

@Composable
private fun LibraryCoverCard(
    card: LibraryCard,
    remainingEpisodeCount: Int?,
    selected: Boolean,
    selectionMode: Boolean,
    select: () -> Unit,
    openDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = if (selectionMode) select else openDetails),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            RemoteArtwork(
                card.artwork().orElse(null),
                card.title(),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            ) {
                Text(
                    card.title(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { select() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                RemainingEpisodeBadge(remainingEpisodeCount)
            }
        }
    }
}

@Composable
private fun RemainingEpisodeBadge(remainingEpisodeCount: Int?) {
    if (remainingEpisodeCount == null || remainingEpisodeCount <= 0) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            remainingEpisodeCount.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryPage(
    presentation: LibraryPresentation,
    reader: ReaderPresentation,
    player: PlayerPresentation,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    resumeError: String?,
    goBack: () -> Unit,
    navigate: (LibraryHistoryRow, (LibraryNavigator) -> Unit) -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    val history = remember(revision) { presentation.history() }
    val cards = remember(revision) { presentation.library().titles().associateBy { it.id() } }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(MediaKind.ANIME) }
    var contentLabels by remember(reader, player) {
        mutableStateOf<Map<HistoryContentKey, String>>(emptyMap())
    }
    var episodeIds by remember(player) {
        mutableStateOf<Map<HistoryContentKey, SourceEpisodeId>>(emptyMap())
    }
    CrashSafeLaunchedEffect(reader, player, kind, history) {
        val rows = history.entries().filter { it.kind() == kind }
        val content = withContext(Dispatchers.IO) {
            val labels = mutableMapOf<HistoryContentKey, String>()
            val resolvedEpisodeIds = mutableMapOf<HistoryContentKey, SourceEpisodeId>()
            rows.groupBy { it.libraryItemId() }.forEach { (libraryItemId, titleRows) ->
                if (kind == MediaKind.ANIME) {
                    runCatching { player.episodes(libraryItemId) }.getOrDefault(emptyList()).forEach { episode ->
                        val key = HistoryContentKey(libraryItemId, episode.episode().id().value())
                        val fallbackPosition = titleRows
                            .filter { it.contentId() == episode.episode().id().value() }
                            .maxOfOrNull { it.position() }
                            ?: 0L
                        val position = episode.playback()
                            .map { it.positionMillis() }
                            .orElse(fallbackPosition)
                        labels[key] = "${episode.episode().title()} · ${formatMediaPosition(position)}"
                        resolvedEpisodeIds[key] = episode.episode().id()
                    }
                } else {
                    runCatching { reader.contentUnits(libraryItemId) }.getOrDefault(emptyList()).forEach { unit ->
                        labels[HistoryContentKey(libraryItemId, unit.id().value())] = unit.title()
                    }
                }
            }
            labels to resolvedEpisodeIds
        }
        contentLabels = content.first
        episodeIds = content.second
    }
    val entries = history.entries().filter {
        val contentLabel = contentLabels[HistoryContentKey(it.libraryItemId(), it.contentId())]
        it.kind() == kind &&
            (query.isBlank() || it.title().contains(query, ignoreCase = true) ||
                contentLabel?.contains(query, ignoreCase = true) == true)
    }
    val groups = entries.groupBy { row ->
        row.openedAt().atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("ui.search.history") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("ui.history")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ui.back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "ui.search.history")
                    }
                    IconButton(
                        enabled = history.entries().any { it.kind() == kind },
                        onClick = {
                            history.entries().filter { it.kind() == kind }.forEach { row ->
                                presentation.removeHistoryEntry(
                                    row.libraryItemId(),
                                    row.contentId(),
                                    row.openedAt(),
                                )
                            }
                            revision++
                        },
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "ui.clear.history")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = if (kind == MediaKind.ANIME) 0 else 1) {
                Tab(
                    selected = kind == MediaKind.ANIME,
                    onClick = { kind = MediaKind.ANIME },
                        text = { Text("ui.anime") },
                )
                Tab(
                    selected = kind == MediaKind.MANGA,
                    onClick = { kind = MediaKind.MANGA },
                        text = { Text("ui.manga") },
                )
            }
            resumeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (history.entries().isEmpty()) {
                EmptyPage("ui.opened.titles.appear.here")
            } else if (entries.isEmpty()) {
                EmptyPage("ui.no.history.entries.match.search")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (date, rows) ->
                        item(key = "history-date-$date") {
                            Text(
                                historyDateLabel(date),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                                cards[row.libraryItemId()],
                                contentLabels[HistoryContentKey(row.libraryItemId(), row.contentId())],
                                resume = {
                                    if (row.kind() == MediaKind.ANIME) {
                                        openPlayer(
                                            row.libraryItemId(),
                                            episodeIds[HistoryContentKey(row.libraryItemId(), row.contentId())],
                                        )
                                    } else {
                                        openReader(row.libraryItemId(), null)
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
                                toggleFavorite = {
                                    val favorite = cards[row.libraryItemId()]?.favorite() == true
                                    presentation.setFavorite(setOf(row.libraryItemId()), !favorite)
                                    revision++
                                },
                                openDetails = { transition ->
                                    navigate(row, transition)
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
    card: LibraryCard?,
    contentLabel: String?,
    resume: () -> Unit,
    remove: () -> Unit,
    toggleFavorite: () -> Unit,
    openDetails: ((LibraryNavigator) -> Unit) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            openDetails { it.openDetails(row.libraryItemId()) }
        }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteArtwork(
            card?.artwork()?.orElse(null),
            row.title(),
            modifier = Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contentLabel ?: if (row.kind() == MediaKind.ANIME) {
                    "Episode · ${formatMediaPosition(row.position())}"
                } else {
                    "Chapter"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = resume) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = if (row.kind() == MediaKind.ANIME) "Resume" else "Continue",
            )
        }
        IconButton(onClick = toggleFavorite) {
            Icon(
                if (card?.favorite() == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (card?.favorite() == true) {
                    "ui.remove.from.library"
                } else {
                    "ui.add.to.library"
                },
            )
        }
        IconButton(onClick = remove) {
            Icon(Icons.Default.Delete, contentDescription = "ui.remove.history.entry")
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
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    goBackOverride: (() -> Unit)?,
) {
    val id = destination.selectedTitle().orElse(null)
    var relatedBackStack by remember { mutableStateOf<List<LibraryItemId>>(emptyList()) }
    var revision by remember(id) { mutableStateOf(0) }
    var browserPage by remember(id) {
        mutableStateOf<SourceWebPage?>(null)
    }
    var chapters by remember(id) { mutableStateOf(listOf<SourceContentUnit>()) }
    var episodes by remember(id) { mutableStateOf(listOf<EpisodeSnapshot>()) }
    var unitError by remember(id) { mutableStateOf<String?>(null) }
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
        CrashSafeLaunchedEffect(details.id(), revision) {
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
            artwork = { modifier -> MediaArtwork(details, detailPlatform, modifier) },
            chapters = chapters,
            episodes = episodes,
            unitError = unitError,
            related = presentation.relatedTitles(details.id()),
            canRead = runCatching { reader.canOpen(details.id()) }.getOrDefault(false),
            canWatch = runCatching { player.canOpen(details.id()) }.getOrDefault(false),
            canDownload = runCatching { downloads.canEnqueue(details.id()) }.getOrDefault(false),
            canTrack = true,
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
            track = { openTracking(details.id()) },
            favorite = {
                presentation.setFavorite(setOf(details.id()), !details.favorite())
                revision++
            },
            edit = { title, metadata ->
                presentation.editTitle(details.id(), title, metadata)
                revision++
            },
            setCategories = { selected ->
                presentation.setTitleCategories(details.id(), selected)
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
private fun DetailsPage(
    details: LibraryDetails,
    categories: List<LibraryCategory>,
    sourceName: String,
    artwork: @Composable (Modifier) -> Unit,
    chapters: List<SourceContentUnit>,
    episodes: List<EpisodeSnapshot>,
    unitError: String?,
    related: List<LibraryCard>,
    canRead: Boolean,
    canWatch: Boolean,
    canDownload: Boolean,
    canTrack: Boolean,
    readerError: String?,
    downloadError: String?,
    read: (SourceContentUnit?) -> Unit,
    watch: () -> Unit,
    watchEpisode: (EpisodeSnapshot) -> Unit,
    download: () -> Unit,
    downloadChapter: (SourceContentUnit) -> Unit,
    downloadEpisode: (EpisodeSnapshot) -> Unit,
    track: () -> Unit,
    favorite: () -> Unit,
    edit: (String, LibraryTitleMetadata) -> Unit,
    setCategories: (Set<String>) -> Unit,
    openTitleWeb: (() -> Unit)?,
    openSourceWeb: (() -> Unit)?,
    share: () -> Unit,
    openRelated: (LibraryItemId) -> Unit,
    goBack: () -> Unit,
) {
    var editing by remember(details.id()) { mutableStateOf(false) }
    var editingCategories by remember(details.id()) { mutableStateOf(false) }
    MediaDetailsScreen(
        model = MediaDetailsUiModel(
            title = details.title(),
            authors = details.authors(),
            status = formatEnum(details.publicationStatus()),
            sourceName = sourceName,
            description = details.description(),
        genres = details.genres(),
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
        canOpenWeb = openTitleWeb != null || openSourceWeb != null,
        canDownload = canDownload,
        primaryLabel = if (canWatch) "ui.watch" else "ui.read",
        canOpenPrimary = canWatch || canRead,
        errors = listOfNotNull(readerError, downloadError, unitError),
        toggleFavorite = favorite,
        track = track,
        openWeb = openTitleWeb ?: openSourceWeb ?: {},
        download = download,
        share = share,
        manageCategories = { editingCategories = true },
        edit = { editing = true },
        openPrimary = if (canWatch) watch else ({ read(null) }),
        goBack = goBack,
    ) {
            if (chapters.isNotEmpty()) {
                item {
                    MediaContentHeading(
                        UiTranslations.format("dynamic.chapters.count", LocalLanguagePack.current, chapters.size),
                    )
                }
                items(chapters, key = { it.id().value() }) { chapter ->
                    MediaUnitRow(
                        title = chapter.title(),
                        summary = chapter.publishedAt().map(mediaDateTimeFormatter::format).orElse("Available"),
                        open = { read(chapter) },
                        download = { downloadChapter(chapter) },
                    )
                }
            }
            if (episodes.isNotEmpty()) {
                item {
                    MediaContentHeading(
                        UiTranslations.format("dynamic.episodes.count", LocalLanguagePack.current, episodes.size),
                    )
                }
                items(episodes, key = { it.episode().id().value() }) { episode ->
                    val playback = episode.playback().orElse(null)
                    val metadata = listOfNotNull(
                        episode.episode().uploadedAt().map(mediaDateTimeFormatter::format).orElse(null),
                        episode.episode().scanlator().orElse(null),
                        playback?.let {
                            if (it.completed()) {
                                UiTranslations.translate("ui.watched", LocalLanguagePack.current)
                            } else {
                                UiTranslations.format(
                                    "dynamic.progress",
                                    LocalLanguagePack.current,
                                    formatMediaPosition(it.positionMillis()),
                                )
                            }
                        },
                    ).ifEmpty { listOf("Available") }.joinToString(" · ")
                    MediaUnitRow(
                        title = episode.episode().title(),
                        summary = metadata,
                        open = { watchEpisode(episode) },
                        download = { downloadEpisode(episode) },
                        muted = playback?.completed() == true,
                    )
                }
            }
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
            save = { selected ->
                setCategories(selected)
                editingCategories = false
            },
        )
    }
}

@Composable
private fun TitleCategoriesDialog(
    details: LibraryDetails,
    categories: List<LibraryCategory>,
    dismiss: () -> Unit,
    save: (Set<String>) -> Unit,
) {
    var selected by remember(details.id(), categories) {
        mutableStateOf<Set<String>>(
            details.categories().filterTo(linkedSetOf()) { name ->
                categories.any { it.name() == name }
            },
        )
    }
    var error by remember(details.id()) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ui.categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) {
                    Text("ui.no.categories.for.this.library.type")
                } else {
                    Text(
                        "ui.select.categories.for.this.title",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(categories, key = LibraryCategory::name) { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = selected.toggleCategory(category.name())
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = category.name() in selected,
                                    onCheckedChange = {
                                        selected = selected.toggleCategory(category.name())
                                    },
                                )
                                Text(
                                    category.name(),
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = categories.isNotEmpty(),
                onClick = {
                    runCatching { save(selected) }
                        .onFailure { error = it.message ?: "ui.unable.to.update.categories" }
                },
            ) { Text("ui.save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("ui.cancel") } },
    )
}

private fun Set<String>.toggleCategory(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun RelatedTitlesSection(
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
private fun RelatedTitleCard(card: LibraryCard, open: () -> Unit) {
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
private fun MediaArtwork(details: LibraryDetails, platform: DetailPlatform, modifier: Modifier) {
    val location = details.artwork().orElse(null)
    var image by remember(location) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(location) { mutableStateOf(false) }
    CrashSafeLaunchedEffect(location) {
        image = null
        failed = false
        if (location == null || !(location.scheme.equals("http", true)
                    || location.scheme.equals("https", true))) {
            failed = location != null
            return@CrashSafeLaunchedEffect
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
                platform.pageDecoder(response.body()) ?: error("Artwork format is not supported")
            }
        }.onSuccess { image = it }.onFailure { failed = true }
    }
    when {
        image != null -> Image(
            image!!,
            contentDescription = UiTranslations.format(
                "dynamic.title.artwork",
                LocalLanguagePack.current,
                details.title(),
            ),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        location == null -> MediaArtworkPlaceholder("ui.no.artwork", modifier)
        failed -> MediaArtworkPlaceholder("ui.artwork.unavailable", modifier)
        else -> MediaArtworkPlaceholder("ui.loading.artwork", modifier)
    }
}

@Composable
private fun MediaArtworkPlaceholder(label: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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

private fun commaSeparated(value: String): List<String> = value.split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun PublicationStatus.next(): PublicationStatus =
    PublicationStatus.entries[(ordinal + 1) % PublicationStatus.entries.size]

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
    openHistory: () -> Unit,
    openDownloads: () -> Unit,
    openBackup: () -> Unit,
    openTracking: () -> Unit,
    openCategories: () -> Unit,
    openStatistics: () -> Unit,
    openExtensionRepositories: () -> Unit,
    openSettings: () -> Unit,
) {
    var queue by remember(downloads) { mutableStateOf(downloads.queue()) }
    DisposableEffect(downloads) {
        val observation = downloads.observe { queue = downloads.queue() }
        onDispose { observation.close() }
    }
    val pendingDownloads = queue.jobs().count {
        it.status() != DownloadStatus.COMPLETED && it.status() != DownloadStatus.CANCELLED
    }
    Scaffold(topBar = { TopAppBar(title = { Text("ui.more") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { AnilibSection("ui.quick.filters") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreSwitchRow(
                        "ui.downloaded.only",
                        "ui.use.downloaded.content.without.the.online.fallback",
                        queue.offlineMode(),
                        Icons.Outlined.Download,
                        downloads::setOfflineMode,
                    )
                    MoreSwitchRow(
                        "ui.incognito.mode",
                        "ui.pause.reading.and.watching.history",
                        settings.incognitoMode(),
                        Icons.Outlined.VisibilityOff,
                        setIncognitoMode,
                    )
                }
            }
            item { AnilibSection("ui.library") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.history",
                        "ui.recently.watched.and.read",
                        Icons.Default.History,
                        openHistory,
                    )
                    MoreRow(
                        "ui.download.queue",
                        if (pendingDownloads == 0) {
                            "ui.no.pending.downloads"
                        } else {
                            UiTranslations.format(
                                "dynamic.pending.downloads",
                                LocalLanguagePack.current,
                                pendingDownloads,
                            )
                        },
                        Icons.Outlined.Download,
                        openDownloads,
                    )
                    MoreRow(
                        "ui.categories",
                        "ui.organize.anime.and.manga.in.your.library",
                        Icons.Outlined.Category,
                        openCategories,
                    )
                    MoreRow(
                        "ui.statistics",
                        "ui.library.and.reading.activity",
                        Icons.Outlined.Assessment,
                        openStatistics,
                    )
                }
            }
            item { AnilibSection("ui.services") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.backup.and.restore",
                        "ui.create.or.restore.a.local.backup",
                        Icons.Outlined.Backup,
                        openBackup,
                    )
                    MoreRow(
                        "ui.tracking",
                        "ui.manage.external.tracking.accounts",
                        Icons.Outlined.Sync,
                        openTracking,
                    )
                    MoreRow(
                        "ui.extension.repositories",
                        "ui.add.compatible.extension.repositories.and.install.sources",
                        Icons.Outlined.Extension,
                        openExtensionRepositories,
                    )
                }
            }
            item { AnilibSection("ui.application") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.settings",
                        UiTranslations.format(
                            "dynamic.bundle.count",
                            LocalLanguagePack.current,
                            componentCount,
                        ),
                        Icons.Outlined.Settings,
                        openSettings,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MoreSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnilibLeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MoreRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnilibLeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingDetails(openLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ui.this.title.is.no.longer.in.the.library")
            Spacer(Modifier.height(12.dp))
            Button(onClick = openLibrary) { Text("ui.back.to.library") }
        }
    }
}

private fun librarySummary(overview: LibraryOverview): String =
    "${overview.titles().size} titles | ${overview.favoriteCount()} favorites | " +
        "${overview.categories().size} categories"

private fun cardMetadata(card: LibraryCard): String {
    val categoryText = joined(card.categories())
    val progressText = card.progress().map { progress(card.kind(), it) }.orElse("Not started")
    return "${formatEnum(card.kind())} | $categoryText | $progressText"
}

private fun progress(kind: MediaKind, progress: LibraryProgress): String {
    if (progress.extent() == LibraryProgress.UNKNOWN_EXTENT) {
        return if (kind == MediaKind.ANIME && progress.position() >= 1_000L) {
            formatMediaPosition(progress.position())
        } else {
            progress.position().toString()
        }
    }
    val percentage = (progress.completion().orElse(0.0) * 100.0).roundToInt()
    val position = if (kind == MediaKind.ANIME && progress.extent() >= 1_000L) {
        formatMediaPosition(progress.position())
    } else {
        progress.position().toString()
    }
    val extent = if (kind == MediaKind.ANIME && progress.extent() >= 1_000L) {
        formatMediaPosition(progress.extent())
    } else {
        progress.extent().toString()
    }
    return "$position / $extent ($percentage%)"
}

private fun joined(values: List<String>): String =
    if (values.isEmpty()) "None" else values.joinToString(", ")

private fun formatEnum(value: Enum<*>): String = value.name
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .replaceFirstChar(Char::uppercase)

private enum class AppSection {
    ANIME,
    MANGA,
    UPDATES,
    BROWSE,
    MORE,
}

private fun AppSection.label(language: LanguagePack): String {
    val source = when (this) {
        AppSection.ANIME -> "ui.anime"
        AppSection.MANGA -> "ui.manga"
        AppSection.UPDATES -> "ui.updates"
        AppSection.BROWSE -> "ui.explore"
        AppSection.MORE -> "ui.more"
    }
    return UiTranslations.translate(source, language)
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
    StartScreen.LIBRARY -> AppSection.ANIME
    StartScreen.UPDATES -> AppSection.UPDATES
    StartScreen.HISTORY -> AppSection.MORE
    StartScreen.BROWSE -> AppSection.BROWSE
    StartScreen.MORE -> AppSection.MORE
}

private enum class MoreDestination {
    HISTORY,
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
    AppSection.ANIME -> Icons.Default.PlayArrow
    AppSection.MANGA -> Icons.Default.CollectionsBookmark
    AppSection.UPDATES -> Icons.Default.NewReleases
    AppSection.BROWSE -> Icons.Default.Explore
    AppSection.MORE -> Icons.Default.MoreHoriz
}
