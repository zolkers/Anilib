package fr.vriege.anilib.platform.android

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport
import fr.vriege.anilib.platform.compose.AnilibApp
import fr.vriege.anilib.platform.compose.ComposePlayerBackend

/** Android launcher for the shared Anilib product and adaptive Compose shell. */
class MainActivity : ComponentActivity() {
    private var product: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val started = StandardAnilib.start(
            filesDir.toPath(),
            UrlConnectionHttpTransport(),
            ComposePlayerBackend(),
            emptyList(),
        )
        product = started
        val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
        val discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION)
        val reader = started.capability(ReaderUiCapabilities.PRESENTATION)
        val player = started.capability(PlayerUiCapabilities.PRESENTATION)
        val downloads = started.capability(DownloadUiCapabilities.PRESENTATION)
        val backup = started.capability(BackupUiCapabilities.PRESENTATION)
        val componentCount = started.components().size
        setContent {
            AnilibApp(
                presentation = presentation,
                discovery = discovery,
                reader = reader,
                player = player,
                downloads = downloads,
                backup = backup,
                pageDecoder = ::decodePage,
                componentCount = componentCount,
            )
        }
    }

    override fun onDestroy() {
        try {
            product?.close()
            product = null
        } finally {
            super.onDestroy()
        }
    }
}

private fun decodePage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
