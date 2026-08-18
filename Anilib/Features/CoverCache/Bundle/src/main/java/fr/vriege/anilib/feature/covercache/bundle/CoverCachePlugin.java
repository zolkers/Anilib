package fr.vriege.anilib.feature.covercache.bundle;

import fr.vriege.anilib.feature.covercache.CoverCacheCapabilities;
import fr.vriege.anilib.feature.covercache.runtime.JdkFileCoverCache;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class CoverCachePlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.cover-cache", "Cover cache", "0.1.0"))
            .provides(CoverCacheCapabilities.CACHE)
            .build();

    private final Path cacheDirectory;

    public CoverCachePlugin(Path cacheDirectory) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        context.publish(CoverCacheCapabilities.CACHE, new JdkFileCoverCache(cacheDirectory));
    }
}
