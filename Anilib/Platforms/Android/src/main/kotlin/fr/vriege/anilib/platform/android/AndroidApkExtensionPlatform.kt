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
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeState
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import fr.vriege.anilib.framework.http.AnilibHttpClient
import fr.vriege.anilib.framework.http.HttpCachePolicy
import fr.vriege.anilib.framework.http.HttpRequest
import fr.vriege.anilib.kernel.PluginRegistration
import fr.vriege.anilib.kernel.StartedAnilib
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

internal class AndroidApkExtensionPlatform(
    private val activity: ComponentActivity,
    private val client: AnilibHttpClient,
    private val inventory: AndroidAniyomiExtensionInventory = AndroidAniyomiExtensionInventory(activity),
    private val runtimePreflight: AndroidAniyomiRuntimePreflight = AndroidAniyomiRuntimePreflight(activity),
    private val sourceRuntime: AndroidAniyomiSourceRuntime = AndroidAniyomiSourceRuntime(activity),
    private val product: StartedAnilib,
    private val startupReports: Map<String, ApkExtensionRuntimeReport> = emptyMap(),
) : ApkExtensionPlatform {
    private val runtimeReports = ConcurrentHashMap(startupReports)
    private val dynamicRegistrations = ConcurrentHashMap<String, List<PluginRegistration>>()

    override fun available(): Boolean = true

    override fun discoverInstalled(): List<InstalledApkExtension> = inventory.discover()

    override fun runtimeReport(extensionPackage: InstalledApkExtension): ApkExtensionRuntimeReport {
        val current = runtimePreflight.report(extensionPackage)
        val startup = runtimeReports[extensionPackage.packageName()]?.takeIf {
            it.state() == ApkExtensionRuntimeState.ACTIVE ||
                it.state() == ApkExtensionRuntimeState.ACTIVATION_FAILED
        }
        return if (current.state() == ApkExtensionRuntimeState.HOST_ABI_AVAILABLE &&
            startup != null
        ) {
            startup
        } else {
            current
        }
    }

    override fun trustCertificate(
        extensionPackage: InstalledApkExtension,
        certificateSha256: String,
    ): ApkExtensionRuntimeReport = runtimePreflight.trust(extensionPackage, certificateSha256)

    override fun forgetCertificateTrust(
        extensionPackage: InstalledApkExtension,
    ): ApkExtensionRuntimeReport = runtimePreflight.forget(extensionPackage)

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
            bytes
        }.thenCompose { bytes ->
            handOff(extensionPackage.packageName(), bytes)
        }.thenApply {
            activateInstalled(extensionPackage.packageName())
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

    private fun handOff(packageName: String, bytes: ByteArray): CompletableFuture<Unit> {
        val installer = activity.packageManager.packageInstaller
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(packageName) }
        val sessionId = installer.createSession(parameters)
        val completion = AndroidApkInstallCoordinator.register(sessionId)
        installer.openSession(sessionId).use { session ->
            session.openWrite("extension.apk", 0, bytes.size.toLong()).use { output ->
                output.write(bytes)
                session.fsync(output)
            }
            val result = PendingIntent.getBroadcast(
                activity,
                sessionId,
                Intent(activity, AnilibApkInstallReceiver::class.java).putExtra(
                    AnilibApkInstallReceiver.EXTRA_EXPECTED_PACKAGE,
                    packageName,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
        return completion.thenApply { installedPackage ->
            check(installedPackage == null || installedPackage == packageName) {
                "Android installed an unexpected package: $installedPackage"
            }
        }
    }

    private fun activateInstalled(packageName: String): String {
        val extension = inventory.discover(packageName)
            ?: error("Android installed $packageName, but it is not a compatible source extension")
        val certificates = extension.signingCertificateSha256()
        require(certificates.size == 1) {
            "Automatic activation requires exactly one APK signing certificate"
        }
        val preflight = runtimePreflight.trust(extension, certificates.single())
        runtimeReports[packageName] = preflight
        check(preflight.state() == ApkExtensionRuntimeState.HOST_ABI_AVAILABLE) {
            "${extension.displayName()} was installed, but its source API is unavailable: " +
                preflight.state().name.lowercase().replace('_', ' ')
        }
        val activation = sourceRuntime.prepare(extension)
        runtimeReports.putAll(activation.reports)
        val failure = activation.reports[packageName]
        check(failure?.state() == ApkExtensionRuntimeState.ACTIVE) {
            failure?.activationFailure()?.orElse("Extension source activation failed")
                ?: "Extension source activation failed"
        }
        val registrations = mutableListOf<PluginRegistration>()
        try {
            activation.bundles.forEach { registrations += product.install(it) }
        } catch (failureCause: Throwable) {
            registrations.asReversed().forEach { runCatching { it.close() } }
            throw failureCause
        }
        dynamicRegistrations.put(packageName, registrations)?.asReversed()?.forEach {
            runCatching { it.close() }
        }
        val sourceLabel = if (registrations.size == 1) "source" else "sources"
        return "${extension.displayName()} installed. ${registrations.size} $sourceLabel added immediately."
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

class AnilibApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmation(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val expectedPackage = intent.getStringExtra(EXTRA_EXPECTED_PACKAGE)
        val installedPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: expectedPackage
        val message = if (status == PackageInstaller.STATUS_SUCCESS) {
            "Package installed for Anilib by Android."
        } else {
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "APK installation failed."
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            AndroidApkInstallCoordinator.complete(sessionId, installedPackage)
        } else {
            AndroidApkInstallCoordinator.fail(sessionId, message)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun confirmation(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }

    companion object {
        const val EXTRA_EXPECTED_PACKAGE = "fr.vriege.anilib.expectedExtensionPackage"
    }
}

private object AndroidApkInstallCoordinator {
    private val pending = ConcurrentHashMap<Int, CompletableFuture<String?>>()

    fun register(sessionId: Int): CompletableFuture<String?> = CompletableFuture<String?>().also {
        check(pending.putIfAbsent(sessionId, it) == null) { "Duplicate Android install session" }
    }

    fun complete(sessionId: Int, packageName: String?) {
        pending.remove(sessionId)?.complete(packageName)
    }

    fun fail(sessionId: Int, message: String) {
        pending.remove(sessionId)?.completeExceptionally(IllegalStateException(message))
    }
}
