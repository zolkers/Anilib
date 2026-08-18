package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;
import fr.vriege.anilib.feature.player.PlayerPlayback;
import fr.vriege.anilib.feature.player.PlayerAdvancedCapability;
import fr.vriege.anilib.feature.player.PlayerAdvancedPlayback;
import fr.vriege.anilib.feature.player.PlayerAdvancedState;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerPreferenceStore;
import fr.vriege.anilib.feature.player.PlayerPreferences;
import fr.vriege.anilib.feature.player.PlayerQualityPolicy;
import fr.vriege.anilib.feature.player.PlayerSubtitlePolicy;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PlayerController implements AutoCloseable {
    private final PlayerSession session;
    private final PlayerPreferenceStore preferences;

    PlayerController(PlayerSession session, PlayerPreferenceStore preferences) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        try {
            applyPreferences(this.preferences.snapshot(session.snapshot().libraryItemId()));
        } catch (RuntimeException failure) {
            try {
                session.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public PlayerSessionSnapshot snapshot() {
        return session.snapshot();
    }

    public PlayerPlayback playback() {
        return session.playback();
    }

    public void selectStream(String streamId) {
        session.selectStream(streamId);
    }

    public void selectSubtitle(Optional<String> subtitleId) {
        session.selectSubtitle(subtitleId);
    }

    public PlayerPreferences preferences() {
        return preferences.snapshot(session.snapshot().libraryItemId());
    }

    public boolean hasPreferenceOverride() {
        return preferences.hasOverride(session.snapshot().libraryItemId());
    }

    public void setPreferences(PlayerPreferences value, boolean titleOverride) {
        Objects.requireNonNull(value, "preferences must not be null");
        if (titleOverride) {
            preferences.saveOverride(session.snapshot().libraryItemId(), value);
        } else {
            preferences.save(value);
            preferences.clearOverride(session.snapshot().libraryItemId());
        }
        applyPreferences(value);
    }

    public void clearPreferenceOverride() {
        preferences.clearOverride(session.snapshot().libraryItemId());
        applyPreferences(preferences.snapshot(session.snapshot().libraryItemId()));
    }

    public void play() {
        session.play();
    }

    public void pause() {
        session.pause();
    }

    public void seekTo(long positionMillis) {
        session.seekTo(positionMillis);
    }

    public void setVolume(float volume) {
        session.setVolume(volume);
    }

    public void setPlaybackSpeed(float speed) {
        session.setPlaybackSpeed(speed);
    }

    public Set<PlayerAdvancedCapability> advancedCapabilities() {
        if (session.playback() instanceof PlayerAdvancedPlayback advanced) {
            return Set.copyOf(advanced.advancedCapabilities());
        }
        return Set.of();
    }

    public Optional<PlayerAdvancedState> advancedState() {
        if (session.playback() instanceof PlayerAdvancedPlayback advanced) {
            return Optional.of(advanced.advancedState());
        }
        return Optional.empty();
    }

    public void setLoop(boolean loop) {
        advanced(PlayerAdvancedCapability.LOOP).setLoop(loop);
    }

    public void restart() {
        advanced(PlayerAdvancedCapability.RESTART).restart();
    }

    public void frameStep() {
        advanced(PlayerAdvancedCapability.FRAME_STEP).frameStep();
    }

    public void setAudioDelay(long delayMillis) {
        advanced(PlayerAdvancedCapability.AUDIO_DELAY).setAudioDelay(delayMillis);
    }

    public void setSubtitleDelay(long delayMillis) {
        advanced(PlayerAdvancedCapability.SUBTITLE_DELAY).setSubtitleDelay(delayMillis);
    }

    public void setAspectRatio(Optional<String> aspectRatio) {
        advanced(PlayerAdvancedCapability.ASPECT_RATIO).setAspectRatio(aspectRatio);
    }

    public void setDeinterlace(boolean enabled) {
        advanced(PlayerAdvancedCapability.DEINTERLACE).setDeinterlace(enabled);
    }

    public void updatePlayback(long positionMillis, long durationMillis) {
        session.updatePlayback(positionMillis, durationMillis);
    }

    public void markCompleted() {
        session.markCompleted();
    }

    @Override
    public void close() {
        session.close();
    }

    private void applyPreferences(PlayerPreferences value) {
        session.setMediaPolicy(value.decoderPolicy(), value.preferredAudioLanguage());
        selectPreferredStream(value);
        selectPreferredSubtitle(value);
    }

    private void selectPreferredStream(PlayerPreferences value) {
        PlayerSessionSnapshot snapshot = session.snapshot();
        Optional<SourceVideoStream> requested = switch (value.qualityPolicy()) {
            case AUTOMATIC -> Optional.empty();
            case PREFERRED -> snapshot.streams().stream()
                    .filter(stream -> stream.quality().equalsIgnoreCase(value.preferredQuality().orElseThrow()))
                    .findFirst();
            case HIGHEST -> snapshot.streams().stream().max(Comparator.comparingLong(
                    stream -> qualityScore(stream.quality())));
            case LOWEST -> snapshot.streams().stream().min(Comparator.comparingLong(
                    stream -> qualityScore(stream.quality())));
        };
        requested.filter(stream -> !stream.id().equals(snapshot.selectedStreamId()))
                .ifPresent(stream -> session.selectStream(stream.id()));
    }

    private void selectPreferredSubtitle(PlayerPreferences value) {
        SourceVideoStream stream = session.snapshot().selectedStream();
        Optional<String> requested = switch (value.subtitlePolicy()) {
            case OFF -> Optional.empty();
            case FIRST_AVAILABLE -> stream.subtitles().stream().findFirst().map(SourceSubtitleTrack::id);
            case MATCH_LANGUAGE -> matchingSubtitle(stream, value.preferredSubtitleLanguage());
        };
        if (!requested.equals(session.snapshot().selectedSubtitleId())) {
            session.selectSubtitle(requested);
        }
    }

    private static Optional<String> matchingSubtitle(
            SourceVideoStream stream,
            Optional<String> preferredLanguage) {
        if (preferredLanguage.isEmpty()) {
            return Optional.empty();
        }
        String requested = preferredLanguage.orElseThrow();
        return stream.subtitles().stream()
                .filter(track -> track.language().map(language -> language.equalsIgnoreCase(requested)).orElse(false)
                        || track.label().equalsIgnoreCase(requested))
                .findFirst()
                .map(SourceSubtitleTrack::id);
    }

    private static long qualityScore(String quality) {
        long score = 0L;
        long current = 0L;
        boolean digit = false;
        for (int index = 0; index < quality.length(); index++) {
            char character = quality.charAt(index);
            if (Character.isDigit(character)) {
                digit = true;
                current = Math.min(Long.MAX_VALUE / 10L, current) * 10L + character - '0';
            } else if (digit) {
                score = Math.max(score, current);
                current = 0L;
                digit = false;
            }
        }
        return digit ? Math.max(score, current) : score;
    }

    private PlayerAdvancedPlayback advanced(PlayerAdvancedCapability capability) {
        if (!(session.playback() instanceof PlayerAdvancedPlayback advanced)
                || !advanced.advancedCapabilities().contains(capability)) {
            throw new PlayerException(
                    "Player backend does not support " + capability.name().toLowerCase(Locale.ROOT));
        }
        return advanced;
    }
}
