package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification;

import java.nio.file.Path;

public interface ApplicationUpdatePresentation {
    ApplicationUpdateSnapshot snapshot();

    ApplicationUpdateSnapshot checkNow();

    ApplicationUpdateSnapshot setChannel(ApplicationUpdateChannel channel);

    ApplicationUpdateVerification verifyDownloadedArtifact(Path artifact);
}
