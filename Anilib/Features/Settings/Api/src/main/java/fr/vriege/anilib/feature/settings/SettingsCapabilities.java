package fr.vriege.anilib.feature.settings;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class SettingsCapabilities {
    public static final CapabilityKey<SettingsService> SERVICE =
            CapabilityKey.of("feature.settings.service", SettingsService.class);
    public static final CapabilityKey<UnusedDataRegistrar> UNUSED_DATA_REGISTRAR =
            CapabilityKey.of("feature.settings.unused-data-registrar", UnusedDataRegistrar.class);

    private SettingsCapabilities() {
    }
}
