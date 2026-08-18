package fr.vriege.anilib.feature.settings;

import java.nio.file.Path;
import java.util.Set;

public interface DiagnosticService {
    DiagnosticSnapshot snapshot();

    void recordLog(String message);

    void recordCrash(String summary, String details);

    Path export();

    DiagnosticResetPlan planReset(Set<DiagnosticResetArea> areas);

    void executeReset(String confirmationToken);
}
