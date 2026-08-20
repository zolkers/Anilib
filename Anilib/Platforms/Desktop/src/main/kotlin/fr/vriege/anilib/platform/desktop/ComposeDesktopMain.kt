package fr.vriege.anilib.platform.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.covercache.bundle.CoverCachePlugin
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatforms
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.network.NetworkCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities
import fr.vriege.anilib.feature.settings.ApplicationWindowMode
import fr.vriege.anilib.feature.settings.SettingsCapabilities
import fr.vriege.anilib.feature.settings.PlayerWindowMode
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
import fr.vriege.anilib.feature.source.SourceCapabilities
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities
import fr.vriege.anilib.feature.updates.ui.UpdateUiCapabilities
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdateUiCapabilities
import fr.vriege.anilib.framework.http.jdk.JdkHttpTransport
import fr.vriege.anilib.kernel.StartedAnilib
import fr.vriege.anilib.platform.compose.AnilibApp
import fr.vriege.anilib.platform.compose.ApplicationUpdatePlatformController
import fr.vriege.anilib.platform.compose.BackupImportPicker
import fr.vriege.anilib.platform.compose.BrowserDataController
import fr.vriege.anilib.platform.compose.BrowserPlatformController
import fr.vriege.anilib.platform.compose.BrowserRuntimeStatus
import fr.vriege.anilib.platform.compose.ComposePlayerBackend
import fr.vriege.anilib.platform.compose.DesktopBrowserRuntime
import fr.vriege.anilib.platform.compose.ShareController
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.skia.Image

fun main(arguments: Array<String>) {
    val dataDirectory = DesktopDataDirectory.resolve()
    val transport = JdkHttpTransport()
    val extensionPlatform = DesktopApkExtensionPlatform.open(dataDirectory, transport)
    val plugins = listOf(CoverCachePlugin(dataDirectory.resolve("cache").resolve("covers")))
    val started = try {
        StandardAnilib.start(
            dataDirectory,
            transport,
            ComposePlayerBackend(),
            DesktopLibraryUpdateNotifier(),
            plugins,
        )
    } catch (failure: Throwable) {
        extensionPlatform.close()
        throw failure
    }
    extensionPlatform.attach(started)
    if (GraphicsEnvironment.isHeadless() || arguments.contains("--headless")) {
        printHeadlessSummary(started)
        try {
            started.close()
        } finally {
            extensionPlatform.close()
        }
        return
    }
    val browserRuntimeStatus = BrowserRuntimeStatus.deferred {
        DesktopBrowserRuntime.initialize(dataDirectory)
    }
    val crashShield = DesktopUiCrashShield.install(
        started.capability(SettingsCapabilities.DIAGNOSTICS),
    )
    val settingsService = started.capability(SettingsCapabilities.SERVICE)
    try {
        application {
            val initialApplicationWindowMode = settingsService.snapshot().applicationWindowMode()
            val windowState = rememberWindowState(placement = initialApplicationWindowMode.placement())
            val applicationWindowMode = remember { mutableStateOf(initialApplicationWindowMode) }
            val applicationFullscreen = remember { mutableStateOf(false) }
            val applicationPlacementBeforeFullscreen = remember { mutableStateOf(WindowPlacement.Floating) }
            val applicationModeBeforeApplicationFullscreen = remember { mutableStateOf(initialApplicationWindowMode) }
            val playerFullscreen = remember { mutableStateOf(false) }
            val playerActive = remember { mutableStateOf(false) }
            val playerWindowMode = remember { mutableStateOf(PlayerWindowMode.BORDERLESS) }
            val placementBeforeFullscreen = remember { mutableStateOf(WindowPlacement.Floating) }
            val applicationModeBeforeFullscreen = remember { mutableStateOf(initialApplicationWindowMode) }
            val setApplicationFullscreen: (Boolean) -> Unit = remember(windowState) {
                { fullscreen ->
                    if (fullscreen != applicationFullscreen.value) {
                        if (fullscreen) {
                            applicationPlacementBeforeFullscreen.value = windowState.placement
                            applicationModeBeforeApplicationFullscreen.value = applicationWindowMode.value
                            windowState.placement = WindowPlacement.Fullscreen
                        } else if (
                            applicationModeBeforeApplicationFullscreen.value == applicationWindowMode.value
                        ) {
                            windowState.placement = applicationPlacementBeforeFullscreen.value
                        } else {
                            windowState.placement = applicationWindowMode.value.placement()
                        }
                        applicationFullscreen.value = fullscreen
                    }
                }
            }
            val setPlayerFullscreen: (Boolean) -> Unit = remember(windowState) {
                { fullscreen ->
                    if (fullscreen != playerFullscreen.value) {
                        if (fullscreen) {
                            placementBeforeFullscreen.value = windowState.placement
                            applicationModeBeforeFullscreen.value = applicationWindowMode.value
                            playerWindowMode.value = settingsService.snapshot().playerWindowMode()
                            windowState.placement = when (playerWindowMode.value) {
                                PlayerWindowMode.WINDOWED -> windowState.placement
                                PlayerWindowMode.FULLSCREEN -> WindowPlacement.Fullscreen
                                PlayerWindowMode.BORDERLESS -> WindowPlacement.Maximized
                            }
                        } else if (applicationModeBeforeFullscreen.value == applicationWindowMode.value) {
                            windowState.placement = placementBeforeFullscreen.value
                        } else {
                            windowState.placement = applicationWindowMode.value.placement()
                        }
                        playerFullscreen.value = fullscreen
                    }
                }
            }
            DisposableEffect(settingsService, windowState) {
                val observation = settingsService.observe { settings ->
                    applicationWindowMode.value = settings.applicationWindowMode()
                    if (!playerFullscreen.value && !applicationFullscreen.value) {
                        windowState.placement = settings.applicationWindowMode().placement()
                    }
                }
                onDispose { observation.close() }
            }
            val closing = remember { AtomicBoolean() }
            val closeApplication: () -> Unit = remember {
                {
                    if (closing.compareAndSet(false, true)) {
                        try {
                            DesktopBrowserRuntime.dispose()
                        } finally {
                            try {
                                crashShield.close()
                            } finally {
                                try {
                                    started.close()
                                } finally {
                                    try {
                                        extensionPlatform.close()
                                    } finally {
                                        exitApplication()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val windowUndecorated = applicationFullscreen.value || if (playerFullscreen.value) {
                playerWindowMode.value == PlayerWindowMode.BORDERLESS
            } else {
                applicationWindowMode.value == ApplicationWindowMode.BORDERLESS
            }
            val content = remember {
                movableContentOf {
                    DesktopAnilibContent(
                        started = started,
                        browserRuntimeStatus = browserRuntimeStatus,
                        browserDataController = DesktopBrowserDataController(dataDirectory),
                        browserPlatformController = DesktopBrowserPlatformController(),
                        backupImportPicker = DesktopBackupImportPicker(),
                        applicationUpdatePlatformController = DesktopApplicationUpdateController(dataDirectory),
                        shareController = DesktopShareController(),
                        extensionPlatform = extensionPlatform,
                        playerFullscreen = playerFullscreen.value,
                        setPlayerFullscreen = setPlayerFullscreen,
                        setPlayerActive = { active -> playerActive.value = active },
                    )
                }
            }
            key(windowUndecorated) {
                Window(
                    onCloseRequest = closeApplication,
                    title = "Anilib",
                    state = windowState,
                    undecorated = windowUndecorated,
                    onPreviewKeyEvent = { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.F4 &&
                            event.isAltPressed
                        ) {
                            closeApplication()
                            true
                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                            if (applicationFullscreen.value) {
                                setApplicationFullscreen(false)
                            } else if (playerFullscreen.value || playerActive.value) {
                                setPlayerFullscreen(!playerFullscreen.value)
                            } else {
                                setApplicationFullscreen(true)
                            }
                            true
                        } else if (
                            playerFullscreen.value &&
                            event.key == Key.Escape &&
                            event.type == KeyEventType.KeyDown
                        ) {
                            setPlayerFullscreen(false)
                            true
                        } else if (
                            applicationFullscreen.value &&
                            event.key == Key.Escape &&
                            event.type == KeyEventType.KeyDown
                        ) {
                            setApplicationFullscreen(false)
                            true
                        } else if (
                            !playerFullscreen.value &&
                            !applicationFullscreen.value &&
                            applicationWindowMode.value == ApplicationWindowMode.BORDERLESS &&
                            event.key == Key.Escape &&
                            event.type == KeyEventType.KeyDown
                        ) {
                            settingsService.replace(
                                settingsService.snapshot().withApplicationWindowMode(ApplicationWindowMode.WINDOWED),
                            )
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    LaunchedEffect(window) {
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                    }
                    content()
                }
            }
        }
    } finally {
        crashShield.close()
    }
}

private fun ApplicationWindowMode.placement(): WindowPlacement = when (this) {
    ApplicationWindowMode.WINDOWED -> WindowPlacement.Floating
    ApplicationWindowMode.MAXIMIZED,
    ApplicationWindowMode.BORDERLESS,
    -> WindowPlacement.Maximized
}

@Composable
internal fun DesktopAnilibContent(
    started: StartedAnilib,
    browserRuntimeStatus: BrowserRuntimeStatus,
    browserDataController: BrowserDataController,
    browserPlatformController: BrowserPlatformController,
    backupImportPicker: BackupImportPicker,
    applicationUpdatePlatformController: ApplicationUpdatePlatformController,
    shareController: ShareController,
    extensionPlatform: ApkExtensionPlatform = ApkExtensionPlatforms.unavailable(),
    playerFullscreen: Boolean = false,
    setPlayerFullscreen: (Boolean) -> Unit = {},
    setPlayerActive: (Boolean) -> Unit = {},
) {
    AnilibApp(
        presentation = started.capability(LibraryUiCapabilities.PRESENTATION),
        discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION),
        extensionRepositories = started.capability(ExtensionRepositoryUiCapabilities.PRESENTATION),
        apkExtensionPlatform = extensionPlatform,
        networkMaintenance = started.capability(NetworkCapabilities.MAINTENANCE),
        browserCookies = started.capability(NetworkCapabilities.COOKIES),
        browserRuntimeStatus = browserRuntimeStatus,
        browserDataController = browserDataController,
        browserPlatformController = browserPlatformController,
        settingsPresentation = started.capability(SettingsUiCapabilities.PRESENTATION),
        reader = started.capability(ReaderUiCapabilities.PRESENTATION),
        player = started.capability(PlayerUiCapabilities.PRESENTATION),
        downloads = started.capability(DownloadUiCapabilities.PRESENTATION),
        backup = started.capability(BackupUiCapabilities.PRESENTATION),
        backupImportPicker = backupImportPicker,
        tracking = started.capability(TrackerUiCapabilities.PRESENTATION),
        updates = started.capability(UpdateUiCapabilities.PRESENTATION),
        applicationUpdates = started.capability(ApplicationUpdateUiCapabilities.PRESENTATION),
        applicationUpdatePlatformController = applicationUpdatePlatformController,
        httpClient = started.capability(NetworkCapabilities.HTTP_CLIENT),
        shareController = shareController,
        pageDecoder = ::decodePage,
        applyReaderOrientationPolicy = {},
        applyPlayerOrientationPolicy = {},
        requestPlayerPictureInPicture = {},
        playerFullscreen = playerFullscreen,
        setPlayerFullscreen = setPlayerFullscreen,
        setPlayerActive = setPlayerActive,
        setPlayerBackgroundAudio = {},
        enableAndroidPlayerControls = false,
        enableDesktopPlayerControls = true,
        componentCount = started.components().size,
        darkTheme = desktopDarkTheme(),
        reportUiFailure = { failure ->
            runCatching {
                started.capability(SettingsCapabilities.DIAGNOSTICS).recordCrash(
                    "Recovered Compose UI failure",
                    failure.stackTraceToString(),
                )
            }
        },
    )
}

private fun decodePage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

private fun printHeadlessSummary(started: StartedAnilib) {
    val count = started.capability(LibraryUiCapabilities.PRESENTATION).library().titles().size
    val sources = started.capability(SourceCapabilities.REGISTRY).sources()
    println(
        "Anilib started headlessly with ${started.components().size} bundles, " +
            "${sources.size} sources (${sources.joinToString { it.descriptor().id().toString() }}), " +
            "and $count library items.",
    )
}

@Composable
private fun desktopDarkTheme(): Boolean {
    val theme = System.getProperty("anilib.theme", "system")
    if (theme.equals("dark", ignoreCase = true)) {
        return true
    }
    if (theme.equals("light", ignoreCase = true)) {
        return false
    }
    return isSystemInDarkTheme()
}
