package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.feature.player.PlayerMedia
import fr.vriege.anilib.feature.source.SourceStreamFormat
import fr.vriege.anilib.feature.source.SourceVideoStream
import fr.vriege.anilib.platform.compose.ComposePlayerBackend
import java.net.URI
import java.nio.file.Files
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposePlayerBackendLocalFileTest {
    @Test
    fun `local file uri is converted to the native desktop path`() {
        val file = Files.createTempFile("anilib-player", ".mp4")
        val playback = ComposePlayerBackend().open(media(file.toUri()))
        try {
            val location = playback.javaClass.getDeclaredField("mediaLocation").run {
                isAccessible = true
                get(playback) as String
            }

            assertEquals(file.toString(), location)
        } finally {
            playback.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `remote uri remains unchanged`() {
        val location = URI.create("https://media.example/video.mp4")
        val playback = ComposePlayerBackend().open(media(location))
        try {
            val routed = playback.javaClass.getDeclaredField("mediaLocation").run {
                isAccessible = true
                get(playback) as String
            }

            assertEquals(location.toString(), routed)
        } finally {
            playback.close()
        }
    }

    @Test
    fun `protected HLS uri keeps its playlist extension through the relay`() {
        val playback = ComposePlayerBackend().open(
            PlayerMedia(
                "Protected episode",
                SourceVideoStream(
                    "protected",
                    "1080p",
                    URI.create("https://media.example/master.m3u8?token=secret"),
                    SourceStreamFormat.HLS,
                    mapOf("Referer" to "https://source.example/"),
                    emptyList(),
                ),
                Optional.empty(),
                0L,
            ),
        )
        try {
            val routed = playback.javaClass.getDeclaredField("mediaLocation").run {
                isAccessible = true
                get(playback) as String
            }

            assertTrue(routed.startsWith("http://127.0.0.1:"))
            assertTrue(URI.create(routed).path.endsWith(".m3u8"))
        } finally {
            playback.close()
        }
    }

    private fun media(location: URI) = PlayerMedia(
        "Offline episode",
        SourceVideoStream(
            "offline",
            "Offline",
            location,
            SourceStreamFormat.PROGRESSIVE,
            emptyMap(),
            emptyList(),
        ),
        Optional.empty(),
        0L,
    )
}
