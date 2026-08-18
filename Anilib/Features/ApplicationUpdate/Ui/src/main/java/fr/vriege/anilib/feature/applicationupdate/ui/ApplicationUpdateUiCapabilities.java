package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class ApplicationUpdateUiCapabilities {
    public static final CapabilityKey<ApplicationUpdatePresentation> PRESENTATION =
            CapabilityKey.of("feature.application-update.ui.presentation", ApplicationUpdatePresentation.class);

    private ApplicationUpdateUiCapabilities() {
    }
}
