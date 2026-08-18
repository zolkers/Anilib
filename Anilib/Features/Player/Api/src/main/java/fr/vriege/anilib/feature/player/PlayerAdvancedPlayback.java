package fr.vriege.anilib.feature.player;

import java.util.Optional;
import java.util.Locale;
import java.util.Set;

public interface PlayerAdvancedPlayback {
    Set<PlayerAdvancedCapability> advancedCapabilities();

    PlayerAdvancedState advancedState();

    default void setLoop(boolean loop) {
        throw unsupported(PlayerAdvancedCapability.LOOP);
    }

    default void restart() {
        throw unsupported(PlayerAdvancedCapability.RESTART);
    }

    default void frameStep() {
        throw unsupported(PlayerAdvancedCapability.FRAME_STEP);
    }

    default void setAudioDelay(long delayMillis) {
        throw unsupported(PlayerAdvancedCapability.AUDIO_DELAY);
    }

    default void setSubtitleDelay(long delayMillis) {
        throw unsupported(PlayerAdvancedCapability.SUBTITLE_DELAY);
    }

    default void setAspectRatio(Optional<String> aspectRatio) {
        throw unsupported(PlayerAdvancedCapability.ASPECT_RATIO);
    }

    default void setDeinterlace(boolean enabled) {
        throw unsupported(PlayerAdvancedCapability.DEINTERLACE);
    }

    private static PlayerException unsupported(PlayerAdvancedCapability capability) {
        return new PlayerException(
                "Player backend does not support " + capability.name().toLowerCase(Locale.ROOT));
    }
}
