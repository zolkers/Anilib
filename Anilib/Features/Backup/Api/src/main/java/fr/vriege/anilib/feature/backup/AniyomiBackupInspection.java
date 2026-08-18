package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.nio.file.Path;

public record AniyomiBackupInspection(
        Path path,
        long sizeBytes,
        int mangaCount,
        int animeCount,
        int categoryCount,
        int historyCount,
        int progressCount,
        int skippedEntryCount) {
    public AniyomiBackupInspection {
        path = Preconditions.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (sizeBytes < 0
                || mangaCount < 0
                || animeCount < 0
                || categoryCount < 0
                || historyCount < 0
                || progressCount < 0
                || skippedEntryCount < 0) {
            throw new IllegalArgumentException("Aniyomi backup counts must not be negative");
        }
    }

    public int titleCount() {
        return Math.addExact(mangaCount, animeCount);
    }
}
