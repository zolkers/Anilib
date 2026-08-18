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
import fr.vriege.anilib.feature.library.MediaKind
import fr.vriege.anilib.feature.settings.BrowserPolicy
import fr.vriege.anilib.feature.settings.SettingsCapabilities
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
import java.util.Comparator
import java.util.Locale
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
            StandardAnilib.start(directory).use { started ->
                val settings = started.capability(SettingsCapabilities.SERVICE)
                settings.replace(settings.snapshot().withReducedMotion(true))
                started.capability(LibraryCapabilities.CATALOG).save(
                    LibraryItem.create("Acceptance title", MediaKind.MANGA),
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

                captureStable("Library")
                onNodeWithText("Acceptance title").performClick()
                captureStable("Anime and manga details")
                goBack()

                visitPrimary("Updates")
                visitPrimary("History")
                onNodeWithText("Browse").performClick()
                captureStable("Browse landing and sources")
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
                }

                onNodeWithText("More").performClick()
                captureStable("More hub")
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
                goBack()
                visitMore("About", "About")
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

    private fun androidx.compose.ui.test.ComposeUiTest.captureStable(route: String) {
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText("Calculating statistics…")).fetchSemanticsNodes().isEmpty()
        }
        val root = onRoot(useUnmergedTree = true)
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

    private fun deleteTree(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
