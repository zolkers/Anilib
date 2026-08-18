package fr.vriege.anilib.platform.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionCompatibility
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import java.security.MessageDigest
import java.util.Optional

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

    fun discover(packageName: String): InstalledApkExtension? {
        val packageManager = applicationContext.packageManager
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(packageFlags().toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, packageFlags())
            }
            packageInfo.takeIf(::isAniyomiExtension)
                ?.let { extensionMetadata(packageManager, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> {
        val flags = packageFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageFlags(): Int = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            0
        })

    private fun isAniyomiExtension(packageInfo: PackageInfo): Boolean = extensionKind(packageInfo) != null

    private fun extensionKind(packageInfo: PackageInfo): ExtensionContentKind? {
        val features = packageInfo.reqFeatures.orEmpty().map { it.name }.toSet()
        return when {
            ANIME_EXTENSION_FEATURE in features -> ExtensionContentKind.ANIME
            MANGA_EXTENSION_FEATURE in features -> ExtensionContentKind.MANGA
            else -> null
        }
    }

    private fun extensionMetadata(
        packageManager: PackageManager,
        packageInfo: PackageInfo,
    ): InstalledApkExtension? {
        val applicationInfo = packageInfo.applicationInfo ?: return null
        val metadata = applicationInfo.metaData ?: return null
        val packageName = packageInfo.packageName
        val contentKind = extensionKind(packageInfo) ?: return null
        val contract = metadataContract(contentKind)
        val entrypoints = metadata.getString(contract.sourceClass)
            .orEmpty()
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { if (it.startsWith('.')) packageName + it else it }
            .distinct()
        val versionName = packageInfo.versionName?.takeIf(String::isNotBlank) ?: "unknown"
        val libraryVersion = metadata.getString(contract.extensionLibrary)
            ?.takeIf(String::isNotBlank)
            ?: versionName.substringBeforeLast('.', "unknown")
        val signatures = signatures(packageInfo)
        val compatibility = when {
            signatures.isEmpty() -> ApkExtensionCompatibility.UNSIGNED
            entrypoints.isEmpty() -> ApkExtensionCompatibility.MISSING_ENTRYPOINT
            !supportedLibrary(contentKind, libraryVersion) ->
                ApkExtensionCompatibility.UNSUPPORTED_LIBRARY
            else -> ApkExtensionCompatibility.COMPATIBLE_METADATA
        }
        val metadataName = metadata.getString(METADATA_NAME)?.takeIf(String::isNotBlank)
        val sourceFactory = metadata.getString(contract.sourceFactory)
            ?.takeIf(String::isNotBlank)
            ?.let { if (it.startsWith('.')) packageName + it else it }
        val applicationLabel = packageManager.getApplicationLabel(applicationInfo)
            .toString()
            .removePrefix("Aniyomi: ")
            .removePrefix("Tachiyomi: ")
            .takeIf(String::isNotBlank)
        return InstalledApkExtension(
            packageName,
            metadataName ?: applicationLabel ?: packageName,
            versionCode(packageInfo),
            versionName,
            libraryVersion,
            metadataFlag(metadata, METADATA_CONTENT_WARNING) || metadataFlag(metadata, contract.nsfw),
            metadataFlag(metadata, METADATA_IS_TORRENT) || metadataFlag(metadata, METADATA_TORRENT),
            contentKind,
            entrypoints,
            Optional.ofNullable(sourceFactory),
            metadataFlag(metadata, contract.hasReadme),
            metadataFlag(metadata, contract.hasChangelog),
            signatures,
            compatibility,
        )
    }

    private fun supportedLibrary(contentKind: ExtensionContentKind, libraryVersion: String): Boolean =
        libraryVersion.toDoubleOrNull() in when (contentKind) {
            ExtensionContentKind.ANIME -> SUPPORTED_ANIME_LIBRARY_VERSIONS
            ExtensionContentKind.MANGA -> SUPPORTED_MANGA_LIBRARY_VERSIONS
            ExtensionContentKind.MIXED,
            ExtensionContentKind.UNKNOWN,
            -> emptySet()
        }

    private fun metadataContract(contentKind: ExtensionContentKind): MetadataContract =
        when (contentKind) {
            ExtensionContentKind.ANIME -> MetadataContract(
                METADATA_ANIME_SOURCE_CLASS,
                METADATA_ANIME_SOURCE_FACTORY,
                METADATA_ANIME_EXTENSION_LIB,
                METADATA_ANIME_NSFW,
                METADATA_ANIME_HAS_README,
                METADATA_ANIME_HAS_CHANGELOG,
            )
            ExtensionContentKind.MANGA -> MetadataContract(
                METADATA_MANGA_SOURCE_CLASS,
                METADATA_MANGA_SOURCE_FACTORY,
                METADATA_MANGA_EXTENSION_LIB,
                METADATA_MANGA_NSFW,
                METADATA_MANGA_HAS_README,
                METADATA_MANGA_HAS_CHANGELOG,
            )
            ExtensionContentKind.MIXED,
            ExtensionContentKind.UNKNOWN,
            -> error("APK extension cannot use mixed or unknown metadata")
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
        const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
        const val MANGA_EXTENSION_FEATURE = "tachiyomi.extension"
        const val METADATA_ANIME_SOURCE_CLASS = "tachiyomi.animeextension.class"
        const val METADATA_ANIME_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
        const val METADATA_ANIME_EXTENSION_LIB = "aniyomix.extensionLib"
        const val METADATA_ANIME_NSFW = "tachiyomi.animeextension.nsfw"
        const val METADATA_ANIME_HAS_README = "tachiyomi.animeextension.hasReadme"
        const val METADATA_ANIME_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
        const val METADATA_MANGA_SOURCE_CLASS = "tachiyomi.extension.class"
        const val METADATA_MANGA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        const val METADATA_MANGA_EXTENSION_LIB = "tachiyomi.extension.lib"
        const val METADATA_MANGA_NSFW = "tachiyomi.extension.nsfw"
        const val METADATA_MANGA_HAS_README = "tachiyomi.extension.hasReadme"
        const val METADATA_MANGA_HAS_CHANGELOG = "tachiyomi.extension.hasChangelog"
        const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"
        const val METADATA_NAME = "aniyomix.name"
        const val METADATA_CONTENT_WARNING = "aniyomix.contentWarning"
        const val METADATA_IS_TORRENT = "aniyomix.torrent"
        const val MAX_VISIBLE_PACKAGES = 10_000
        val SUPPORTED_ANIME_LIBRARY_VERSIONS = setOf(14.0, 16.0, 17.0)
        val SUPPORTED_MANGA_LIBRARY_VERSIONS = setOf(1.4, 1.6)
    }
}

private data class MetadataContract(
    val sourceClass: String,
    val sourceFactory: String,
    val extensionLibrary: String,
    val nsfw: String,
    val hasReadme: String,
    val hasChangelog: String,
)
