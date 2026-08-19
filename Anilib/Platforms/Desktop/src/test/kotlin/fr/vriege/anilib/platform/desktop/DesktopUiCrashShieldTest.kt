package fr.vriege.anilib.platform.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUiCrashShieldTest {
    @Test
    fun onlyDesktopUiThreadsAreRecovered() {
        assertTrue(
            DesktopUiCrashShield.recoverableUiThread(Thread({ }, "AWT-EventQueue-0")),
        )
        assertTrue(
            DesktopUiCrashShield.recoverableUiThread(Thread({ }, "Skiko Render Thread")),
        )
        assertFalse(
            DesktopUiCrashShield.recoverableUiThread(Thread({ }, "extension-engine-worker")),
        )
    }
}
