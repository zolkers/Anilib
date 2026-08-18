package fr.vriege.anilib.feature.applicationupdate;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class ApplicationUpdateCapabilities {
    public static final CapabilityKey<ApplicationUpdateService> SERVICE =
            CapabilityKey.of("feature.application-update.service", ApplicationUpdateService.class);

    private ApplicationUpdateCapabilities() {
    }
}
