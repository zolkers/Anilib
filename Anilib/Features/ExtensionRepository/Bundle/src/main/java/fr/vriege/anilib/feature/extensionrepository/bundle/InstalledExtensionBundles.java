package fr.vriege.anilib.feature.extensionrepository.bundle;

import fr.vriege.anilib.feature.extensionrepository.runtime.PortableSourceBundleLoadResult;
import fr.vriege.anilib.feature.extensionrepository.runtime.PortableSourceBundleLoader;
import java.nio.file.Path;

public final class InstalledExtensionBundles {
    private InstalledExtensionBundles() {
    }

    public static ExtensionBundleSelection select(Path installationDirectory) {
        PortableSourceBundleLoadResult loaded = new PortableSourceBundleLoader(installationDirectory).load();
        return new ExtensionBundleSelection(loaded.bundles(), loaded.failures());
    }
}
