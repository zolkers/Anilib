module fr.vriege.anilib.feature.tracker.runtime {
    requires fr.vriege.anilib.framework.backup.api;
    requires fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.tracker.api;

    exports fr.vriege.anilib.feature.tracker.runtime;
}
