package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class ReaderUiCapabilities {
    public static final CapabilityKey<ReaderPresentation> PRESENTATION =
            CapabilityKey.of("feature.reader.presentation", ReaderPresentation.class);

    private ReaderUiCapabilities() {
    }
}
