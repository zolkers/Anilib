package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.platform.compose.BrowserDataClearResult
import fr.vriege.anilib.platform.compose.BrowserDataController
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Schedules locked KCEF profile data for safe removal before its next initialization. */
internal class DesktopBrowserDataController(dataDirectory: Path) : BrowserDataController {
    private val browserDirectory = dataDirectory.toAbsolutePath().normalize().resolve("browser")
    private val marker = browserDirectory.resolve("clear-data-on-start")

    override fun clearData(): BrowserDataClearResult = runCatching {
        Files.createDirectories(browserDirectory)
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                "Browser cleanup marker is not a regular file"
            }
        } else {
            Files.createFile(marker)
        }
    }.fold(
        onSuccess = {
            BrowserDataClearResult(
                true,
                "Desktop WebView cache and site storage will be cleared on the next launch.",
            )
        },
        onFailure = { failure ->
            BrowserDataClearResult(
                false,
                failure.message ?: "Desktop WebView cleanup could not be scheduled.",
            )
        },
    )
}
