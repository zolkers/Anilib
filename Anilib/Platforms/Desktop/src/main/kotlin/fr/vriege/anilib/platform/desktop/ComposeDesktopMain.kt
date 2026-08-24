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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer
import org.jetbrains.skia.Image

fun main(arguments: Array<String>) {
    val dataDirectory = DesktopDataDirectory.resolve()
    val headless = GraphicsEnvironment.isHeadless() || arguments.contains("--headless")
    val startupSplash = if (headless) null else DesktopStartupSplash.open()
    val transport = JdkHttpTransport()
    val extensionPlatform = try {
        DesktopApkExtensionPlatform.open(dataDirectory, transport)
    } catch (failure: Throwable) {
        startupSplash?.close()
        throw failure
    }
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
        startupSplash?.close()
        extensionPlatform.close()
        throw failure
    }
    extensionPlatform.attach(started)
    if (headless) {
        printHeadlessSummary(started)
        try {
            started.close()
        } finally {
            extensionPlatform.close()
        }
        return
    }
    val browserRuntime = DesktopBrowserRuntime
    val browserRuntimeStatus = BrowserRuntimeStatus.deferred {
        browserRuntime.initialize(dataDirectory)
    }
    val crashShield = DesktopUiCrashShield.install(
        started.capability(SettingsCapabilities.DIAGNOSTICS),
    )
    val settingsService = started.capability(SettingsCapabilities.SERVICE)
    startupSplash?.setReducedMotion(settingsService.snapshot().reducedMotion())
    try {
        application {
            val applicationIcon = remember { loadApplicationIcon() }
            val initialApplicationWindowMode = settingsService.snapshot().applicationWindowMode()
            val windowState = rememberWindowState(placement = initialApplicationWindowMode.placement())
            val intendedWindowPlacement = remember {
                mutableStateOf(initialApplicationWindowMode.placement())
            }
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
                            intendedWindowPlacement.value = WindowPlacement.Fullscreen
                        } else if (
                            applicationModeBeforeApplicationFullscreen.value == applicationWindowMode.value
                        ) {
                            intendedWindowPlacement.value = applicationPlacementBeforeFullscreen.value
                        } else {
                            intendedWindowPlacement.value = applicationWindowMode.value.placement()
                        }
                        windowState.placement = intendedWindowPlacement.value
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
                            intendedWindowPlacement.value = playerWindowMode.value.placement(
                                current = windowState.placement,
                                applicationUndecorated =
                                    applicationWindowMode.value == ApplicationWindowMode.BORDERLESS,
                            )
                        } else if (applicationModeBeforeFullscreen.value == applicationWindowMode.value) {
                            intendedWindowPlacement.value = placementBeforeFullscreen.value
                        } else {
                            intendedWindowPlacement.value = applicationWindowMode.value.placement()
                        }
                        windowState.placement = intendedWindowPlacement.value
                        playerFullscreen.value = fullscreen
                    }
                }
            }
            DisposableEffect(settingsService, windowState) {
                val observation = settingsService.observe { settings ->
                    applicationWindowMode.value = settings.applicationWindowMode()
                    if (!playerFullscreen.value && !applicationFullscreen.value) {
                        intendedWindowPlacement.value = settings.applicationWindowMode().placement()
                        windowState.placement = intendedWindowPlacement.value
                    }
                }
                onDispose { observation.close() }
            }
            val closing = remember { AtomicBoolean() }
            val closeApplication: () -> Unit = remember {
                {
                    if (closing.compareAndSet(false, true)) {
                        try {
                            browserRuntime.dispose()
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
            val windowUndecorated = windowUndecorated(
                applicationFullscreen = applicationFullscreen.value,
                playerFullscreen = playerFullscreen.value,
                applicationWindowMode = applicationWindowMode.value,
            )
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
                    icon = applicationIcon,
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
                    DisposableEffect(window) {
                        var pendingExit: Timer? = null
                        val listener = object : WindowAdapter() {
                            override fun windowLostFocus(event: WindowEvent) {
                                if (!playerFullscreen.value) return
                                pendingExit?.stop()
                                pendingExit = Timer(PLAYER_FULLSCREEN_FOCUS_GRACE_MILLIS) {
                                    if (shouldExitPlayerFullscreen(playerFullscreen.value, window.isFocused)) {
                                        setPlayerFullscreen(false)
                                    }
                                }.apply {
                                    isRepeats = false
                                    start()
                                }
                            }

                            override fun windowGainedFocus(event: WindowEvent) {
                                pendingExit?.stop()
                                pendingExit = null
                            }
                        }
                        window.addWindowFocusListener(listener)
                        onDispose {
                            pendingExit?.stop()
                            window.removeWindowFocusListener(listener)
                        }
                    }
                    LaunchedEffect(window) {
                        windowState.placement = intendedWindowPlacement.value
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                        startupSplash?.close()
                    }
                    content()
                }
            }
        }
    } finally {
        startupSplash?.close()
        crashShield.close()
    }
}

private const val PLAYER_FULLSCREEN_FOCUS_GRACE_MILLIS = 150

internal fun shouldExitPlayerFullscreen(
    playerFullscreen: Boolean,
    windowFocused: Boolean,
): Boolean = playerFullscreen && !windowFocused

private fun ApplicationWindowMode.placement(): WindowPlacement = when (this) {
    ApplicationWindowMode.WINDOWED -> WindowPlacement.Floating
    ApplicationWindowMode.MAXIMIZED,
    ApplicationWindowMode.BORDERLESS,
    -> WindowPlacement.Maximized
}

internal fun PlayerWindowMode.placement(
    current: WindowPlacement,
    applicationUndecorated: Boolean,
): WindowPlacement = when (this) {
    PlayerWindowMode.WINDOWED -> current
    PlayerWindowMode.FULLSCREEN -> WindowPlacement.Fullscreen
    PlayerWindowMode.BORDERLESS -> if (applicationUndecorated) {
        WindowPlacement.Maximized
    } else {
        WindowPlacement.Fullscreen
    }
}

internal fun windowUndecorated(
    applicationFullscreen: Boolean,
    playerFullscreen: Boolean,
    applicationWindowMode: ApplicationWindowMode,
): Boolean = if (playerFullscreen) {
    // This value keys the Window. Keep it stable while video is active so its native surface survives.
    applicationWindowMode == ApplicationWindowMode.BORDERLESS
} else {
    applicationFullscreen || applicationWindowMode == ApplicationWindowMode.BORDERLESS
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
        playerFullscreen = playerFullscreen,
        setPlayerFullscreen = setPlayerFullscreen,
        setPlayerActive = setPlayerActive,
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

private fun loadApplicationIcon(): Painter? = runCatching {
    val loader = Thread.currentThread().contextClassLoader
    val bytes = requireNotNull(loader.getResourceAsStream("assets/anilib-icon.png")) {
        "Anilib application icon is missing"
    }.use { it.readAllBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

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
