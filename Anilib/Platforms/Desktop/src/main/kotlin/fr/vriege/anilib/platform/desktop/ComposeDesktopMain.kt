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
import fr.vriege.anilib.platform.compose.ComposePlayerBackend
import fr.vriege.anilib.platform.compose.DesktopBrowserRuntime
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
    val browserDataController = DesktopBrowserDataController(dataDirectory)
    val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
    val discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION)
    val extensionRepositories = started.capability(ExtensionRepositoryUiCapabilities.PRESENTATION)
    val reader = started.capability(ReaderUiCapabilities.PRESENTATION)
    val player = started.capability(PlayerUiCapabilities.PRESENTATION)
    val downloads = started.capability(DownloadUiCapabilities.PRESENTATION)
    val backup = started.capability(BackupUiCapabilities.PRESENTATION)
    val tracking = started.capability(TrackerUiCapabilities.PRESENTATION)
    val updates = started.capability(UpdateUiCapabilities.PRESENTATION)
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
            AnilibApp(
                presentation = presentation,
                discovery = discovery,
                extensionRepositories = extensionRepositories,
                apkExtensionPlatform = ApkExtensionPlatforms.unavailable(),
                networkMaintenance = started.capability(NetworkCapabilities.MAINTENANCE),
                browserCookies = started.capability(NetworkCapabilities.COOKIES),
                browserRuntimeStatus = browserRuntimeStatus,
                browserDataController = browserDataController,
                settingsPresentation = started.capability(SettingsUiCapabilities.PRESENTATION),
                reader = reader,
                player = player,
                downloads = downloads,
                backup = backup,
                backupImportPicker = DesktopBackupImportPicker(),
                tracking = tracking,
                updates = updates,
                applicationUpdates = started.capability(ApplicationUpdateUiCapabilities.PRESENTATION),
                httpClient = started.capability(NetworkCapabilities.HTTP_CLIENT),
                shareController = DesktopShareController(),
                pageDecoder = ::decodePage,
                componentCount = started.components().size,
                darkTheme = desktopDarkTheme(),
            )
        }
    }
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
