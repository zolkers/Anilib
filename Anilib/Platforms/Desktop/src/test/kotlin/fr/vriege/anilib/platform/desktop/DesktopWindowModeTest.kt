package fr.vriege.anilib.platform.desktop

import androidx.compose.ui.window.WindowPlacement
import fr.vriege.anilib.feature.settings.ApplicationWindowMode
import fr.vriege.anilib.feature.settings.PlayerWindowMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun `desktop application icon is packaged`() {
        assertNotNull(javaClass.getResource("/assets/anilib-icon.png"))
    }

    @Test
    fun `player fullscreen keeps the window decoration stable`() {
        ApplicationWindowMode.entries.forEach { applicationMode ->
            val before = windowUndecorated(applicationMode)
            val during = windowUndecorated(applicationMode)

            assertEquals(before, during, applicationMode.name)
        }
    }

    @Test
    fun `borderless player avoids a decorated maximized window`() {
        assertEquals(
            WindowPlacement.Fullscreen,
            PlayerWindowMode.BORDERLESS.placement(
                current = WindowPlacement.Floating,
                applicationUndecorated = false,
            ),
        )
        assertEquals(
            WindowPlacement.Maximized,
            PlayerWindowMode.BORDERLESS.placement(
                current = WindowPlacement.Maximized,
                applicationUndecorated = true,
            ),
        )
    }

    @Test
    fun `escape never changes the application window while video is active`() {
        assertFalse(
            shouldExitApplicationBorderless(
                playerFullscreen = false,
                playerActive = true,
                applicationWindowMode = ApplicationWindowMode.BORDERLESS,
            ),
        )
        assertTrue(
            shouldExitApplicationBorderless(
                playerFullscreen = false,
                playerActive = false,
                applicationWindowMode = ApplicationWindowMode.BORDERLESS,
            ),
        )
    }
}
