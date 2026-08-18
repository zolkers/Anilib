package fr.vriege.anilib.platform.android

import android.content.Context
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionPackage
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeState
import java.util.Locale
import java.util.Optional

/** Persists package-specific certificate trust and audits the host ABI without loading extension code. */
internal class AndroidAniyomiRuntimePreflight(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun report(extension: LegacyExtensionPackage): LegacyExtensionRuntimeReport {
        if (extension.compatibility() != LegacyExtensionCompatibility.COMPATIBLE_METADATA) {
            return report(extension, LegacyExtensionRuntimeState.INCOMPATIBLE_METADATA)
        }
        val trustedCertificate = preferences.getString(extension.packageName(), null)
            ?.takeIf(extension.signingCertificateSha256()::contains)
            ?: return report(extension, LegacyExtensionRuntimeState.TRUST_REQUIRED)
        val missingClasses = requiredHostClasses(extension)
            .filterNot(::hostClassAvailable)
        if (missingClasses.isNotEmpty()) {
            return LegacyExtensionRuntimeReport(
                extension.packageName(),
                LegacyExtensionRuntimeState.HOST_ABI_MISSING,
                missingClasses,
                Optional.of(trustedCertificate),
            )
        }
        return LegacyExtensionRuntimeReport(
            extension.packageName(),
            LegacyExtensionRuntimeState.HOST_ABI_AVAILABLE,
            emptyList(),
            Optional.of(trustedCertificate),
        )
    }

    @Suppress("ApplySharedPref")
    fun trust(extension: LegacyExtensionPackage, certificateSha256: String): LegacyExtensionRuntimeReport {
        require(extension.compatibility() == LegacyExtensionCompatibility.COMPATIBLE_METADATA) {
            "Only metadata-compatible extensions can be trusted"
        }
        val normalized = certificateSha256.trim().lowercase(Locale.ROOT)
        require(SHA_256.matches(normalized)) { "Certificate fingerprint must be 64 lowercase hex characters" }
        require(normalized in extension.signingCertificateSha256()) {
            "Certificate fingerprint does not sign ${extension.packageName()}"
        }
        check(preferences.edit().putString(extension.packageName(), normalized).commit()) {
            "Unable to persist legacy extension trust"
        }
        return report(extension)
    }

    @Suppress("ApplySharedPref")
    fun forget(extension: LegacyExtensionPackage): LegacyExtensionRuntimeReport {
        check(preferences.edit().remove(extension.packageName()).commit()) {
            "Unable to remove legacy extension trust"
        }
        return report(extension)
    }

    private fun report(
        extension: LegacyExtensionPackage,
        state: LegacyExtensionRuntimeState,
    ): LegacyExtensionRuntimeReport = LegacyExtensionRuntimeReport(
        extension.packageName(),
        state,
        emptyList(),
        Optional.empty(),
    )

    private fun requiredHostClasses(extension: LegacyExtensionPackage): List<String> = buildList {
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
        const val PREFERENCES_NAME = "anilib-legacy-extension-trust"
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
