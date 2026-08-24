package fr.vriege.anilib.platform.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopFfmpegVideoFinalizerTest {
    @Test
    fun `progressive media omits hls-only input options`() {
        val command = DesktopFfmpegVideoFinalizer.finalizationCommand(
            Path.of("ffmpeg"),
            Path.of("offline.media"),
            Path.of("offline.partial.mp4"),
        )

        assertFalse(command.contains("-allowed_extensions"))
        assertFalse(command.contains("-protocol_whitelist"))
        assertInputAndOutput(command, "offline.media", "offline.partial.mp4")
    }

    @Test
    fun `offline hls keeps the local playlist options`() {
        val command = DesktopFfmpegVideoFinalizer.finalizationCommand(
            Path.of("ffmpeg"),
            Path.of("offline.m3u8"),
            Path.of("offline.partial.mp4"),
        )

        assertContains(command, "-allowed_extensions")
        assertContains(command, "-protocol_whitelist")
        assertInputAndOutput(command, "offline.m3u8", "offline.partial.mp4")
    }

    private fun assertInputAndOutput(command: List<String>, input: String, output: String) {
        val inputOption = command.indexOf("-i")
        assertEquals(input, command[inputOption + 1])
        assertEquals(output, command.last())
    }
}
