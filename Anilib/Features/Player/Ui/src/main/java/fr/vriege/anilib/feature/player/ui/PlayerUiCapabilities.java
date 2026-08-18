package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable platform-neutral presentation capability for Player screens. */
public final class PlayerUiCapabilities {
    public static final CapabilityKey<PlayerPresentation> PRESENTATION =
            CapabilityKey.of("feature.player.ui.presentation", PlayerPresentation.class);

    private PlayerUiCapabilities() {
    }
}
