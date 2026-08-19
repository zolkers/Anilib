package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.feature.settings.DiagnosticService
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane

internal class DesktopUiCrashShield private constructor(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val handler: Thread.UncaughtExceptionHandler,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (Thread.getDefaultUncaughtExceptionHandler() === handler) {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    internal companion object {
        fun install(diagnostics: DiagnosticService): DesktopUiCrashShield {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            lateinit var shield: DesktopUiCrashShield
            val handler = Thread.UncaughtExceptionHandler { thread, failure ->
                if (!recoverableUiThread(thread)) {
                    previous?.uncaughtException(thread, failure) ?: failure.printStackTrace()
                    return@UncaughtExceptionHandler
                }
                runCatching {
                    diagnostics.recordCrash(
                        "Recovered desktop UI exception on ${thread.name}",
                        failure.stackTraceToString(),
                    )
                }
                EventQueue.invokeLater {
                    runCatching {
                        JOptionPane.showMessageDialog(
                            null,
                            uiFailureMessage(failure) +
                                "\n\nAnilib stayed open and saved a diagnostic report.",
                            "Anilib recovered from an error",
                            JOptionPane.ERROR_MESSAGE,
                        )
                    }
                }
            }
            shield = DesktopUiCrashShield(previous, handler)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            return shield
        }

        fun recoverableUiThread(thread: Thread): Boolean =
            thread.name.startsWith("AWT-EventQueue") || thread.name.startsWith("Skiko")

        private fun uiFailureMessage(failure: Throwable): String {
            var cause = failure
            repeat(MAX_CAUSE_DEPTH) {
                cause = cause.cause ?: return@repeat
            }
            val type = cause.javaClass.simpleName.ifBlank { "Unexpected error" }
            val detail = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            return (if (detail.isBlank()) type else "$type: $detail").take(MAX_FAILURE_MESSAGE)
        }

        private const val MAX_CAUSE_DEPTH = 8
        private const val MAX_FAILURE_MESSAGE = 400
    }
}
