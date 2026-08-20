module fr.vriege.anilib.feature.backup.runtime {
    requires fr.vriege.anilib.framework.backup.api;
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires transitive fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.backup.api;

    exports fr.vriege.anilib.feature.backup.runtime;
}
