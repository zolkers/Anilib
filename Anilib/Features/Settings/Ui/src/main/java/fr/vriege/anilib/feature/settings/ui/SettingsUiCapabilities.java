package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class SettingsUiCapabilities {
    public static final CapabilityKey<SettingsPresentation> PRESENTATION =
            CapabilityKey.of("feature.settings.presentation", SettingsPresentation.class);

    private SettingsUiCapabilities() {
    }
}
