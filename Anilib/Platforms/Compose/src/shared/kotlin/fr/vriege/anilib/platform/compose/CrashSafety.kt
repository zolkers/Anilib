package fr.vriege.anilib.platform.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal val LocalUiFailureHandler = compositionLocalOf<(Throwable) -> Unit> {
    { failure -> throw failure }
}

@Composable
internal fun rememberCrashSafeCoroutineScope(): CoroutineScope {
    val report = LocalUiFailureHandler.current
    val handler = remember(report) {
        CoroutineExceptionHandler { _, failure -> reportRecoverable(failure, report) }
    }
    val compositionScope = rememberCoroutineScope { handler }
    val scope = remember(compositionScope, handler) {
        val parentJob = compositionScope.coroutineContext[Job]
        CoroutineScope(compositionScope.coroutineContext + SupervisorJob(parentJob) + handler)
    }
    DisposableEffect(scope) {
        onDispose(scope::cancel)
    }
    return scope
}

@Composable
internal fun CrashSafeLaunchedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    val report = LocalUiFailureHandler.current
    LaunchedEffect(*keys) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            reportRecoverable(failure, report)
        }
    }
}

@Composable
internal fun CrashRecoveryDialog(
    message: String,
    continueApplication: () -> Unit,
    returnToLibrary: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = continueApplication,
        title = { Text("ui.anilib.recovered.from.an.error") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                Text("ui.a.diagnostic.report.was.saved.you.can.continue.or.return.to.the.library")
            }
        },
        confirmButton = {
            TextButton(onClick = continueApplication) { Text("ui.continue") }
        },
        dismissButton = {
            TextButton(onClick = returnToLibrary) { Text("ui.return.to.library") }
        },
    )
}

internal enum class UiNoticeKind {
    INFO,
    ERROR,
}

@Composable
internal fun UiNoticeDialog(
    kind: UiNoticeKind,
    message: String,
    dismiss: () -> Unit,
    retry: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (kind == UiNoticeKind.ERROR) "Something went wrong" else "Information") },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                    retry?.invoke()
                },
            ) { Text(if (retry == null) "OK" else "Retry") }
        },
        dismissButton = {
            if (retry != null) {
                TextButton(onClick = dismiss) { Text("ui.close") }
            }
        },
    )
}

internal fun uiFailureMessage(failure: Throwable): String {
    var cause = failure
    repeat(MAX_CAUSE_DEPTH) {
        cause = cause.cause ?: return@repeat
    }
    val type = cause.javaClass.simpleName.ifBlank { "Unexpected error" }
    val detail = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return (if (detail.isBlank()) type else "$type: $detail").take(MAX_FAILURE_MESSAGE)
}

internal fun reportRecoverable(failure: Throwable, report: (Throwable) -> Unit) {
    if (failure is VirtualMachineError || failure.javaClass.name == THREAD_DEATH_CLASS) throw failure
    report(failure)
}

private const val MAX_CAUSE_DEPTH = 8
private const val MAX_FAILURE_MESSAGE = 400
private const val THREAD_DEATH_CLASS = "java.lang.ThreadDeath"
