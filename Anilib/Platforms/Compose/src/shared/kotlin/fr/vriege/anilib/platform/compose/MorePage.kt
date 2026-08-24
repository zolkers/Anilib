package fr.vriege.anilib.platform.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vriege.anilib.feature.downloads.ui.DownloadPresentation
import fr.vriege.anilib.feature.downloads.DownloadStatus
import fr.vriege.anilib.feature.settings.SettingsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MorePage(
    settings: SettingsSnapshot,
    setIncognitoMode: (Boolean) -> Unit,
    downloads: DownloadPresentation,
    openHistory: () -> Unit,
    openDownloads: () -> Unit,
    openBackup: () -> Unit,
    openTracking: () -> Unit,
    openCategories: () -> Unit,
    openStatistics: () -> Unit,
    openExtensionRepositories: () -> Unit,
    openSettings: () -> Unit,
) {
    val scope = rememberCrashSafeCoroutineScope()
    val queue = rememberDownloadQueueSnapshot(downloads)
    val pendingDownloads = queue?.jobs()?.count {
        it.status() != DownloadStatus.COMPLETED && it.status() != DownloadStatus.CANCELLED
    } ?: 0
    Scaffold(topBar = { TopAppBar(title = { Text("ui.more") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { AnilibSection("ui.quick.filters") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreSwitchRow(
                        "ui.downloaded.only",
                        "ui.use.downloaded.content.without.the.online.fallback",
                        queue?.offlineMode() == true,
                        Icons.Outlined.Download,
                        { enabled ->
                            scope.launch {
                                withContext(Dispatchers.IO) { downloads.setOfflineMode(enabled) }
                            }
                        },
                    )
                    MoreSwitchRow(
                        "ui.incognito.mode",
                        "ui.pause.reading.and.watching.history",
                        settings.incognitoMode(),
                        Icons.Outlined.VisibilityOff,
                        setIncognitoMode,
                    )
                }
            }
            item { AnilibSection("ui.library") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.history",
                        "ui.recently.watched.and.read",
                        Icons.Default.History,
                        openHistory,
                    )
                    MoreRow(
                        "ui.download.queue",
                        if (pendingDownloads == 0) {
                            "ui.no.pending.downloads"
                        } else {
                            UiTranslations.format(
                                "dynamic.pending.downloads",
                                LocalLanguagePack.current,
                                pendingDownloads,
                            )
                        },
                        Icons.Outlined.Download,
                        openDownloads,
                    )
                    MoreRow(
                        "ui.categories",
                        "ui.organize.anime.and.manga.in.your.library",
                        Icons.Outlined.Category,
                        openCategories,
                    )
                    MoreRow(
                        "ui.statistics",
                        "ui.library.and.reading.activity",
                        Icons.Outlined.Assessment,
                        openStatistics,
                    )
                }
            }
            item { AnilibSection("ui.services") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.backup.and.restore",
                        "ui.create.or.restore.a.local.backup",
                        Icons.Outlined.Backup,
                        openBackup,
                    )
                    MoreRow(
                        "ui.tracking",
                        "ui.manage.external.tracking.accounts",
                        Icons.Outlined.Sync,
                        openTracking,
                    )
                    MoreRow(
                        "ui.extension.repositories",
                        "ui.add.compatible.extension.repositories.and.install.sources",
                        Icons.Outlined.Extension,
                        openExtensionRepositories,
                    )
                }
            }
            item { AnilibSection("ui.application") }
            item {
                AnilibGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MoreRow(
                        "ui.settings",
                        "ui.manage.application.preferences",
                        Icons.Outlined.Settings,
                        openSettings,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun MoreSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnilibLeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun MoreRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnilibLeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal enum class MoreDestination {
    HISTORY,
    DOWNLOADS,
    BACKUP,
    TRACKING,
    CATEGORIES,
    STATISTICS,
    EXTENSION_REPOSITORIES,
    SETTINGS,
    ABOUT,
}
