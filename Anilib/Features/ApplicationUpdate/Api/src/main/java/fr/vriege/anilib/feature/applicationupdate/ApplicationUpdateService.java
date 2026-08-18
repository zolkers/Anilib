package fr.vriege.anilib.feature.applicationupdate;

import java.nio.file.Path;

public interface ApplicationUpdateService {
    ApplicationUpdateSnapshot snapshot();

    ApplicationUpdateSnapshot checkNow();

    ApplicationUpdateSnapshot setChannel(ApplicationUpdateChannel channel);

    ApplicationUpdateVerification verifyDownloadedArtifact(Path artifact);
}
