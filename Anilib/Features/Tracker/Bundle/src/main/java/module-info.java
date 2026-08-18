module fr.vriege.anilib.feature.tracker.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.tracker.api;
    requires fr.vriege.anilib.feature.tracker.runtime;
    requires fr.vriege.anilib.feature.tracker.ui;

    exports fr.vriege.anilib.feature.tracker.bundle;
}
