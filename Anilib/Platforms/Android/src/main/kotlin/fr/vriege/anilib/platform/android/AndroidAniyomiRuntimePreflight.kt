package fr.vriege.anilib.platform.android

import android.content.Context
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeState
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import java.util.Locale
import java.util.Optional

internal class AndroidAniyomiRuntimePreflight(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun report(extension: InstalledApkExtension): ApkExtensionRuntimeReport {
        if (extension.compatibility() != ApkExtensionCompatibility.COMPATIBLE_METADATA) {
            return report(extension, ApkExtensionRuntimeState.INCOMPATIBLE_METADATA)
        }
        val trustedCertificate = preferences.getString(extension.packageName(), null)
            ?.takeIf(extension.signingCertificateSha256()::contains)
            ?: return report(extension, ApkExtensionRuntimeState.TRUST_REQUIRED)
        val missingClasses = requiredHostClasses(extension)
            .filterNot(::hostClassAvailable)
        if (missingClasses.isNotEmpty()) {
            return ApkExtensionRuntimeReport(
                extension.packageName(),
                ApkExtensionRuntimeState.HOST_ABI_MISSING,
                missingClasses,
                Optional.of(trustedCertificate),
                Optional.empty(),
            )
        }
        return ApkExtensionRuntimeReport(
            extension.packageName(),
            ApkExtensionRuntimeState.HOST_ABI_AVAILABLE,
            emptyList(),
            Optional.of(trustedCertificate),
            Optional.empty(),
        )
    }

    @Suppress("ApplySharedPref")
    fun trust(extension: InstalledApkExtension, certificateSha256: String): ApkExtensionRuntimeReport {
        require(extension.compatibility() == ApkExtensionCompatibility.COMPATIBLE_METADATA) {
            "Only metadata-compatible extensions can be trusted"
        }
        val normalized = certificateSha256.trim().lowercase(Locale.ROOT)
        require(SHA_256.matches(normalized)) { "Certificate fingerprint must be 64 lowercase hex characters" }
        require(normalized in extension.signingCertificateSha256()) {
            "Certificate fingerprint does not sign ${extension.packageName()}"
        }
        check(preferences.edit().putString(extension.packageName(), normalized).commit()) {
            "Unable to persist APK extension trust"
        }
        return report(extension)
    }

    @Suppress("ApplySharedPref")
    fun forget(extension: InstalledApkExtension): ApkExtensionRuntimeReport {
        check(preferences.edit().remove(extension.packageName()).commit()) {
            "Unable to remove APK extension trust"
        }
        return report(extension)
    }

    private fun report(
        extension: InstalledApkExtension,
        state: ApkExtensionRuntimeState,
    ): ApkExtensionRuntimeReport = ApkExtensionRuntimeReport(
        extension.packageName(),
        state,
        emptyList(),
        Optional.empty(),
        Optional.empty(),
    )

    private fun requiredHostClasses(extension: InstalledApkExtension): List<String> = buildList {
        addAll(REQUIRED_HOST_CLASSES)
        if (extension.torrent()) {
            add(TORRENT_HOST_CLASS)
        }
    }

    private fun hostClassAvailable(className: String): Boolean = try {
        Class.forName(className, false, applicationContext.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        false
    }

    private companion object {
        const val PREFERENCES_NAME = "anilib-apk-extension-trust"
        val SHA_256 = Regex("[0-9a-f]{64}")
        val REQUIRED_HOST_CLASSES = listOf(
            "eu.kanade.tachiyomi.animesource.AnimeSource",
            "eu.kanade.tachiyomi.animesource.AnimeSourceFactory",
            "eu.kanade.tachiyomi.network.NetworkHelper",
            "rx.Observable",
            "okhttp3.OkHttpClient",
            "org.jsoup.Jsoup",
            "uy.kohesive.injekt.Injekt",
            "fi.iki.elonen.NanoHTTPD",
            "kotlinx.coroutines.BuildersKt",
            "kotlinx.serialization.json.Json",
            "androidx.preference.Preference",
        )
        const val TORRENT_HOST_CLASS = "aniyomi.core.common.torrent.TorrentServerApi"
    }
}
