package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.platform.compose.BackupImportPicker
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

internal class DesktopBackupImportPicker : BackupImportPicker {
    override fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit) {
        runCatching {
            val dialog = FileDialog(null as Frame?, "Import Aniyomi backup", FileDialog.LOAD)
            try {
                dialog.isVisible = true
                val file = dialog.file ?: return
                onSelected(Path.of(dialog.directory, file))
            } finally {
                dialog.dispose()
            }
        }.onFailure { failure ->
            onFailure(failure.message ?: "The backup file picker could not be opened.")
        }
    }

    override fun release(path: Path) = Unit
}
