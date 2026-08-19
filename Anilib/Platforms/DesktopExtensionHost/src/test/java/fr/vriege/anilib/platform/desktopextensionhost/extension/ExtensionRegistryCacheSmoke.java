package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ExtensionRegistryCacheSmoke {
    private ExtensionRegistryCacheSmoke() {
    }

    public static void verify() throws IOException {
        Path directory = Files.createTempDirectory("anilib-extension-registry-cache-");
        try {
            ExtensionRegistry registry = new ExtensionRegistry(directory);
            List<InstalledExtension> first = registry.installed();
            List<InstalledExtension> second = registry.installed();
            if (first != second || !first.isEmpty()) {
                throw new IllegalStateException("Installed extension inventory was rescanned without a mutation");
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
