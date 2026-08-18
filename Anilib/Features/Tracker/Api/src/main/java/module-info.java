module fr.vriege.anilib.feature.tracker.api {
    requires transitive fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.framework.http.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires transitive fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.network.api;

    exports fr.vriege.anilib.feature.tracker;
}
