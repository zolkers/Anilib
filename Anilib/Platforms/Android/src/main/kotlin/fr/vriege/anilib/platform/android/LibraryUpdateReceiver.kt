package fr.vriege.anilib.platform.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import fr.vriege.anilib.configuration.standard.StandardAnilib
import fr.vriege.anilib.feature.updates.UpdateCapabilities
import fr.vriege.anilib.feature.updates.UpdateInterval
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport
import java.time.Instant
import java.util.concurrent.CompletableFuture

class LibraryUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        schedule(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CompletableFuture.runAsync {
            runCatching { refreshIfDue(context.applicationContext) }
            pendingResult.finish()
        }
    }

    private fun refreshIfDue(context: Context) {
        StandardAnilib.start(
            context.filesDir.toPath(),
            UrlConnectionHttpTransport(),
            AndroidLibraryUpdateNotifier(context),
            emptyList(),
        ).use { product ->
            val updates = product.capability(UpdateCapabilities.SERVICE)
            val snapshot = updates.snapshot()
            if (snapshot.policy().interval() == UpdateInterval.MANUAL) return
            val dueAt = snapshot.nextRunAt().orElse(null)
            if (snapshot.lastRunAt().isEmpty || dueAt == null || !Instant.now().isBefore(dueAt)) {
                updates.runNow().join()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 4100
        private const val POLL_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val operation = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, LibraryUpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + POLL_INTERVAL_MILLIS,
                POLL_INTERVAL_MILLIS,
                operation,
            )
        }
    }
}
