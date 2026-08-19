package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.platform.compose.BackupImportPicker
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class DesktopBackupImportPicker : BackupImportPicker {
    override fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit) {
        runCatching {
            val dialog = FileDialog(null as Frame?, "Import Anilib or Aniyomi backup", FileDialog.LOAD)
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

    override fun export(path: Path, onExported: (String) -> Unit, onFailure: (String) -> Unit) {
        runCatching {
            val dialog = FileDialog(null as Frame?, "Export Anilib backup", FileDialog.SAVE)
            try {
                dialog.file = path.fileName.toString()
                dialog.isVisible = true
                val file = dialog.file ?: return
                val target = Path.of(dialog.directory, file)
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                onExported(target.toString())
            } finally {
                dialog.dispose()
            }
        }.onFailure { failure ->
            onFailure(failure.message ?: "The backup could not be exported.")
        }
    }

    override fun share(path: Path, onFailure: (String) -> Unit) {
        runCatching {
            val files = listOf(path.toFile())
            val transferable = object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                    flavor == DataFlavor.javaFileListFlavor

                override fun getTransferData(flavor: DataFlavor): Any {
                    require(isDataFlavorSupported(flavor)) { "Unsupported clipboard flavor" }
                    return files
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
        }.onFailure { failure ->
            onFailure(failure.message ?: "The backup could not be copied for sharing.")
        }
    }
}
