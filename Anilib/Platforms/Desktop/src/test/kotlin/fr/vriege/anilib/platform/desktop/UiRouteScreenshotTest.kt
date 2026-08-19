package fr.vriege.anilib.platform.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.applicationupdate.ApplicationArtifact
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification
import fr.vriege.anilib.feature.library.LibraryCapabilities
import fr.vriege.anilib.feature.library.LibraryItem
import fr.vriege.anilib.feature.library.LibraryOrigin
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.player.PlayerBackend
import fr.vriege.anilib.feature.player.PlayerMedia
import fr.vriege.anilib.feature.player.PlayerPlayback
import fr.vriege.anilib.feature.player.PlayerPlaybackSnapshot
import fr.vriege.anilib.feature.player.PlayerPlaybackStatus
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.feature.settings.SettingsCapabilities
import fr.vriege.anilib.feature.source.SourceContentKind
import fr.vriege.anilib.feature.source.CatalogueSource
import fr.vriege.anilib.feature.source.SourceBrowseRequest
import fr.vriege.anilib.feature.source.SourceDescriptor
import fr.vriege.anilib.feature.source.SourceExtensionPlugin
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.feature.source.SourcePage
import fr.vriege.anilib.feature.source.SourceSdk
import fr.vriege.anilib.feature.source.SourceSearchRequest
import fr.vriege.anilib.feature.source.WebSource
import fr.vriege.anilib.foundation.component.ComponentDescriptor
import fr.vriege.anilib.framework.http.jdk.JdkHttpTransport
import fr.vriege.anilib.platform.compose.ApplicationUpdatePlatformController
import fr.vriege.anilib.platform.compose.BackupImportPicker
import fr.vriege.anilib.platform.compose.BrowserDataClearResult
import fr.vriege.anilib.platform.compose.BrowserDataController
import fr.vriege.anilib.platform.compose.BrowserPlatformBridge
import fr.vriege.anilib.platform.compose.BrowserPlatformController
import fr.vriege.anilib.platform.compose.BrowserRuntimeStatus
import fr.vriege.anilib.platform.compose.ShareController
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import java.util.Base64
import java.util.Comparator
import java.util.Locale
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UiRouteScreenshotTest {
    @Test
    fun auditedNavigationRoutesAreStableOnCompactLayout() = verifyAuditedRoutes(480, 720)

    @Test
    fun auditedNavigationRoutesAreStableOnExpandedLayout() = verifyAuditedRoutes(1000, 720)

    private fun verifyAuditedRoutes(width: Int, height: Int) = runComposeUiTest {
        val directory = Files.createTempDirectory("anilib-ui-acceptance")
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        System.setProperty("anilib.theme", "light")
        try {
            prepareLocalContent(directory)
            StandardAnilib.start(
                directory,
                JdkHttpTransport(),
                AcceptancePlayerBackend(),
                listOf(
                    SourceExtensionPlugin(
                        ComponentDescriptor.of(
                            "acceptance.web-source",
                            "Acceptance web source",
                            "1.0.0",
                        ),
                        AcceptanceWebSource(),
                    ),
                ),
            ).use { started ->
                val settings = started.capability(SettingsCapabilities.SERVICE)
                settings.replace(settings.snapshot().withReducedMotion(true))
                val library = started.capability(LibraryCapabilities.CATALOG)
                library.save(
                    LibraryItem.create("Acceptance manga", MediaKind.MANGA)
                        .withOrigin(LibraryOrigin("anilib.local", "DIRECTORY:Acceptance manga")),
                )
                library.save(
                    LibraryItem.create("Acceptance anime", MediaKind.ANIME)
                        .withOrigin(
                            LibraryOrigin("anilib.local", "ANIME_SERIES:localanime/Acceptance anime"),
                        ),
                )
                setContent {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        DesktopAnilibContent(
                            started = started,
                            browserRuntimeStatus = BrowserRuntimeStatus.unavailable("Acceptance fixture"),
                            browserDataController = BrowserDataController {
                                BrowserDataClearResult(true, "Acceptance fixture cleared")
                            },
                            browserPlatformController = acceptanceBrowserController(),
                            backupImportPicker = acceptanceBackupPicker(),
                            applicationUpdatePlatformController = acceptanceUpdateController(),
                            shareController = object : ShareController {
                                override fun share(title: String, text: String) = Unit
                            },
                        )
                    }
                }

                captureStable("Anime library")
                visitPrimary("Manga")
                onNodeWithText("Acceptance manga").performClick()
                waitForContentDescription("Read")
                captureStable("Manga details with local chapters")
                onNodeWithContentDescription("Read").performClick()
                waitForContentDescription("Close reader")
                captureStable("Reader with decoded local page")
                onNodeWithContentDescription("Close reader").performClick()
                goBack()

                visitPrimary("Anime")
                onNodeWithText("Acceptance anime").performClick()
                waitForContentDescription("Watch")
                waitForText("First episode")
                captureStable("Anime details with local episodes")
                onNodeWithContentDescription("Watch").performClick()
                waitForText("No media backend is available.")
                captureStable("Player session")
                goBack()
                goBack()

                visitPrimary("Updates")
                onNodeWithText("Explore").performClick()
                captureStable("Explore landing and sources")
                onNodeWithText("Languages").performClick()
                waitForText("Source languages")
                onNodeWithText("Done").performClick()
                listOf(
                    "Anime sources",
                    "Manga sources",
                    "Anime extensions",
                    "Manga extensions",
                    "Migrate anime",
                    "Migrate manga",
                ).forEach { tab ->
                    onNodeWithText(tab).performClick()
                    captureStable(tab)
                    if (tab == "Anime extensions") {
                        onNodeWithText("Manage repositories").performClick()
                        waitForText("Extension repositories")
                        captureStable("Extension installation entry point")
                        goBack()
                        onNodeWithText("Explore").performClick()
                    }
                    if (tab == "Manga sources") {
                        onNodeWithText("Acceptance web").performClick()
                        waitForContentDescription("Open source website")
                        onNodeWithContentDescription("Open source website").performClick()
                        waitForText("WebView unavailable")
                        captureStable("WebView unavailable state")
                        onNodeWithContentDescription("Close browser").performClick()
                        goBack()
                        waitForText("Local library")
                        onNodeWithText("Local library").performClick()
                        waitForText("Acceptance catalogue manga")
                        captureStable("Local source catalogue")
                        goBack()
                    }
                }

                onNodeWithText("More").performClick()
                captureStable("More hub")
                visitMore("History", "History")
                visitMore("Download queue", "Downloads")
                visitMore("Backup and restore", "Backup and restore")
                visitMore("Tracking", "Tracking")
                visitMore("Categories", "Categories")
                visitMore("Statistics", "Statistics")
                visitMore("Extension repositories", "Extension repositories")

                scrollToText("Settings")
                onNodeWithText("Settings").performClick()
                captureStable("Settings home")
                listOf(
                    "General",
                    "Appearance",
                    "Content and privacy",
                    "Library and updates",
                    "Reader",
                    "Player",
                    "Downloads",
                    "Data and storage",
                ).forEach { destination ->
                    scrollToText(destination)
                    onAllNodes(hasText(destination) and hasClickAction()).onFirst().performClick()
                    captureStable("$destination settings")
                    goBack()
                }
                scrollToText("About")
                onNodeWithText("About").performClick()
                captureStable("About")
                goBack()
            }
        } finally {
            Locale.setDefault(previousLocale)
            System.clearProperty("anilib.theme")
            deleteTree(directory)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.visitPrimary(label: String) {
        onNodeWithText(label).performClick()
        captureStable(label)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.visitMore(label: String, snapshot: String) {
        scrollToText(label)
        onNodeWithText(label).performClick()
        captureStable(snapshot)
        goBack()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollToText(label: String) {
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(label) and hasClickAction())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.goBack() {
        val icon = onNodeWithContentDescription("Back")
        if (runCatching { icon.fetchSemanticsNode() }.isSuccess) {
            icon.performClick()
        } else {
            onNodeWithText("Back").performClick()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(label: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForContentDescription(label: String) {
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithContentDescription(label).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.captureStable(route: String) {
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText("Calculating statistics…")).fetchSemanticsNodes().isEmpty()
        }
        val root = onRoot(useUnmergedTree = true)
        var previousSemantics: String? = null
        var stablePolls = 0
        waitUntil(timeoutMillis = 5_000) {
            val currentSemantics = root.printToString(maxDepth = 80)
            if (currentSemantics == previousSemantics) {
                stablePolls++
            } else {
                previousSemantics = currentSemantics
                stablePolls = 0
            }
            stablePolls >= 10
        }
        val firstSemantics = root.printToString(maxDepth = 80)
        val firstImage = root.captureToImage()
        waitForIdle()
        val secondImage = root.captureToImage()
        assertEquals(firstImage.width, secondImage.width, "$route screenshot width changed")
        assertEquals(firstImage.height, secondImage.height, "$route screenshot height changed")
        assertContentEquals(
            firstImage.toPixelMap().buffer,
            secondImage.toPixelMap().buffer,
            "$route screenshot changed without an interaction",
        )
        assertEquals(firstSemantics, root.printToString(maxDepth = 80), "$route semantics changed")
    }

    @Composable
    private fun acceptanceBrowserController(): BrowserPlatformController =
        object : BrowserPlatformController {
            @Composable
            override fun rememberBridge(
                policy: BrowserPolicy,
                report: (String) -> Unit,
                interceptNavigation: (String) -> Boolean,
            ) = BrowserPlatformBridge(null) { }
        }

    private fun acceptanceBackupPicker(): BackupImportPicker = object : BackupImportPicker {
        override fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit) = Unit

        override fun export(path: Path, onExported: (String) -> Unit, onFailure: (String) -> Unit) = Unit

        override fun share(path: Path, onFailure: (String) -> Unit) = Unit

        override fun release(path: Path) = Unit
    }

    private fun acceptanceUpdateController(): ApplicationUpdatePlatformController =
        object : ApplicationUpdatePlatformController {
            override suspend fun download(artifact: ApplicationArtifact, progress: (Long) -> Unit): Path =
                error("Downloads are disabled in UI acceptance fixtures")

            override fun install(verification: ApplicationUpdateVerification) = Unit
        }

    private fun prepareLocalContent(directory: Path) {
        val image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val legacyManga = Files.createDirectories(
            directory.resolve("local-content").resolve("Acceptance manga"),
        )
        Files.write(legacyManga.resolve("001.png"), image)
        Files.write(legacyManga.resolve("002.png"), image)

        val catalogueManga = Files.createDirectories(
            directory.resolve("local-content").resolve("local")
                .resolve("Acceptance catalogue manga").resolve("chapter_1"),
        )
        Files.write(catalogueManga.resolve("001.png"), image)
        Files.writeString(
            catalogueManga.parent.resolve("details.json"),
            """{"title":"Acceptance catalogue manga","description":"Structured local fixture"}""",
        )
        Files.writeString(
            catalogueManga.parent.resolve("chapters.json"),
            """[{"chapter_number":1,"name":"Opening chapter"}]""",
        )

        val anime = Files.createDirectories(
            directory.resolve("local-content").resolve("localanime").resolve("Acceptance anime"),
        )
        Files.write(anime.resolve("ep01.mp4"), byteArrayOf(0))
        Files.writeString(
            anime.resolve("details.json"),
            """{"title":"Acceptance anime","description":"Structured local anime fixture"}""",
        )
        Files.writeString(
            anime.resolve("episodes.json"),
            """[{"episode_number":1,"name":"First episode"}]""",
        )
    }

    private class AcceptancePlayerBackend : PlayerBackend {
        override fun id() = "acceptance-player"

        override fun available() = true

        override fun open(media: PlayerMedia): PlayerPlayback = AcceptancePlayback(media)
    }

    private class AcceptanceWebSource : CatalogueSource, WebSource {
        private val descriptor = SourceDescriptor(
            SourceId.of("acceptance.web"),
            "Acceptance web",
            "1.0.0",
            "en",
            setOf(SourceContentKind.MANGA),
            SourceSdk.API_VERSION,
        )

        override fun descriptor() = descriptor

        override fun homePage(): URI = URI.create("https://acceptance.invalid/")

        override fun popular(request: SourceBrowseRequest) = SourcePage(emptyList(), false)

        override fun search(request: SourceSearchRequest) = SourcePage(emptyList(), false)
    }

    private class AcceptancePlayback(
        private val media: PlayerMedia,
    ) : PlayerPlayback {
        private var status = PlayerPlaybackStatus.PAUSED
        private var position = media.startPositionMillis()
        private var volume = 1f
        private var speed = 1f

        override fun media() = media

        override fun snapshot() = PlayerPlaybackSnapshot(
            status,
            position,
            60_000L,
            volume,
            speed,
            Optional.empty(),
        )

        override fun play() {
            status = PlayerPlaybackStatus.PLAYING
        }

        override fun pause() {
            status = PlayerPlaybackStatus.PAUSED
        }

        override fun seekTo(positionMillis: Long) {
            position = positionMillis.coerceIn(0L, 60_000L)
        }

        override fun setVolume(volume: Float) {
            this.volume = volume
        }

        override fun setPlaybackSpeed(speed: Float) {
            this.speed = speed
        }

        override fun selectSubtitle(subtitleId: Optional<String>) = Unit

        override fun close() = Unit
    }

    private fun deleteTree(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
