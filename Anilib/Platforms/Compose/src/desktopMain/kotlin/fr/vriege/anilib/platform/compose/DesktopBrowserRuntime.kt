package fr.vriege.anilib.platform.compose

import dev.datlag.kcef.KCEF
import java.nio.file.Path

/** Owns KCEF initialization and disposal at the desktop platform boundary. */
object DesktopBrowserRuntime {
    fun initialize(dataDirectory: Path): BrowserRuntimeStatus {
        var failure: Throwable? = null
        var restartRequired = false
        return runCatching {
            val browserDirectory = dataDirectory.toAbsolutePath().normalize().resolve("browser")
            KCEF.initBlocking(
                builder = {
                    installDir(browserDirectory.resolve("runtime").toFile())
                    settings {
                        cachePath = browserDirectory.resolve("cache").toString()
                        persistSessionCookies = true
                    }
                },
                onError = { failure = it },
                onRestartRequired = { restartRequired = true },
            )
            when {
                failure != null -> BrowserRuntimeStatus.unavailable(
                    failure?.message ?: "The desktop browser engine could not start.",
                )
                restartRequired -> BrowserRuntimeStatus.unavailable(
                    "The desktop browser engine was installed. Restart Anilib to use it.",
                )
                else -> BrowserRuntimeStatus.ready()
            }
        }.getOrElse {
            BrowserRuntimeStatus.unavailable(it.message ?: "The desktop browser engine could not start.")
        }
    }

    fun dispose() {
        KCEF.disposeBlocking()
    }
}
