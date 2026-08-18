package fr.vriege.anilib.feature.backup.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class BackupUiCapabilities {
    public static final CapabilityKey<BackupPresentation> PRESENTATION =
            CapabilityKey.of("feature.backup.ui.presentation", BackupPresentation.class);

    private BackupUiCapabilities() {
    }
}
