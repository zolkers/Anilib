package fr.vriege.anilib.platform.compose

import dev.datlag.kcef.KCEF
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

object DesktopBrowserRuntime {
    fun initialize(dataDirectory: Path): BrowserRuntimeStatus {
        unsupportedPlatformMessage()?.let { return BrowserRuntimeStatus.unavailable(it) }
        var failure: Throwable? = null
        var restartRequired = false
        return runCatching {
            val browserDirectory = dataDirectory.toAbsolutePath().normalize().resolve("browser")
            clearPendingData(browserDirectory)
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
        if (unsupportedPlatformMessage() != null) {
            return
        }
        KCEF.disposeBlocking()
    }

    private fun unsupportedPlatformMessage(): String? {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val architecture = System.getProperty("os.arch", "").lowercase(Locale.ROOT)
        return if (osName.contains("windows") && architecture in setOf("aarch64", "arm64")) {
            "The embedded browser is unavailable on Windows ARM64. " +
                "Source browsing and downloads remain available."
        } else {
            null
        }
    }

    private fun clearPendingData(browserDirectory: Path) {
        val marker = browserDirectory.resolve("clear-data-on-start")
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        require(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            "Browser cleanup marker is not a regular file"
        }
        val cacheDirectory = browserDirectory.resolve("cache").normalize()
        require(cacheDirectory.parent == browserDirectory) { "Browser cache escaped its data directory" }
        if (Files.exists(cacheDirectory, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(cacheDirectory, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, failure: IOException?): FileVisitResult {
                    if (failure != null) {
                        throw failure
                    }
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        }
        Files.delete(marker)
    }
}
