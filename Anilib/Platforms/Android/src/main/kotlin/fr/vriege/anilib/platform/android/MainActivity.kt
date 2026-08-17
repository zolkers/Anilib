package fr.vriege.anilib.platform.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport
import fr.vriege.anilib.platform.compose.AnilibApp

/** Android launcher for the shared Anilib product and adaptive Compose shell. */
class MainActivity : ComponentActivity() {
    private var product: AutoCloseable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val started = StandardAnilib.start(
            filesDir.toPath(),
            UrlConnectionHttpTransport(),
            emptyList(),
        )
        product = started
        val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
        val componentCount = started.components().size
        setContent {
            AnilibApp(
                presentation = presentation,
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
