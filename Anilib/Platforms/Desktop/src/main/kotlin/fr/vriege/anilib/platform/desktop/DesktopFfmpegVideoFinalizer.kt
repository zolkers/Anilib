package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.feature.downloads.DownloadException
import fr.vriege.anilib.feature.downloads.VideoDownloadFinalizer
import fr.vriege.anilib.feature.downloads.VideoDownloadFinalizer.VideoFinalizationRequest
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

internal class DesktopFfmpegVideoFinalizer private constructor(
    private val ffmpeg: Path?,
    private val ffprobe: Path?,
) : VideoDownloadFinalizer {
    override fun available(): Boolean = ffmpeg.isExecutableFile() && ffprobe.isExecutableFile()

    override fun finalizeVideo(
        request: VideoFinalizationRequest,
        cancelled: BooleanSupplier,
    ) {
        val ffmpegExecutable = ffmpeg.takeIf { it.isExecutableFile() }
            ?: throw DownloadException("FFmpeg is not available in this Anilib installation")
        val ffprobeExecutable = ffprobe.takeIf { it.isExecutableFile() }
            ?: throw DownloadException("FFprobe is not available in this Anilib installation")
        if (!Files.isRegularFile(request.input())) {
            throw DownloadException("Downloaded video input is missing")
        }
        val output = request.output()
        val temporary = output.resolveSibling("${output.fileName}.partial.mp4")
        val log = output.resolveSibling("${output.fileName}.ffmpeg.log")
        try {
            Files.createDirectories(output.parent)
            Files.deleteIfExists(temporary)
            runProcess(
                finalizationCommand(ffmpegExecutable, request.input(), temporary),
                output.parent,
                log,
                cancelled,
                "FFmpeg could not finalize this video",
            )
            runProcess(
                listOf(
                    ffprobeExecutable.toString(),
                    "-v",
                    "error",
                    "-select_streams",
                    "v:0",
                    "-show_entries",
                    "stream=index",
                    "-of",
                    "csv=p=0",
                    temporary.toString(),
                ),
                output.parent,
                log,
                cancelled,
                "FFprobe rejected the finalized video",
                requireOutput = true,
            )
            moveAtomically(temporary, output)
        } catch (exception: IOException) {
            throw DownloadException("Unable to finalize downloaded video", exception)
        } finally {
            deleteQuietly(temporary)
            deleteQuietly(log)
        }
    }

    private fun runProcess(
        command: List<String>,
        directory: Path,
        log: Path,
        cancelled: BooleanSupplier,
        failureMessage: String,
        requireOutput: Boolean = false,
    ) {
        if (cancelled.asBoolean) {
            throw DownloadException("Video finalization was cancelled")
        }
        val process = try {
            ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        } catch (exception: IOException) {
            throw DownloadException("Unable to start the bundled media tool", exception)
        }
        try {
            while (!process.waitFor(PROCESS_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (cancelled.asBoolean) {
                    stop(process)
                    throw DownloadException("Video finalization was cancelled")
                }
            }
        } catch (exception: InterruptedException) {
            stop(process)
            Thread.currentThread().interrupt()
            throw DownloadException("Video finalization was interrupted", exception)
        }
        if (process.exitValue() != 0 || requireOutput && readLog(log).isBlank()) {
            val detail = readLog(log).takeIf(String::isNotBlank)
            throw DownloadException(detail?.let { "$failureMessage: $it" } ?: failureMessage)
        }
    }

    private fun stop(process: Process) {
        process.destroy()
        try {
            if (!process.waitFor(PROCESS_STOP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        } catch (exception: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }

    private fun readLog(log: Path): String = try {
        if (Files.isRegularFile(log)) {
            Files.newInputStream(log).use { input ->
                String(input.readNBytes(MAXIMUM_LOG_BYTES), Charsets.UTF_8).trim()
            }
        } else {
            ""
        }
    } catch (_: IOException) {
        ""
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            // A later retry uses a unique managed path and replaces stale output safely.
        }
    }

    companion object {
        private const val PROCESS_POLL_MILLIS = 250L
        private const val PROCESS_STOP_GRACE_MILLIS = 2_000L
        private const val MAXIMUM_LOG_BYTES = 32 * 1024

        internal fun finalizationCommand(
            executable: Path,
            input: Path,
            output: Path,
        ): List<String> = buildList {
            add(executable.toString())
            addAll(listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y"))
            if (input.fileName.toString().lowercase(Locale.ROOT).endsWith(".m3u8")) {
                addAll(
                    listOf(
                        "-protocol_whitelist",
                        "file,crypto,data",
                        "-allowed_extensions",
                        "ALL",
                    ),
                )
            }
            addAll(
                listOf(
                    "-i",
                    input.toString(),
                    "-map",
                    "0:v?",
                    "-map",
                    "0:a?",
                    "-map",
                    "0:s?",
                    "-map_metadata",
                    "0",
                    "-map_chapters",
                    "0",
                    "-c:v",
                    "copy",
                    "-c:a",
                    "copy",
                    "-c:s",
                    "mov_text",
                    "-movflags",
                    "+faststart",
                    "-f",
                    "mp4",
                    output.toString(),
                ),
            )
        }

        fun resolve(): DesktopFfmpegVideoFinalizer {
            val executableSuffix = if (isWindows()) ".exe" else ""
            val configuredFfmpeg = configuredPath("anilib.ffmpeg.path")
            val configuredFfprobe = configuredPath("anilib.ffprobe.path")
            if (configuredFfmpeg != null || configuredFfprobe != null) {
                return DesktopFfmpegVideoFinalizer(configuredFfmpeg, configuredFfprobe)
            }
            val resources = configuredPath("compose.application.resources.dir")
            val binaryDirectory = resources?.resolve("ffmpeg")?.resolve("bin")
            return DesktopFfmpegVideoFinalizer(
                binaryDirectory?.resolve("ffmpeg$executableSuffix"),
                binaryDirectory?.resolve("ffprobe$executableSuffix"),
            )
        }

        private fun configuredPath(property: String): Path? = System.getProperty(property)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()

        private fun isWindows(): Boolean = System.getProperty("os.name", "")
            .lowercase(Locale.ROOT)
            .contains("windows")

        private fun Path?.isExecutableFile(): Boolean = this != null &&
            Files.isRegularFile(this) &&
            (isWindows() || Files.isExecutable(this))
    }
}
