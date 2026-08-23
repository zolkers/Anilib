package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.tracker.TrackerAiringSchedule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun NextAiringBanner(
    schedule: TrackerAiringSchedule,
    modifier: Modifier = Modifier,
) {
    var now by remember(schedule) { mutableStateOf(Instant.now()) }
    CrashSafeLaunchedEffect(schedule) {
        while (now.isBefore(schedule.airingAt())) {
            val remainingSeconds = Duration.between(now, schedule.airingAt()).seconds
            delay(if (remainingSeconds < 3_600L) 1_000L else 30_000L)
            now = Instant.now()
        }
    }
    val remaining = Duration.between(now, schedule.airingAt())
    if (remaining.isNegative || remaining.isZero) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    UiTranslations.format(
                        "dynamic.next.episode.number",
                        LocalLanguagePack.current,
                        schedule.episode(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    UiTranslations.format(
                        "dynamic.airs.in",
                        LocalLanguagePack.current,
                        countdownText(remaining),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    mediaDateTimeFormatter.format(schedule.airingAt()),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun countdownText(duration: Duration): String {
    var seconds = duration.seconds.coerceAtLeast(0L)
    val days = seconds / 86_400L
    seconds %= 86_400L
    val hours = seconds / 3_600L
    seconds %= 3_600L
    val minutes = seconds / 60L
    seconds %= 60L
    return when {
        days > 0L -> "${days}d ${hours}h ${minutes}m"
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
