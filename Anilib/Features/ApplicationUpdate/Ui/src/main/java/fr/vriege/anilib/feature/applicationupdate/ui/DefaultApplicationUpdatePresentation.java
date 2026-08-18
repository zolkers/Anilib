package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateService;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification;

import java.nio.file.Path;
import java.util.Objects;

public final class DefaultApplicationUpdatePresentation implements ApplicationUpdatePresentation {
    private final ApplicationUpdateService service;

    public DefaultApplicationUpdatePresentation(ApplicationUpdateService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public ApplicationUpdateSnapshot snapshot() {
        return service.snapshot();
    }

    @Override
    public ApplicationUpdateSnapshot checkNow() {
        return service.checkNow();
    }

    @Override
    public ApplicationUpdateSnapshot setChannel(ApplicationUpdateChannel channel) {
        return service.setChannel(channel);
    }

    @Override
    public ApplicationUpdateVerification verifyDownloadedArtifact(Path artifact) {
        return service.verifyDownloadedArtifact(artifact);
    }
}
