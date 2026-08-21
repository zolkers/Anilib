package fr.vriege.anilib.platform.desktop

import androidx.compose.ui.window.WindowPlacement
import fr.vriege.anilib.feature.settings.ApplicationWindowMode
import fr.vriege.anilib.feature.settings.PlayerWindowMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun `player fullscreen exits when the application stays unfocused`() {
        assertTrue(shouldExitPlayerFullscreen(playerFullscreen = true, windowFocused = false))
        assertFalse(shouldExitPlayerFullscreen(playerFullscreen = true, windowFocused = true))
        assertFalse(shouldExitPlayerFullscreen(playerFullscreen = false, windowFocused = false))
    }

    @Test
    fun `player fullscreen keeps the window decoration stable`() {
        ApplicationWindowMode.entries.forEach { applicationMode ->
            val before = windowUndecorated(
                applicationFullscreen = false,
                playerFullscreen = false,
                applicationWindowMode = applicationMode,
            )
            val during = windowUndecorated(
                applicationFullscreen = false,
                playerFullscreen = true,
                applicationWindowMode = applicationMode,
            )

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
    fun `application fullscreen still controls decoration outside the player`() {
        assertFalse(
            windowUndecorated(false, false, ApplicationWindowMode.WINDOWED),
        )
        assertTrue(
            windowUndecorated(true, false, ApplicationWindowMode.WINDOWED),
        )
    }
}
