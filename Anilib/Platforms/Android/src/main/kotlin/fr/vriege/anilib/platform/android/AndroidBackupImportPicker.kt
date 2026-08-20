package fr.vriege.anilib.platform.android

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import fr.vriege.anilib.platform.compose.BackupImportPicker
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import android.net.Uri

internal class AndroidBackupImportPicker(private val activity: ComponentActivity) : BackupImportPicker {
    private val importDirectory = activity.cacheDir.toPath().resolve("backup-imports").normalize()
    private var selectedCallback: ((Path) -> Unit)? = null
    private var failureCallback: ((String) -> Unit)? = null
    private var exportPath: Path? = null
    private var exportCallback: ((String) -> Unit)? = null
    private var exportFailure: ((String) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val selected = selectedCallback
        val failure = failureCallback
        selectedCallback = null
        failureCallback = null
        if (uri == null || selected == null || failure == null) {
            return@registerForActivityResult
        }
        runCatching { materialize(uri) }
            .onSuccess(selected)
            .onFailure { error ->
                failure(error.message ?: "The selected backup could not be read.")
            }
    }
    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val source = exportPath
        val exported = exportCallback
        val failure = exportFailure
        exportPath = null
        exportCallback = null
        exportFailure = null
        if (uri == null || source == null || exported == null || failure == null) {
            return@registerForActivityResult
        }
        runCatching {
            activity.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "The selected destination cannot be opened." }
                Files.newInputStream(source).use { input -> input.copyTo(output) }
            }
            uri.toString()
        }.onSuccess(exported).onFailure { error ->
            failure(error.message ?: "The backup could not be exported.")
        }
    }

    override fun choose(onSelected: (Path) -> Unit, onFailure: (String) -> Unit) {
        if (selectedCallback != null) {
            onFailure("A backup file selection is already open.")
            return
        }
        selectedCallback = onSelected
        failureCallback = onFailure
        runCatching { launcher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) }
            .onFailure { error ->
                selectedCallback = null
                failureCallback = null
                onFailure(error.message ?: "The backup file picker could not be opened.")
            }
    }

    override fun release(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        if (normalized.parent != importDirectory.toAbsolutePath().normalize()) {
            return
        }
        runCatching {
            if (!Files.isSymbolicLink(normalized)) {
                Files.deleteIfExists(normalized)
            }
        }
    }

    override fun export(path: Path, onExported: (String) -> Unit, onFailure: (String) -> Unit) {
        if (exportPath != null) {
            onFailure("A backup export destination is already open.")
            return
        }
        exportPath = path
        exportCallback = onExported
        exportFailure = onFailure
        runCatching { exportLauncher.launch(path.fileName.toString()) }.onFailure { error ->
            exportPath = null
            exportCallback = null
            exportFailure = null
            onFailure(error.message ?: "The Android document picker could not be opened.")
        }
    }

    override fun share(path: Path, onFailure: (String) -> Unit) {
        runCatching {
            val uri = AndroidBackupFileProvider.uriFor(activity, path)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Share Anilib backup"))
        }.onFailure { error ->
            onFailure(error.message ?: "The backup could not be shared.")
        }
    }

    private fun materialize(uri: Uri): Path {
        Files.createDirectories(importDirectory)
        val target = Files.createTempFile(importDirectory, "backup-import-", ".bin")
        try {
            activity.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected backup cannot be opened." }
                Files.newOutputStream(target).use { output ->
                    val buffer = ByteArray(16_384)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_INPUT_BYTES) {
                            throw IOException("Backup exceeds the 256 MB input limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return target
        } catch (failure: Exception) {
            Files.deleteIfExists(target)
            throw failure
        }
    }

    private companion object {
        const val MAX_INPUT_BYTES = 256L * 1024L * 1024L
    }
}
