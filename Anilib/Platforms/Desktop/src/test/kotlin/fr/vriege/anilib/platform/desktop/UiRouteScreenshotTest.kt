package fr.vriege.anilib.platform.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.pinch
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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import java.util.Comparator
import java.util.Locale
import java.util.Optional
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.test.ComposeUiTest

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
                        .withFavorite(true)
                        .withOrigin(LibraryOrigin("anilib.local", "DIRECTORY:Acceptance manga")),
                )
                library.save(
                    LibraryItem.create("Acceptance anime", MediaKind.ANIME)
                        .withFavorite(true)
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
                assertReaderZoomInputs()
                onNodeWithContentDescription("Show reader controls").performClick()
                onNodeWithContentDescription("Reading mode").performClick()
                onNodeWithText("Vertical").performClick()
                waitForContentDescription("Page 1")
                assertContinuousPageFillsWidth("Vertical reader")
                onNodeWithContentDescription("Reading mode").performClick()
                onNodeWithText("Webtoon").performClick()
                waitForContentDescription("Page 1")
                assertContinuousPageFillsWidth("Webtoon reader")
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
                        onNodeWithText("Acceptance catalogue manga").performClick()
                        waitForContentDescription("Read")
                        captureStable("Source title canonical details")
                        goBack()
                        waitForText("Acceptance catalogue manga")
                        captureStable("Local source catalogue restored after details")
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
                    if (destination == "Appearance") {
                        repeat(3) {
                            onAllNodes(hasText("Navigation") and hasClickAction()).onFirst().performClick()
                            waitForIdle()
                            onNodeWithText("Appearance").fetchSemanticsNode()
                        }
                    }
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

    private fun ComposeUiTest.visitPrimary(label: String) {
        onNodeWithText(label).performClick()
        captureStable(label)
    }

    private fun ComposeUiTest.visitMore(label: String, snapshot: String) {
        scrollToText(label)
        onNodeWithText(label).performClick()
        captureStable(snapshot)
        goBack()
    }

    private fun ComposeUiTest.scrollToText(label: String) {
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(label) and hasClickAction())
    }

    private fun ComposeUiTest.goBack() {
        val icon = onNodeWithContentDescription("Back")
        if (runCatching { icon.fetchSemanticsNode() }.isSuccess) {
            icon.performClick()
        } else {
            onNodeWithText("Back").performClick()
        }
    }

    private fun ComposeUiTest.waitForText(label: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.waitForContentDescription(label: String) {
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithContentDescription(label).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun ComposeUiTest.captureStable(route: String) {
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

    private fun ComposeUiTest.assertContinuousPageFillsWidth(route: String) {
        waitForIdle()
        val image = onRoot(useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val y = (image.height * 0.45f).toInt()
        val filledSamples = listOf(0.25f, 0.5f, 0.75f).count { fraction ->
            val color = pixels[(image.width * fraction).toInt(), y]
            color.alpha > 0.8f && color.red + color.green + color.blue > 1.5f
        }
        assertTrue(filledSamples >= 2, "$route left a viewport-sized gap around an original-size page")
    }

    private fun ComposeUiTest.assertReaderZoomInputs() {
        val canvas = onNodeWithTag("reader-canvas", useUnmergedTree = true)
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
        canvas.performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { updatePointerTo(center); scroll(-1f) }
            key { keyUp(Key.CtrlLeft) }
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "112%"))
        canvas.performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { scroll(1f) }
            key { keyUp(Key.CtrlLeft) }
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
        canvas.performMultiModalInput {
            touch {
                pinch(
                    start0 = center + Offset(-24f, 0f),
                    end0 = center + Offset(-96f, 0f),
                    start1 = center + Offset(24f, 0f),
                    end1 = center + Offset(96f, 0f),
                )
            }
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "400%"))
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
        val bitmap = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) bitmap.setRGB(x, y, 0xFFFFFF)
        }
        val encoded = ByteArrayOutputStream()
        ImageIO.write(bitmap, "png", encoded)
        val image = encoded.toByteArray()
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
        Files.writeString(directory.resolve("reader-display.properties"), "scaleMode=ORIGINAL\n")
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
