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
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
import fr.vriege.anilib.framework.http.jdk.JdkHttpTransport
import fr.vriege.anilib.kernel.StartedAnilib
import fr.vriege.anilib.platform.compose.AnilibApp
import java.awt.GraphicsEnvironment
import org.jetbrains.skia.Image

fun main() {
    val dataDirectory = DesktopDataDirectory.resolve()
    val started = StandardAnilib.start(
        dataDirectory,
        JdkHttpTransport(),
        listOf(CoverCachePlugin(dataDirectory.resolve("cache").resolve("covers"))),
    )
    if (GraphicsEnvironment.isHeadless()) {
        printHeadlessSummary(started)
        started.close()
        return
    }
    val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
    val discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION)
    val reader = started.capability(ReaderUiCapabilities.PRESENTATION)
    val downloads = started.capability(DownloadUiCapabilities.PRESENTATION)
    val backup = started.capability(BackupUiCapabilities.PRESENTATION)
    application {
        Window(
            onCloseRequest = {
                started.close()
                exitApplication()
            },
            title = "Anilib",
        ) {
            AnilibApp(
                presentation = presentation,
                discovery = discovery,
                reader = reader,
                downloads = downloads,
                backup = backup,
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
