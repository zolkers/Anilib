package fr.vriege.anilib.platform.compose

import fr.vriege.anilib.feature.player.PlaybackState
import fr.vriege.anilib.feature.player.PlayerAdvancedCapability
import fr.vriege.anilib.feature.player.PlayerAdvancedPlayback
import fr.vriege.anilib.feature.player.PlayerAdvancedState
import fr.vriege.anilib.feature.player.PlayerBackend
import fr.vriege.anilib.feature.player.PlayerException
import fr.vriege.anilib.feature.player.PlayerMedia
import fr.vriege.anilib.feature.player.PlayerPlayback
import fr.vriege.anilib.feature.player.PlayerPlaybackSnapshot
import fr.vriege.anilib.feature.player.PlayerPlaybackStatus
import fr.vriege.anilib.framework.http.runtime.MediaHeaderProxy
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import java.net.URI
import java.nio.file.Path
import java.util.Optional
import kotlin.math.roundToLong

class ComposePlayerBackend : PlayerBackend {
    override fun id(): String = "compose-native"

    override fun available(): Boolean = true

    override fun open(media: PlayerMedia): PlayerPlayback = ComposePlayerPlayback(media)
}

internal class ComposePlayerPlayback(
    private val media: PlayerMedia,
) : PlayerPlayback, PlayerAdvancedPlayback {
    private val headerProxy = if (
        media.stream().headers().isNotEmpty() || media.stream().subtitles().any { it.headers().isNotEmpty() }
    ) {
        MediaHeaderProxy()
    } else {
        null
    }
    private val mediaLocation = if (media.stream().headers().isEmpty()) {
        media.stream().location().playerLocation()
    } else {
        headerProxy!!.route(media.stream().location(), media.stream().headers()).toString()
    }
    private val subtitleLocations = media.stream().subtitles().associate { subtitle ->
        subtitle.id() to if (subtitle.headers().isEmpty()) {
            subtitle.location().playerLocation()
        } else {
            headerProxy!!.route(subtitle.location(), subtitle.headers()).toString()
        }
    }
    @Volatile
    private var state: VideoPlayerState? = null
    private var requestedVolume = 1f
    private var requestedSpeed = 1f
    private var requestedSubtitle = media.subtitleId()
    private var requestedLoop = false
    @Volatile
    private var ended = false
    private var closed = false

    override fun media(): PlayerMedia = synchronized(this) {
        ensureOpen()
        media
    }

    override fun snapshot(): PlayerPlaybackSnapshot = synchronized(this) {
        ensureOpen()
        val player = state ?: return@synchronized PlayerPlaybackSnapshot(
            PlayerPlaybackStatus.LOADING,
            media.startPositionMillis(),
            PlaybackState.UNKNOWN_DURATION,
            requestedVolume,
            requestedSpeed,
            Optional.empty(),
        )
        val error = player.error
        val status = when {
            error != null -> PlayerPlaybackStatus.FAILED
            ended -> PlayerPlaybackStatus.ENDED
            player.isLoading -> PlayerPlaybackStatus.LOADING
            player.isPlaying -> PlayerPlaybackStatus.PLAYING
            else -> PlayerPlaybackStatus.PAUSED
        }
        PlayerPlaybackSnapshot(
            status,
            secondsToMillis(player.currentTime),
            durationMillis(player),
            player.volume,
            player.playbackSpeed,
            Optional.ofNullable(error?.toString()),
        )
    }

    override fun play() = synchronized(this) {
        ensureOpen()
        ended = false
        state?.play()
        Unit
    }

    override fun pause() = synchronized(this) {
        ensureOpen()
        state?.pause()
        Unit
    }

    override fun seekTo(positionMillis: Long) = synchronized(this) {
        ensureOpen()
        require(positionMillis >= 0) { "positionMillis must not be negative" }
        seek(state, positionMillis)
        ended = false
    }

    override fun setVolume(volume: Float) = synchronized(this) {
        ensureOpen()
        require(volume.isFinite() && volume in 0f..1f) { "volume must be between zero and one" }
        requestedVolume = volume
        state?.volume = volume
    }

    override fun setPlaybackSpeed(speed: Float) = synchronized(this) {
        ensureOpen()
        require(speed.isFinite() && speed in 0.25f..2f) {
            "playbackSpeed must be between 0.25 and 2.0"
        }
        requestedSpeed = speed
        state?.playbackSpeed = speed
    }

    override fun selectSubtitle(subtitleId: Optional<String>) = synchronized(this) {
        ensureOpen()
        val requested = subtitleId.map(String::trim).filter(String::isNotEmpty)
        if (requested.isPresent && media.stream().subtitles().none { it.id() == requested.get() }) {
            throw PlayerException("Subtitle does not belong to the selected stream")
        }
        requestedSubtitle = requested
        applySubtitle(state)
    }

    override fun advancedCapabilities(): Set<PlayerAdvancedCapability> = setOf(
        PlayerAdvancedCapability.LOOP,
        PlayerAdvancedCapability.RESTART,
    )

    override fun advancedState(): PlayerAdvancedState = synchronized(this) {
        ensureOpen()
        PlayerAdvancedState(requestedLoop, 0L, 0L, Optional.empty(), false)
    }

    override fun setLoop(loop: Boolean) = synchronized(this) {
        ensureOpen()
        requestedLoop = loop
        state?.loop = loop
    }

    override fun restart() = synchronized(this) {
        ensureOpen()
        ended = false
        state?.restart()
        Unit
    }

    fun attach(player: VideoPlayerState) = synchronized(this) {
        ensureOpen()
        state = player
        ended = false
        player.volume = requestedVolume
        player.playbackSpeed = requestedSpeed
        player.loop = requestedLoop
        player.onPlaybackEnded = { ended = true }
        player.openUri(mediaLocation, InitialPlayerState.PLAY)
        applySubtitle(player)
    }

    fun resumeWhenReady(): Boolean = synchronized(this) {
        ensureOpen()
        if (media.startPositionMillis() <= 0) return true
        val player = state ?: return false
        if (player.duration <= 0.0) return false
        seek(player, media.startPositionMillis())
        true
    }

    fun detach(player: VideoPlayerState) = synchronized(this) {
        if (state === player) {
            player.onPlaybackEnded = null
            player.pause()
            state = null
        }
    }

    private fun applySubtitle(player: VideoPlayerState?) {
        if (player == null) return
        val selectedId = requestedSubtitle.orElse(null)
        val sourceTrack = media.stream().subtitles().firstOrNull { it.id() == selectedId }
        if (sourceTrack == null) {
            player.disableSubtitles()
            return
        }
        player.selectSubtitleTrack(
            SubtitleTrack(
                label = sourceTrack.label(),
                language = sourceTrack.language().orElse("und"),
                src = subtitleLocations.getValue(sourceTrack.id()),
            ),
        )
    }

    private fun seek(player: VideoPlayerState?, positionMillis: Long) {
        if (player == null || player.duration <= 0.0) return
        val duration = durationMillis(player)
        if (duration > 0) {
            player.seekTo((positionMillis.toDouble() / duration * 1000.0).coerceIn(0.0, 1000.0).toFloat())
        }
    }

    private fun durationMillis(player: VideoPlayerState): Long =
        if (player.duration > 0.0) secondsToMillis(player.duration)
        else PlaybackState.UNKNOWN_DURATION

    private fun secondsToMillis(seconds: Double): Long =
        (seconds.coerceAtLeast(0.0) * 1000.0).roundToLong()

    private fun ensureOpen() {
        if (closed) throw PlayerException("Player playback is closed")
    }

    override fun close() = synchronized(this) {
        if (!closed) {
            state?.let { detach(it) }
            headerProxy?.close()
            closed = true
        }
    }
}

private fun URI.playerLocation(): String = if (scheme.equals("file", ignoreCase = true)) {
    Path.of(this).toString()
} else {
    toString()
}
