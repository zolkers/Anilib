package fr.vriege.anilib.platform.compose

import java.nio.file.Path

interface BackupImportPicker {
    fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit)

    fun release(path: Path)
}
