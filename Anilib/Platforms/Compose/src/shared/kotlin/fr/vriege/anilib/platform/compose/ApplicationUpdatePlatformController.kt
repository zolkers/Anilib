package fr.vriege.anilib.platform.compose

import androidx.compose.runtime.staticCompositionLocalOf
import fr.vriege.anilib.feature.applicationupdate.ApplicationArtifact
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification
import java.nio.file.Path

interface ApplicationUpdatePlatformController {
    suspend fun download(artifact: ApplicationArtifact, progress: (Long) -> Unit): Path

    fun install(verification: ApplicationUpdateVerification)
}

internal val LocalApplicationUpdatePlatformController =
    staticCompositionLocalOf<ApplicationUpdatePlatformController> {
        object : ApplicationUpdatePlatformController {
            override suspend fun download(
                artifact: ApplicationArtifact,
                progress: (Long) -> Unit,
            ): Path = error("Application updates are unavailable on this platform")

            override fun install(verification: ApplicationUpdateVerification) {
                error("Application updates are unavailable on this platform")
            }
        }
    }
