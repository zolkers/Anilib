package fr.vriege.anilib.platform.android

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import fr.vriege.anilib.feature.applicationupdate.ApplicationArtifact
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification
import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors
import fr.vriege.anilib.platform.compose.ApplicationUpdatePlatformController
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection
import java.nio.file.AtomicMoveNotSupportedException

class AndroidApplicationUpdateController(private val activity: MainActivity) :
    ApplicationUpdatePlatformController {
    private val updateDirectory = activity.filesDir.toPath().resolve("application-updates")

    override suspend fun download(artifact: ApplicationArtifact, progress: (Long) -> Unit): Path =
        kotlin.coroutines.suspendCoroutine { continuation ->
            ManagedExecutors.start("anilib-update-download") {
                continuation.resumeWith(runCatching {
            Files.createDirectories(updateDirectory)
            val target = updateDirectory.resolve(artifact.fileName()).normalize()
            require(target.parent == updateDirectory) { "Update installer escapes its managed directory" }
            val temporary = target.resolveSibling(target.fileName.toString() + ".part")
            Files.deleteIfExists(temporary)
            try {
                download(artifact.download(), temporary, artifact.sizeBytes(), progress)
                try {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
                    target
            } finally {
                Files.deleteIfExists(temporary)
            }
                })
            }
        }

    override fun install(verification: ApplicationUpdateVerification) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            error("Allow installs from Anilib in Android settings, then retry")
        }
        val installer = activity.packageManager.packageInstaller
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(activity.packageName) }
        val sessionId = installer.createSession(parameters)
        installer.openSession(sessionId).use { session ->
            Files.newInputStream(verification.artifact()).use { input ->
                session.openWrite("anilib-update.apk", 0, Files.size(verification.artifact())).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val result = PendingIntent.getBroadcast(
                activity,
                sessionId,
                Intent(activity, AnilibApkInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
    }

    private fun download(initial: URI, target: Path, expectedBytes: Long, progress: (Long) -> Unit) {
        var current = initial
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val connection = current.toURL().openConnection() as? HttpsURLConnection
                ?: error("Application update download must remain on HTTPS")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "Anilib-Application-Update")
            connection.connect()
            try {
                if (connection.responseCode in REDIRECTS) {
                    check(redirect < MAX_REDIRECTS) { "Application update exceeded redirect limit" }
                    val location = connection.getHeaderField("Location")
                        ?: error("Application update redirect has no Location header")
                    current = requireHttps(current.resolve(location))
                    return@repeat
                }
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Application update returned HTTP ${connection.responseCode}"
                }
                connection.inputStream.use { input ->
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= expectedBytes) { "Application update exceeds signed size" }
                            output.write(buffer, 0, count)
                            progress(total)
                        }
                        check(total == expectedBytes) { "Application update size is incomplete" }
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        error("Application update redirect handling failed")
    }

    private fun requireHttps(value: URI): URI = value.normalize().also {
        require(
            it.scheme.equals("https", ignoreCase = true) &&
                !it.host.isNullOrBlank() && it.userInfo == null && it.fragment == null,
        ) { "Application update URI must be absolute HTTPS without credentials" }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MILLIS = 30_000
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
