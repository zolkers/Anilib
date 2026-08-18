package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class UpdateUiCapabilities {
    public static final CapabilityKey<UpdatePresentation> PRESENTATION =
            CapabilityKey.of("feature.updates.ui.presentation", UpdatePresentation.class);

    private UpdateUiCapabilities() {
    }
}
