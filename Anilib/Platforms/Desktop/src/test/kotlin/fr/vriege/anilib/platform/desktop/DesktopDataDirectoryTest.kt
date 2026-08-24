package fr.vriege.anilib.platform.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDataDirectoryTest {
    @Test
    fun `legacy installer files are excluded while user data is merged`() {
        val local = Files.createTempDirectory("anilib-data-migration")
        try {
            val legacy = Files.createDirectories(local.resolve("Anilib"))
            Files.createDirectories(legacy.resolve("app")).resolve("runtime.jar").writeText("program")
            Files.createDirectories(legacy.resolve("runtime")).resolve("java.exe").writeText("program")
            legacy.resolve("Anilib.exe").writeText("program")
            legacy.resolve("library.anilib").writeText("library")
            Files.createDirectories(legacy.resolve("extensions")).resolve("source.apk").writeText("extension")

            val durable = Files.createDirectories(local.resolve("AnilibData"))
            val newerSettings = durable.resolve("settings.properties")
            newerSettings.writeText("newer")
            Files.setLastModifiedTime(newerSettings, FileTime.fromMillis(System.currentTimeMillis() + 10_000))
            legacy.resolve("settings.properties").writeText("legacy")

            assertEquals(durable, DesktopDataDirectory.migrateLegacyWindowsData(local))
            assertEquals("library", durable.resolve("library.anilib").readText())
            assertEquals("extension", durable.resolve("extensions/source.apk").readText())
            assertEquals("newer", newerSettings.readText())
            assertFalse(Files.exists(durable.resolve("app")))
            assertFalse(Files.exists(durable.resolve("runtime")))
            assertFalse(Files.exists(durable.resolve("Anilib.exe")))
            assertTrue(Files.exists(legacy.resolve("library.anilib")))
        } finally {
            deleteTree(local)
        }
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
