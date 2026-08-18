package fr.vriege.anilib.platform.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import java.security.MessageDigest
import java.util.Optional

/** Best-effort metadata discovery for Android-visible, separately installed Aniyomi APKs. */
internal class AndroidAniyomiExtensionInventory(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun discover(): List<InstalledApkExtension> {
        val packageManager = applicationContext.packageManager
        return installedPackages(packageManager)
            .asSequence()
            .take(MAX_VISIBLE_PACKAGES)
            .filter(::isAniyomiExtension)
            .mapNotNull { packageInfo ->
                try {
                    extensionMetadata(packageManager, packageInfo)
                } catch (_: RuntimeException) {
                    null
                }
            }
            .sortedWith(compareBy({ it.displayName() }, { it.packageName() }))
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                0
            })
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }
    }

    private fun isAniyomiExtension(packageInfo: PackageInfo): Boolean =
        packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    private fun extensionMetadata(
        packageManager: PackageManager,
        packageInfo: PackageInfo,
    ): InstalledApkExtension? {
        val applicationInfo = packageInfo.applicationInfo ?: return null
        val metadata = applicationInfo.metaData ?: return null
        val packageName = packageInfo.packageName
        val entrypoints = metadata.getString(METADATA_SOURCE_CLASS)
            .orEmpty()
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { if (it.startsWith('.')) packageName + it else it }
            .distinct()
        val versionName = packageInfo.versionName?.takeIf(String::isNotBlank) ?: "unknown"
        val libraryVersion = metadata.getString(METADATA_EXTENSION_LIB)
            ?.takeIf(String::isNotBlank)
            ?: versionName.substringBeforeLast('.', "unknown")
        val signatures = signatures(packageInfo)
        val compatibility = when {
            signatures.isEmpty() -> ApkExtensionCompatibility.UNSIGNED
            entrypoints.isEmpty() -> ApkExtensionCompatibility.MISSING_ENTRYPOINT
            libraryVersion.toDoubleOrNull() !in SUPPORTED_LIBRARY_VERSIONS ->
                ApkExtensionCompatibility.UNSUPPORTED_LIBRARY
            else -> ApkExtensionCompatibility.COMPATIBLE_METADATA
        }
        val metadataName = metadata.getString(METADATA_NAME)?.takeIf(String::isNotBlank)
        val sourceFactory = metadata.getString(METADATA_SOURCE_FACTORY)
            ?.takeIf(String::isNotBlank)
            ?.let { if (it.startsWith('.')) packageName + it else it }
        val applicationLabel = packageManager.getApplicationLabel(applicationInfo)
            .toString()
            .removePrefix("Aniyomi: ")
            .takeIf(String::isNotBlank)
        return InstalledApkExtension(
            packageName,
            metadataName ?: applicationLabel ?: packageName,
            versionCode(packageInfo),
            versionName,
            libraryVersion,
            metadataFlag(metadata, METADATA_CONTENT_WARNING) || metadataFlag(metadata, METADATA_NSFW),
            metadataFlag(metadata, METADATA_IS_TORRENT) || metadataFlag(metadata, METADATA_TORRENT),
            entrypoints,
            Optional.ofNullable(sourceFactory),
            metadataFlag(metadata, METADATA_HAS_README),
            metadataFlag(metadata, METADATA_HAS_CHANGELOG),
            signatures,
            compatibility,
        )
    }

    private fun metadataFlag(metadata: Bundle, key: String): Boolean = when (val value = metadata.get(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> false
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signatures(packageInfo: PackageInfo): List<String> {
        val values = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        return values.orEmpty()
            .map { sha256(it.toByteArray()) }
            .distinct()
            .sorted()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val EXTENSION_FEATURE = "tachiyomi.animeextension"
        const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
        const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
        const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"
        const val METADATA_HAS_README = "tachiyomi.animeextension.hasReadme"
        const val METADATA_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
        const val METADATA_NAME = "aniyomix.name"
        const val METADATA_EXTENSION_LIB = "aniyomix.extensionLib"
        const val METADATA_CONTENT_WARNING = "aniyomix.contentWarning"
        const val METADATA_IS_TORRENT = "aniyomix.torrent"
        const val MAX_VISIBLE_PACKAGES = 10_000
        val SUPPORTED_LIBRARY_VERSIONS = setOf(14.0, 16.0)
    }
}
