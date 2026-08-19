package fr.vriege.anilib.platform.desktopengine.extension;

import java.nio.file.Path;
import java.util.Objects;

public record InstalledExtension(ExtensionApkMetadata metadata, Path apk, Path archive) {
    public InstalledExtension {
        metadata = Objects.requireNonNull(metadata, "metadata");
        apk = Objects.requireNonNull(apk, "apk").toAbsolutePath().normalize();
        archive = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
    }
}
