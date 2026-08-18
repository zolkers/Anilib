package fr.vriege.anilib.platform.compose

import java.nio.file.Path

interface BackupImportPicker {
    fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit)

    fun export(path: Path, onExported: (String) -> Unit, onFailure: (String) -> Unit)

    fun share(path: Path, onFailure: (String) -> Unit)

    fun release(path: Path)
}
