package fr.vriege.anilib.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.vriege.anilib.feature.updates.LibraryUpdateNotification
import fr.vriege.anilib.feature.updates.LibraryUpdateNotificationType
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier
import java.util.concurrent.atomic.AtomicInteger

/** Android notification-channel adapter; update behavior remains in shared Java. */
class AndroidLibraryUpdateNotifier(context: Context) : LibraryUpdateNotifier {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(NotificationManager::class.java)
    private val nextNotice = AtomicInteger(NEW_CONTENT_NOTIFICATION)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(PROGRESS_CHANNEL, "Library update progress", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(
                    NEW_CONTENT_CHANNEL,
                    "New chapters and episodes",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(FAILURE_CHANNEL, "Library update errors", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    override fun available(): Boolean = manager.areNotificationsEnabled()

    override fun publish(notification: LibraryUpdateNotification) {
        when (notification.type()) {
            LibraryUpdateNotificationType.CLEAR_PROGRESS -> manager.cancel(PROGRESS_NOTIFICATION)
            LibraryUpdateNotificationType.PROGRESS -> manager.notify(
                PROGRESS_NOTIFICATION,
                build(notification, PROGRESS_CHANNEL)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(notification.total(), notification.completed(), notification.total() == 0)
                    .build(),
            )
            LibraryUpdateNotificationType.NEW_CONTENT -> manager.notify(
                nextNotice.getAndIncrement(),
                build(notification, NEW_CONTENT_CHANNEL).setAutoCancel(true).build(),
            )
            LibraryUpdateNotificationType.FAILURE -> manager.notify(
                FAILURE_NOTIFICATION,
                build(notification, FAILURE_CHANNEL).setAutoCancel(true).build(),
            )
        }
    }

    override fun close() {
        manager.cancel(PROGRESS_NOTIFICATION)
    }

    private fun build(notification: LibraryUpdateNotification, channel: String): Notification.Builder =
        Notification.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(notification.title())
            .setContentText(notification.message())
            .setStyle(Notification.BigTextStyle().bigText(notification.message()))
            .setContentIntent(openApplication())

    private fun openApplication(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val PROGRESS_CHANNEL = "library_update_progress"
        const val NEW_CONTENT_CHANNEL = "library_update_new_content"
        const val FAILURE_CHANNEL = "library_update_failures"
        const val PROGRESS_NOTIFICATION = 4100
        const val NEW_CONTENT_NOTIFICATION = 4200
        const val FAILURE_NOTIFICATION = 4300
    }
}
