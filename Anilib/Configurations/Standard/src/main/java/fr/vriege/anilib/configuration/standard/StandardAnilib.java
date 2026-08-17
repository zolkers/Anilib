package fr.vriege.anilib.configuration.standard;

import fr.vriege.anilib.feature.library.bundle.LibraryPlugin;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.nio.file.Path;
import java.util.List;

/** Canonical product selection shared by supported platform applications. */
public final class StandardAnilib {
    private StandardAnilib() {
    }

    public static StartedAnilib start(Path dataDirectory) {
        Path libraryFile = dataDirectory.toAbsolutePath().normalize().resolve("library.anilib");
        return new DefaultPluginEngine().start(List.of(new LibraryPlugin(libraryFile)));
    }
}
