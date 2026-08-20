package fr.vriege.anilib.platform.android

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.vriege.anilib.configuration.standard.PortableBundleLoading
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities
import fr.vriege.anilib.feature.network.NetworkCapabilities
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities
import fr.vriege.anilib.feature.settings.SettingsCapabilities
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities
import fr.vriege.anilib.feature.player.PlayerOrientationPolicy
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities
import fr.vriege.anilib.feature.updates.ui.UpdateUiCapabilities
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdateUiCapabilities
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport
import fr.vriege.anilib.platform.compose.AnilibApp
import fr.vriege.anilib.platform.compose.AnilibStartupScreen
import fr.vriege.anilib.platform.compose.ComposePlayerBackend
import fr.vriege.anilib.platform.compose.BrowserRuntimeStatus
import fr.vriege.anilib.kernel.StartedAnilib

class MainActivity : ComponentActivity() {
    private var product: AutoCloseable? = null
    private val startupState = mutableStateOf<AndroidStartupState>(AndroidStartupState.Loading)
    private var startupAttempt = 0
    private var playerActive = false
    private var backgroundAudio = false
    private lateinit var browserDataController: AndroidBrowserDataController
    private lateinit var browserPlatformController: AndroidBrowserPlatformController
    private lateinit var applicationUpdatePlatformController: AndroidApplicationUpdateController
    private lateinit var backupImportPicker: AndroidBackupImportPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browserDataController = AndroidBrowserDataController(this)
        browserPlatformController = AndroidBrowserPlatformController()
        applicationUpdatePlatformController = AndroidApplicationUpdateController(this)
        backupImportPicker = AndroidBackupImportPicker(this)
        setContent {
            when (val state = startupState.value) {
                AndroidStartupState.Loading -> AnilibStartupScreen(null, ::startProduct)
                is AndroidStartupState.Failed -> AnilibStartupScreen(state.message, ::startProduct)
                is AndroidStartupState.Ready -> StartedContent(state)
            }
        }
        runCatching { LibraryUpdateReceiver.schedule(this) }
            .onFailure { failure -> Log.w(LOG_TAG, "Unable to schedule library updates", failure) }
        val packagedVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
        System.setProperty("anilib.version", packagedVersion)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        startProduct()
    }

    private fun startProduct() {
        val attempt = ++startupAttempt
        startupState.value = AndroidStartupState.Loading
        Thread({
            val result = runCatching {
                val apkActivation = AndroidAniyomiSourceRuntime(this).prepare()
                val started = StandardAnilib.start(
                    filesDir.toPath(),
                    UrlConnectionHttpTransport(),
                    ComposePlayerBackend(),
                    AndroidLibraryUpdateNotifier(this),
                    AndroidNetworkStatus(this),
                    PortableBundleLoading.DISABLED,
                    apkActivation.bundles,
                )
                AndroidStartupState.Ready(started, apkActivation)
            }
            runOnUiThread {
                val started = result.getOrNull()
                if (attempt != startupAttempt || isDestroyed) {
                    started?.product?.close()
                    return@runOnUiThread
                }
                result.onSuccess { ready ->
                    product = ready.product
                    startupState.value = ready
                }.onFailure { failure ->
                    Log.e(LOG_TAG, "Anilib startup failed", failure)
                    startupState.value = AndroidStartupState.Failed(startupFailureMessage(failure))
                }
            }
        }, STARTUP_THREAD_NAME).start()
    }

    @Composable
    private fun StartedContent(runtime: AndroidStartupState.Ready) {
        val started = runtime.product
        val apkActivation = runtime.apkActivation
        val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
        val discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION)
        val extensionRepositories = started.capability(ExtensionRepositoryUiCapabilities.PRESENTATION)
        val reader = started.capability(ReaderUiCapabilities.PRESENTATION)
        val player = started.capability(PlayerUiCapabilities.PRESENTATION)
        val downloads = started.capability(DownloadUiCapabilities.PRESENTATION)
        val backup = started.capability(BackupUiCapabilities.PRESENTATION)
        val tracking = started.capability(TrackerUiCapabilities.PRESENTATION)
        val updates = started.capability(UpdateUiCapabilities.PRESENTATION)
        val apkExtensionPlatform = remember(started, apkActivation) {
            AndroidApkExtensionPlatform(
                this,
                started.capability(NetworkCapabilities.HTTP_CLIENT),
                product = started,
                startupReports = apkActivation.reports,
            )
        }
        val componentCount = started.components().size
        val shareController = remember(started) { AndroidShareController(this) }
        val fullscreenState = remember { mutableStateOf(false) }
        val setPlayerFullscreen: (Boolean) -> Unit = remember {
            { fullscreen ->
                fullscreenState.value = fullscreen
                applyPlayerFullscreen(fullscreen)
            }
        }
        BackHandler(enabled = fullscreenState.value) {
            setPlayerFullscreen(false)
        }
        AnilibApp(
            presentation = presentation,
            discovery = discovery,
            extensionRepositories = extensionRepositories,
            apkExtensionPlatform = apkExtensionPlatform,
            networkMaintenance = started.capability(NetworkCapabilities.MAINTENANCE),
            browserCookies = started.capability(NetworkCapabilities.COOKIES),
            browserRuntimeStatus = BrowserRuntimeStatus.ready(),
            browserDataController = browserDataController,
            browserPlatformController = browserPlatformController,
            settingsPresentation = started.capability(SettingsUiCapabilities.PRESENTATION),
            reader = reader,
            player = player,
            downloads = downloads,
            backup = backup,
            backupImportPicker = backupImportPicker,
            tracking = tracking,
            updates = updates,
            applicationUpdates = started.capability(ApplicationUpdateUiCapabilities.PRESENTATION),
            applicationUpdatePlatformController = applicationUpdatePlatformController,
            httpClient = started.capability(NetworkCapabilities.HTTP_CLIENT),
            shareController = shareController,
            pageDecoder = ::decodePage,
            applyReaderOrientationPolicy = ::applyReaderOrientationPolicy,
            applyPlayerOrientationPolicy = ::applyPlayerOrientationPolicy,
            requestPlayerPictureInPicture = ::enterPlayerPictureInPicture,
            playerFullscreen = fullscreenState.value,
            setPlayerFullscreen = setPlayerFullscreen,
            setPlayerActive = ::setPlayerActive,
            setPlayerBackgroundAudio = ::setPlayerBackgroundAudio,
            enableAndroidPlayerControls = true,
            enableDesktopPlayerControls = false,
            componentCount = componentCount,
            reportUiFailure = { failure ->
                Log.e(LOG_TAG, "Anilib recovered from a Compose UI failure", failure)
                runCatching {
                    started.capability(SettingsCapabilities.DIAGNOSTICS).recordCrash(
                        "Recovered Android UI failure",
                        failure.stackTraceToString(),
                    )
                }
            },
        )
    }

    override fun onDestroy() {
        startupAttempt++
        try {
            applyPlayerFullscreen(false)
            if (isFinishing) setPlayerBackgroundAudio(false)
            product?.close()
            product = null
        } finally {
            super.onDestroy()
        }
    }

    override fun onUserLeaveHint() {
        if (playerActive && !backgroundAudio && !isInPictureInPictureMode) {
            enterPlayerPictureInPicture()
        }
        super.onUserLeaveHint()
    }

    private fun applyReaderOrientationPolicy(policy: ReaderOrientationPolicy) {
        requestedOrientation = when (policy) {
            ReaderOrientationPolicy.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientationPolicy.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientationPolicy.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ReaderOrientationPolicy.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun applyPlayerOrientationPolicy(policy: PlayerOrientationPolicy) {
        requestedOrientation = when (policy) {
            PlayerOrientationPolicy.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlayerOrientationPolicy.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientationPolicy.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientationPolicy.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    @Suppress("DEPRECATION")
    private fun applyPlayerFullscreen(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
            }
            return
        }
        window.decorView.systemUiVisibility = if (fullscreen) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun setPlayerActive(active: Boolean) {
        playerActive = active
        if (!active) setPlayerBackgroundAudio(false)
    }

    private fun setPlayerBackgroundAudio(enabled: Boolean) {
        if (backgroundAudio == enabled) return
        backgroundAudio = enabled
        AndroidBackgroundPlaybackService.setEnabled(this, enabled)
    }

    private fun enterPlayerPictureInPicture() {
        if (!playerActive || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return
        }
        val parameters = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= 31) setSeamlessResizeEnabled(true)
            }
            .build()
        enterPictureInPictureMode(parameters)
    }

    private fun startupFailureMessage(failure: Throwable): String {
        var cause = failure
        repeat(MAX_CAUSE_DEPTH) {
            cause = cause.cause ?: return@repeat
        }
        val type = cause.javaClass.simpleName.ifBlank { "Startup failure" }
        val detail = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return (if (detail.isBlank()) type else "$type: $detail").take(MAX_FAILURE_LENGTH)
    }

    private companion object {
        const val LOG_TAG = "AnilibStartup"
        const val STARTUP_THREAD_NAME = "anilib-android-startup"
        const val NOTIFICATION_PERMISSION_REQUEST = 104
        const val MAX_CAUSE_DEPTH = 8
        const val MAX_FAILURE_LENGTH = 400
    }
}

private sealed interface AndroidStartupState {
    data object Loading : AndroidStartupState

    data class Ready(
        val product: StartedAnilib,
        val apkActivation: AndroidApkSourceActivation,
    ) : AndroidStartupState

    data class Failed(val message: String) : AndroidStartupState
}

private fun decodePage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
