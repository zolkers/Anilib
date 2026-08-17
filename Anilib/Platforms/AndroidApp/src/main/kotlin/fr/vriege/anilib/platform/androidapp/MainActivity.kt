package fr.vriege.anilib.platform.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.platform.android.AndroidProductHost
import fr.vriege.anilib.platform.compose.AnilibApp

/** Android lifecycle adapter for the shared Anilib product and Compose shell. */
class MainActivity : ComponentActivity() {
    private var productHost: AndroidProductHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val host = AndroidProductHost(filesDir.toPath())
        host.start()
        productHost = host

        val presentation = host.capability(LibraryUiCapabilities.PRESENTATION)
        setContent {
            AnilibApp(
                presentation = presentation,
                componentCount = host.componentCount(),
            )
        }
    }

    override fun onDestroy() {
        try {
            productHost?.close()
            productHost = null
        } finally {
            super.onDestroy()
        }
    }
}
