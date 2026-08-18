package fr.vriege.anilib.platform.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.PathClassLoader
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiAnimeSourceAdapter
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeReport
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeState
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension
import fr.vriege.anilib.feature.source.SourceId
import fr.vriege.anilib.kernel.AnilibPlugin
import java.lang.reflect.InvocationTargetException

internal data class AndroidApkSourceActivation(
    val bundles: List<AnilibPlugin>,
    val reports: Map<String, ApkExtensionRuntimeReport>,
)

internal class AndroidAniyomiSourceRuntime(
    context: Context,
    private val inventory: AndroidAniyomiExtensionInventory = AndroidAniyomiExtensionInventory(context),
    private val preflight: AndroidAniyomiRuntimePreflight = AndroidAniyomiRuntimePreflight(context),
) {
    private val applicationContext = context.applicationContext
    private val preferenceBridge = AndroidAniyomiPreferenceBridge(context)

    fun prepare(): AndroidApkSourceActivation {
        val bundles = mutableListOf<AnilibPlugin>()
        val reports = linkedMapOf<String, ApkExtensionRuntimeReport>()
        val selectedSourceIds = mutableSetOf<SourceId>()
        inventory.discover().forEach { extension ->
            val report = preflight.report(extension)
            if (report.state() != ApkExtensionRuntimeState.HOST_ABI_AVAILABLE) {
                reports[extension.packageName()] = report
                return@forEach
            }
            val certificate = report.trustedCertificateSha256().orElseThrow()
            try {
                val adapted = load(extension)
                require(adapted.isNotEmpty()) { "APK extension created no sources" }
                val sourceIds = adapted.map { it.source().descriptor().id() }
                require(sourceIds.toSet().size == sourceIds.size) {
                    "APK extension created duplicate source IDs"
                }
                require(sourceIds.none(selectedSourceIds::contains)) {
                    "APK source ID collides with another selected APK"
                }
                selectedSourceIds.addAll(sourceIds)
                bundles.addAll(adapted.map(AniyomiAnimeSourceAdapter.AdaptedSource::bundle))
                reports[extension.packageName()] = ApkExtensionRuntimeReport.active(
                    extension.packageName(),
                    certificate,
                )
            } catch (failure: Throwable) {
                reports[extension.packageName()] = ApkExtensionRuntimeReport.activationFailed(
                    extension.packageName(),
                    certificate,
                    failureMessage(failure),
                )
            }
        }
        return AndroidApkSourceActivation(bundles.toList(), reports.toMap())
    }

    private fun load(
        extension: InstalledApkExtension,
    ): List<AniyomiAnimeSourceAdapter.AdaptedSource> {
        val applicationInfo = applicationInfo(extension.packageName())
        val sourcePath = requireNotNull(applicationInfo.sourceDir) {
            "APK extension has no source path"
        }
        val classLoader = AndroidApkClassLoader(sourcePath, applicationContext.classLoader)
        return extension.sourceEntrypoints()
            .flatMap { entrypoint -> instantiate(entrypoint, classLoader) }
            .map { source ->
                AniyomiAnimeSourceAdapter.adapt(
                    extension.packageName(),
                    extension.versionName(),
                    source,
                    {
                        inventory.discover(extension.packageName())
                            ?.let(preflight::report)
                            ?.state() == ApkExtensionRuntimeState.HOST_ABI_AVAILABLE
                    },
                    preferenceBridge.project(source),
                )
            }
    }

    private fun instantiate(className: String, classLoader: ClassLoader): List<Any> {
        val instance = Class.forName(className, false, classLoader)
            .getDeclaredConstructor()
            .newInstance()
        val factory = instance.javaClass.methods
            .singleOrNull { it.name == "createSources" && it.parameterCount == 0 }
            ?: return listOf(instance)
        val sources = invokeFactory(factory, instance)
        require(sources.all { it != null }) { "Aniyomi source factory returned null" }
        return sources.filterNotNull()
    }

    private fun invokeFactory(method: java.lang.reflect.Method, instance: Any): List<*> = try {
        method.invoke(instance) as? List<*>
            ?: throw IllegalStateException("Aniyomi source factory must return a List")
    } catch (failure: InvocationTargetException) {
        throw failure.cause ?: failure
    }

    @Suppress("DEPRECATION")
    private fun applicationInfo(packageName: String): ApplicationInfo {
        val packageManager = applicationContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }
    }

    private fun failureMessage(failure: Throwable): String {
        var cause = failure
        repeat(MAX_CAUSE_DEPTH) {
            cause.cause?.let { cause = it } ?: return@repeat
        }
        val type = cause.javaClass.simpleName.ifBlank { "ActivationFailure" }
        val detail = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return (if (detail.isBlank()) type else "$type: $detail").take(MAX_FAILURE_LENGTH)
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 8
        const val MAX_FAILURE_LENGTH = 300
    }
}

private class AndroidApkClassLoader(
    sourcePath: String,
    parent: ClassLoader,
) : PathClassLoader(sourcePath, null, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name)?.let { return@synchronized it }
        val loaded = if (PARENT_FIRST_PREFIXES.any(name::startsWith)) {
            super.loadClass(name, false)
        } else {
            try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, false)
            }
        }
        if (resolve) {
            resolveClass(loaded)
        }
        loaded
    }

    private companion object {
        val PARENT_FIRST_PREFIXES = listOf("android.", "java.", "javax.", "kotlin.", "sun.")
    }
}
