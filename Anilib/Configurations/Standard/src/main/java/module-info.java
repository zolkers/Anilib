module fr.vriege.anilib.configuration.standard {
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.kernel.runtime;
    requires fr.vriege.anilib.feature.library.bundle;
    requires fr.vriege.anilib.feature.source.bundle;
    requires fr.vriege.anilib.feature.localsource.bundle;

    exports fr.vriege.anilib.configuration.standard;
}
