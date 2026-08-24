package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vriege.anilib.feature.library.LibraryItemId
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.backup.ui.BackupPresentation
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryPresentation
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState
import fr.vriege.anilib.feature.library.ui.LibraryNavigator
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
import fr.vriege.anilib.feature.source.SourceEpisodeId
import fr.vriege.anilib.feature.reader.ui.ReaderController
import fr.vriege.anilib.feature.reader.ui.ReaderPresentation
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.player.ui.PlayerController
import fr.vriege.anilib.feature.player.ui.PlayerPresentation
import fr.vriege.anilib.feature.tracker.ui.TrackerPresentation
import fr.vriege.anilib.feature.updates.ui.UpdatePresentation
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdatePresentation
import fr.vriege.anilib.framework.http.HttpCookieJar
import fr.vriege.anilib.framework.http.AnilibHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DetailPlatform(
    val shareController: ShareController,
)

internal data class PendingPlayerRequest(
    val token: Any,
    val title: String,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    playerFullscreen: Boolean,
    setPlayerFullscreen: (Boolean) -> Unit,
    setPlayerActive: (Boolean) -> Unit,
    componentCount: Int,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reportUiFailure: (Throwable) -> Unit = {},
) {
    val detailPlatform = remember(shareController) {
        DetailPlatform(shareController)
    }
    val imageEnvironment = remember(httpClient, pageDecoder) {
        ExtensionIconEnvironment(httpClient, pageDecoder)
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
    var browseDetailsTitle by remember { mutableStateOf<LibraryItemId?>(null) }
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
        if (next == AppSection.BROWSE) {
            browseMainDestination = true
            browseDetailsTitle = null
        }
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
        LocalExtensionIconEnvironment provides imageEnvironment,
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
            val currentSetPlayerFullscreen = rememberUpdatedState(setPlayerFullscreen)
            DisposableEffect(playerController != null) {
                val resetFullscreenOnDispose = playerController != null
                onDispose {
                    if (resetFullscreenOnDispose) currentSetPlayerFullscreen.value(false)
                }
            }
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
                // Sources list episodes newest first, so the neighbour towards index 0 is the
                // next episode and the one after it is the previous, matching the reader.
                var episodeNeighbours by remember(playerController) {
                    mutableStateOf<Pair<SourceEpisodeId?, SourceEpisodeId?>>(null to null)
                }
                // Guards the native player: opening a second session while one is still
                // initialising tears the first down mid-init and faults the video layer.
                var episodeSwitching by remember(playerController) { mutableStateOf(false) }
                CrashSafeLaunchedEffect(playerController) {
                    val current = runCatching { playerController.snapshot() }.getOrNull()
                        ?: return@CrashSafeLaunchedEffect
                    val episodes = withContext(Dispatchers.IO) {
                        runCatching {
                            if (presentation.details(current.libraryItemId()).isPresent) {
                                player.episodes(current.libraryItemId())
                            } else {
                                player.episodes(current.episode().id().itemId())
                            }
                        }.getOrDefault(emptyList())
                    }
                    val index = episodes.indexOfFirst { it.episode().id() == current.episode().id() }
                    if (index < 0) return@CrashSafeLaunchedEffect
                    episodeNeighbours = episodes.getOrNull(index - 1)?.episode()?.id() to
                        episodes.getOrNull(index + 1)?.episode()?.id()
                }
                val switchEpisode: (SourceEpisodeId) -> () -> Unit = { episodeId ->
                    {
                        val current = runCatching { playerController.snapshot() }.getOrNull()
                        if (current != null && !episodeSwitching) {
                            episodeSwitching = true
                            val request = PendingPlayerRequest(Any(), current.title())
                            pendingPlayer = request
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (presentation.details(current.libraryItemId()).isPresent) {
                                            player.open(current.libraryItemId(), episodeId)
                                        } else {
                                            player.open(current.title(), episodeId)
                                        }
                                    }
                                }
                                    .onSuccess { opened ->
                                        if (pendingPlayer?.token === request.token) {
                                            playerError = null
                                            pendingPlayer = null
                                            activePlayer = opened
                                        } else {
                                            // Superseded while opening: never install it.
                                            opened.close()
                                        }
                                    }
                                    .onFailure {
                                        if (pendingPlayer?.token === request.token) {
                                            pendingPlayer = null
                                            playerError = it.message
                                                ?: "The episode could not be opened."
                                        }
                                    }
                                episodeSwitching = false
                            }
                        }
                    }
                }
                PlayerSelectionScreen(
                    playerController,
                    playerFullscreen,
                    setPlayerFullscreen,
                    setPlayerActive,
                    nextEpisode = episodeNeighbours.first
                        ?.takeUnless { episodeSwitching }
                        ?.let(switchEpisode),
                    previousEpisode = episodeNeighbours.second
                        ?.takeUnless { episodeSwitching }
                        ?.let(switchEpisode),
                ) {
                    pendingPlayer = null
                    activePlayer = null
                }
            } else if (playerRequest != null) {
                PlayerLoadingScreen(playerRequest.title) {
                    pendingPlayer = null
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
                val openSourceReader: (String, SourceContentUnitId) -> Unit = { title, contentUnitId ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { reader.open(title, contentUnitId) }
                        }
                            .onSuccess {
                                readerError = null
                                activeReader = it
                            }
                            .onFailure { readerError = it.message ?: "The reader could not be opened." }
                    }
                }
                val enqueueDownload: (LibraryItemId) -> Unit = { id ->
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { downloads.enqueue(id) } }
                            .onSuccess { downloadError = null }
                            .onFailure { downloadError = it.message ?: "The download could not be queued." }
                    }
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
                val openSourcePlayer: (String, SourceEpisodeId) -> Unit = { title, episodeId ->
                    val request = PendingPlayerRequest(token = Any(), title = title)
                    pendingPlayer = request
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { player.open(title, episodeId) }
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
                        AppSection.BROWSE -> browseMainDestination && browseDetailsTitle == null
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
                        browseDetailsTitle,
                        { browseDetailsTitle = it },
                        componentCount,
                        navigate,
                        openSection,
                        openReader,
                        openSourceReader,
                        readerError,
                        openPlayer,
                        openSourcePlayer,
                        enqueueDownload,
                        downloadError,
                        { activeTrackingTitle = it },
                        moreDestination,
                        { moreDestination = it },
                        { moreDestination = null },
                    )
                }
                trackingTitle?.let { itemId ->
                    val details = presentation.details(itemId).orElse(null)
                    if (details == null) {
                        activeTrackingTitle = null
                    } else {
                        ModalBottomSheet(
                            onDismissRequest = { activeTrackingTitle = null },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            TitleTrackingScreen(
                                presentation = tracking,
                                browserRuntimeStatus = browserRuntimeStatus,
                                itemId = itemId,
                                title = details.title(),
                                kind = details.kind(),
                                goBack = { activeTrackingTitle = null },
                            )
                        }
                    }
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
internal fun AdaptiveShell(
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
    browseDetailsTitle: LibraryItemId?,
    browseDetailsChanged: (LibraryItemId?) -> Unit,
    componentCount: Int,
    navigate: ((LibraryNavigator) -> Unit) -> Unit,
    openSection: (AppSection) -> Unit,
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    openSourceReader: (String, SourceContentUnitId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    openSourcePlayer: (String, SourceEpisodeId) -> Unit,
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
                    componentCount,
                    navigate,
                    openSection,
                    openReader,
                    openSourceReader,
                    readerError,
                    openPlayer,
                    openSourcePlayer,
                    enqueueDownload,
                    downloadError,
                    openTracking,
                    moreDestination,
                    openMore,
                    closeMore,
                    browseDestinationChanged,
                    browseDetailsTitle,
                    browseDetailsChanged,
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
internal fun AnilibNavigationRail(
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
internal fun AnilibNavigationBar(
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
internal fun AppDestination(
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
    openReader: (LibraryItemId, SourceContentUnitId?) -> Unit,
    openSourceReader: (String, SourceContentUnitId) -> Unit,
    readerError: String?,
    openPlayer: (LibraryItemId, SourceEpisodeId?) -> Unit,
    openSourcePlayer: (String, SourceEpisodeId) -> Unit,
    enqueueDownload: (LibraryItemId) -> Unit,
    downloadError: String?,
    openTracking: (LibraryItemId) -> Unit,
    moreDestination: MoreDestination?,
    openMore: (MoreDestination) -> Unit,
    closeMore: () -> Unit,
    browseDestinationChanged: (Boolean) -> Unit,
    browseDetailsTitle: LibraryItemId?,
    browseDetailsChanged: (LibraryItemId?) -> Unit,
) {
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
                goBackOverride = null,
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
        AppSection.BROWSE -> Box(modifier = Modifier.fillMaxSize()) {
            DiscoveryScreen(
                discovery,
                presentation,
                extensionRepositories,
                apkExtensionPlatform,
                browserCookies,
                browserRuntimeStatus,
                openSourceReader = openSourceReader,
                openSourcePlayer = openSourcePlayer,
                openLibraryDetails = { browseDetailsChanged(it) },
                navigationVisibilityChanged = browseDestinationChanged,
                manageExtensions = {
                    openSection(AppSection.MORE)
                    openMore(MoreDestination.EXTENSION_REPOSITORIES)
                },
            )
            browseDetailsTitle?.let { libraryItemId ->
                Surface(modifier = Modifier.fillMaxSize()) {
                    DetailsDestination(
                        presentation,
                        discovery,
                        browserCookies,
                        browserRuntimeStatus,
                        detailPlatform,
                        reader,
                        player,
                        downloads,
                        tracking,
                        LibraryNavigator().apply { openDetails(libraryItemId) }.state(),
                        { transition ->
                            val overlayNavigator = LibraryNavigator().apply { openDetails(libraryItemId) }
                            transition(overlayNavigator)
                            browseDetailsChanged(overlayNavigator.state().selectedTitle().orElse(null))
                        },
                        openReader,
                        readerError,
                        openPlayer,
                        enqueueDownload,
                        downloadError,
                        openTracking,
                        goBackOverride = { browseDetailsChanged(null) },
                    )
                }
            }
        }
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

@Composable
internal fun EmptyPage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceholderPage(title: String, message: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal enum class AppSection {
    ANIME,
    MANGA,
    UPDATES,
    BROWSE,
    MORE,
}

internal fun AppSection.label(language: LanguagePack): String {
    val source = when (this) {
        AppSection.ANIME -> "ui.anime"
        AppSection.MANGA -> "ui.manga"
        AppSection.UPDATES -> "ui.updates"
        AppSection.BROWSE -> "ui.explore"
        AppSection.MORE -> "ui.more"
    }
    return UiTranslations.translate(source, language)
}

internal fun appColorScheme(settings: SettingsSnapshot, dark: Boolean): ColorScheme {
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

internal fun StartScreen.appSection(): AppSection = when (this) {
    StartScreen.LIBRARY -> AppSection.ANIME
    StartScreen.UPDATES -> AppSection.UPDATES
    StartScreen.HISTORY -> AppSection.MORE
    StartScreen.BROWSE -> AppSection.BROWSE
    StartScreen.MORE -> AppSection.MORE
}

internal fun AppSection.icon(): ImageVector = when (this) {
    AppSection.ANIME -> Icons.Default.PlayArrow
    AppSection.MANGA -> Icons.Default.CollectionsBookmark
    AppSection.UPDATES -> Icons.Default.NewReleases
    AppSection.BROWSE -> Icons.Default.Explore
    AppSection.MORE -> Icons.Default.MoreHoriz
}
