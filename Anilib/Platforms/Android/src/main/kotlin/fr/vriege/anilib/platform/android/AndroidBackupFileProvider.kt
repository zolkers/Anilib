package fr.vriege.anilib.platform.android

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

internal class AndroidBackupFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Backup shares are read-only" }
        return ParcelFileDescriptor.open(resolve(uri).toFile(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val path = resolve(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> path.fileName.toString()
                OpenableColumns.SIZE -> Files.size(path)
                else -> null
            }
        })
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun resolve(uri: Uri): Path {
        val androidContext = requireNotNull(context) { "Backup provider is not attached" }
        require(uri.authority == "${androidContext.packageName}.backup-files") { "Invalid backup authority" }
        val encoded = uri.pathSegments.singleOrNull() ?: error("Invalid backup share URI")
        val relative = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        val root = androidContext.filesDir.toPath().toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && path.parent != root.parent) { "Backup path escapes application files" }
        require(Files.isRegularFile(path) && !Files.isSymbolicLink(path)) { "Backup share is not a regular file" }
        return path
    }

    companion object {
        fun uriFor(context: Context, path: Path): Uri {
            val root = context.filesDir.toPath().toAbsolutePath().normalize()
            val normalized = path.toAbsolutePath().normalize()
            require(normalized.startsWith(root) && Files.isRegularFile(normalized)) {
                "Only managed application backups can be shared"
            }
            val relative = root.relativize(normalized).toString()
            val encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(relative.toByteArray(StandardCharsets.UTF_8))
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.backup-files")
                .appendPath(encoded)
                .build()
        }
    }
}
