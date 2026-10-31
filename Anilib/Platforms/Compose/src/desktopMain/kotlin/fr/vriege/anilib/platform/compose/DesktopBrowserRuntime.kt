package fr.vriege.anilib.platform.compose

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBuilder
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object DesktopBrowserRuntime {
    const val NATIVE_RELEASE = "jbr-release-17.0.14b1367.22"
    private const val RUNTIME_DIRECTORY = "runtime-kcef-2025.03.23"
    private const val CACHE_DIRECTORY = "cache-kcef-2025.03.23"
    @Volatile
    private var cachedStatus: BrowserRuntimeStatus? = null

    @Volatile
    private var initializationAttempted = false

    @Synchronized
    fun initialize(dataDirectory: Path): BrowserRuntimeStatus {
        cachedStatus?.let { return it }
        initializationAttempted = true
        var failure: Throwable? = null
        var restartRequired = false
        return runCatching {
            val browserDirectory = dataDirectory.toAbsolutePath().normalize().resolve("browser")
            clearPendingData(browserDirectory)
            KCEF.initBlocking(
                builder = {
                    installDir(browserDirectory.resolve(RUNTIME_DIRECTORY).toFile())
                    download { github { release(NATIVE_RELEASE) } }
                    settings {
                        configureNativePaths(this, browserDirectory.resolve(RUNTIME_DIRECTORY))
                        cachePath = browserDirectory.resolve(CACHE_DIRECTORY).toString()
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
        }.also { cachedStatus = it }
    }

    fun configureNativePaths(settings: KCEFBuilder.Settings, runtime: Path) {
        // JCEF otherwise derives these from java.home, which belongs to the app's JVM, not Chromium.
        val helper = if (System.getProperty("os.name").startsWith("Windows")) "jcef_helper.exe" else "jcef_helper"
        settings.browserSubProcessPath = runtime.resolve(helper).toString()
        settings.resourcesDirPath = runtime.toString()
        settings.localesDirPath = runtime.resolve("locales").toString()
    }

    @Synchronized
    fun dispose() {
        if (initializationAttempted) {
            KCEF.disposeBlocking()
            initializationAttempted = false
            cachedStatus = null
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
        val cacheDirectory = browserDirectory.resolve(CACHE_DIRECTORY).normalize()
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
