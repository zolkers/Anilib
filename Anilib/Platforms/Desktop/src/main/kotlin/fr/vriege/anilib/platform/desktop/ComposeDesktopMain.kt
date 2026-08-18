package fr.vriege.anilib.platform.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.covercache.bundle.CoverCachePlugin
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatforms
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.network.NetworkCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
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
import org.jetbrains.skia.Image

fun main() {
    val dataDirectory = DesktopDataDirectory.resolve()
    val started = StandardAnilib.start(
        dataDirectory,
        JdkHttpTransport(),
        ComposePlayerBackend(),
        DesktopLibraryUpdateNotifier(),
        listOf(CoverCachePlugin(dataDirectory.resolve("cache").resolve("covers"))),
    )
    if (GraphicsEnvironment.isHeadless()) {
        printHeadlessSummary(started)
        started.close()
        return
    }
    val browserRuntimeStatus = DesktopBrowserRuntime.initialize(dataDirectory)
    application {
        Window(
            onCloseRequest = {
                try {
                    DesktopBrowserRuntime.dispose()
                } finally {
                    try {
                        started.close()
                    } finally {
                        exitApplication()
                    }
                }
            },
            title = "Anilib",
        ) {
            DesktopAnilibContent(
                started = started,
                browserRuntimeStatus = browserRuntimeStatus,
                browserDataController = DesktopBrowserDataController(dataDirectory),
                browserPlatformController = DesktopBrowserPlatformController(),
                backupImportPicker = DesktopBackupImportPicker(),
                applicationUpdatePlatformController = DesktopApplicationUpdateController(dataDirectory),
                shareController = DesktopShareController(),
            )
        }
    }
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
) {
    AnilibApp(
        presentation = started.capability(LibraryUiCapabilities.PRESENTATION),
        discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION),
        extensionRepositories = started.capability(ExtensionRepositoryUiCapabilities.PRESENTATION),
        apkExtensionPlatform = ApkExtensionPlatforms.unavailable(),
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
        setPlayerActive = {},
        setPlayerBackgroundAudio = {},
        enableAndroidPlayerControls = false,
        enableDesktopPlayerControls = true,
        componentCount = started.components().size,
        darkTheme = desktopDarkTheme(),
    )
}

private fun decodePage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

private fun printHeadlessSummary(started: StartedAnilib) {
    val count = started.capability(LibraryUiCapabilities.PRESENTATION).library().titles().size
    println(
        "Anilib started headlessly with ${started.components().size} bundles and $count library items.",
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
