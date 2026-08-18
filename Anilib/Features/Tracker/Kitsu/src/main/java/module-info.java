module fr.vriege.anilib.feature.tracker.kitsu {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.http.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.tracker.api;
    requires fr.vriege.anilib.feature.tracker.providersupport;

    exports fr.vriege.anilib.feature.tracker.kitsu;
}
