module fr.vriege.anilib.feature.updates.runtime {
    requires fr.vriege.anilib.framework.backup.api;
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.updates.api;

    exports fr.vriege.anilib.feature.updates.runtime;
}
