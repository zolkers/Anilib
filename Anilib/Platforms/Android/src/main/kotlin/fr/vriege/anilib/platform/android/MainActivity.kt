package fr.vriege.anilib.platform.android

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.graphics.BitmapFactory
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities
import fr.vriege.anilib.feature.network.NetworkCapabilities
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities
import fr.vriege.anilib.feature.player.PlayerOrientationPolicy
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities
import fr.vriege.anilib.feature.updates.ui.UpdateUiCapabilities
import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdateUiCapabilities
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport
import fr.vriege.anilib.platform.compose.AnilibApp
import fr.vriege.anilib.platform.compose.ComposePlayerBackend
import fr.vriege.anilib.platform.compose.BrowserRuntimeStatus

class MainActivity : ComponentActivity() {
    private var product: AutoCloseable? = null
    private var playerActive = false
    private var backgroundAudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LibraryUpdateReceiver.schedule(this)
        val packagedVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
        System.setProperty("anilib.version", packagedVersion)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }

        val apkActivation = AndroidAniyomiSourceRuntime(this).prepare()
        val started = StandardAnilib.start(
            filesDir.toPath(),
            UrlConnectionHttpTransport(),
            ComposePlayerBackend(),
            AndroidLibraryUpdateNotifier(this),
            AndroidNetworkStatus(this),
            apkActivation.bundles,
        )
        product = started
        val presentation = started.capability(LibraryUiCapabilities.PRESENTATION)
        val discovery = started.capability(DiscoveryUiCapabilities.PRESENTATION)
        val extensionRepositories = started.capability(ExtensionRepositoryUiCapabilities.PRESENTATION)
        val reader = started.capability(ReaderUiCapabilities.PRESENTATION)
        val player = started.capability(PlayerUiCapabilities.PRESENTATION)
        val downloads = started.capability(DownloadUiCapabilities.PRESENTATION)
        val backup = started.capability(BackupUiCapabilities.PRESENTATION)
        val tracking = started.capability(TrackerUiCapabilities.PRESENTATION)
        val updates = started.capability(UpdateUiCapabilities.PRESENTATION)
        val apkExtensionPlatform = AndroidApkExtensionPlatform(
            this,
            started.capability(NetworkCapabilities.HTTP_CLIENT),
            startupReports = apkActivation.reports,
        )
        val browserDataController = AndroidBrowserDataController(this)
        val browserPlatformController = AndroidBrowserPlatformController()
        val backupImportPicker = AndroidBackupImportPicker(this)
        val componentCount = started.components().size
        setContent {
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
                httpClient = started.capability(NetworkCapabilities.HTTP_CLIENT),
                shareController = AndroidShareController(this),
                pageDecoder = ::decodePage,
                applyReaderOrientationPolicy = ::applyReaderOrientationPolicy,
                applyPlayerOrientationPolicy = ::applyPlayerOrientationPolicy,
                requestPlayerPictureInPicture = ::enterPlayerPictureInPicture,
                setPlayerActive = ::setPlayerActive,
                setPlayerBackgroundAudio = ::setPlayerBackgroundAudio,
                enableAndroidPlayerControls = true,
                enableDesktopPlayerControls = false,
                componentCount = componentCount,
            )
        }
    }

    override fun onDestroy() {
        try {
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

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 104
    }
}

private fun decodePage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
