package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.feature.extensionrepository.runtime.MiwayomiSourceBridge;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.net.URI;
import java.util.List;
import java.util.Set;

final class MiwayomiDesktopApkEngineClient implements DesktopApkEngineClient {
    private final MiwayomiSourceBridge bridge;

    MiwayomiDesktopApkEngineClient(MiwayomiSourceBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void requireHealthy() {
        bridge.requireHealthy();
    }

    @Override
    public void saveRepositories(List<URI> repositories) {
        bridge.saveRepositories(repositories);
    }

    @Override
    public List<AnilibPlugin> sourceBundles() {
        return bridge.sourceBundles();
    }

    @Override
    public Set<String> installedPackageNames() {
        return bridge.installedPackageNames();
    }

    @Override
    public String install(URI artifact) {
        return bridge.install(artifact);
    }

    @Override
    public String uninstall(String packageName) {
        return bridge.uninstall(packageName);
    }
}
