package fr.vriege.anilib.platform.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionInstaller
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionPackage
import fr.vriege.anilib.framework.http.AnilibHttpClient
import fr.vriege.anilib.framework.http.HttpCachePolicy
import fr.vriege.anilib.framework.http.HttpRequest
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Android-only, user-confirmed hand-off for legacy Aniyomi extension APKs. */
internal class AndroidAniyomiApkInstaller(
    private val activity: ComponentActivity,
    private val client: AnilibHttpClient,
    private val inventory: AndroidAniyomiExtensionInventory = AndroidAniyomiExtensionInventory(activity),
) : LegacyExtensionInstaller {
    override fun available(): Boolean = true

    override fun discoverInstalled(): List<LegacyExtensionPackage> = inventory.discover()

    override fun install(extensionPackage: ExtensionPackageMetadata): CompletableFuture<String> {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return CompletableFuture<String>().apply {
                completeExceptionally(
                    IllegalStateException("Allow installs from Anilib in Android settings, then retry."),
                )
            }
        }
        return CompletableFuture.supplyAsync {
            val artifact = extensionPackage.artifacts()
                .firstOrNull { it.format() == ExtensionArtifactFormat.ANIYOMI_APK }
                ?: throw IllegalArgumentException("Extension has no Aniyomi APK artifact")
            val bytes = fetch(artifact.uri())
            handOff(extensionPackage.packageName(), bytes)
            "Android package installer opened. Confirm the APK installation there."
        }
    }

    private fun fetch(initialUri: URI): ByteArray {
        var current = requireHttps(initialUri)
        repeat(MAX_REDIRECTS + 1) { redirects ->
            val response = client.execute(
                HttpRequest.builder(current)
                    .header("accept", "application/vnd.android.package-archive, application/octet-stream")
                    .cache(HttpCachePolicy.bypass())
                    .minimumInterval(Duration.ofMillis(250))
                    .build(),
            )
            if (response.statusCode() !in REDIRECTS) {
                check(response.statusCode() == 200) { "Extension APK returned HTTP ${response.statusCode()}" }
                val body = response.body()
                require(body.size <= MAX_APK_BYTES) { "Extension APK exceeds 16 MiB" }
                return body
            }
            check(redirects < MAX_REDIRECTS) { "Extension APK exceeded $MAX_REDIRECTS redirects" }
            val location = response.firstHeader("location")
                .orElseThrow { IllegalStateException("Extension APK redirect has no Location header") }
            current = requireHttps(current.resolve(location))
        }
        error("Unreachable extension APK redirect state")
    }

    private fun handOff(packageName: String, bytes: ByteArray) {
        val installer = activity.packageManager.packageInstaller
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(packageName) }
        val sessionId = installer.createSession(parameters)
        installer.openSession(sessionId).use { session ->
            session.openWrite("extension.apk", 0, bytes.size.toLong()).use { output ->
                output.write(bytes)
                session.fsync(output)
            }
            val result = PendingIntent.getBroadcast(
                activity,
                sessionId,
                Intent(activity, AniyomiApkInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
    }

    private fun requireHttps(value: URI): URI {
        val uri = value.normalize()
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null,
        ) { "Extension APK URI must be absolute HTTPS without credentials" }
        return uri
    }

    private companion object {
        const val MAX_APK_BYTES = 16 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

/** Receives PackageInstaller status and launches Android's mandatory confirmation surface. */
class AniyomiApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmation(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val message = if (status == PackageInstaller.STATUS_SUCCESS) {
            "Aniyomi extension APK installed by Android."
        } else {
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "APK installation failed."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun confirmation(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }
}
