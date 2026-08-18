package fr.vriege.anilib.feature.extensionrepository;

import java.net.URI;
import java.util.List;

public interface ExtensionRepositoryService {
    List<URI> repositories();

    void add(URI indexUri);

    boolean remove(URI indexUri);

    List<ExtensionRepositorySnapshot> snapshots();

    ExtensionRepositorySnapshot refresh(URI indexUri);

    List<ExtensionRepositorySnapshot> refreshAll();

    List<ExtensionPackageMetadata> packages();
}
