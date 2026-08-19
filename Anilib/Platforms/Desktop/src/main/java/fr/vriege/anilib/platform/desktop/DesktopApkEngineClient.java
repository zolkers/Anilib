package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.kernel.AnilibPlugin;

import java.net.URI;
import java.util.List;
import java.util.Set;

interface DesktopApkEngineClient {
    void requireHealthy();

    void saveRepositories(List<URI> repositories);

    List<AnilibPlugin> sourceBundles();

    Set<String> installedPackageNames();

    String install(URI artifact);

    String uninstall(String packageName);
}
