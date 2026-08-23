module fr.vriege.anilib.feature.tracker.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.backup.api;
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.player.api;
    requires fr.vriege.anilib.feature.reader.api;
    requires fr.vriege.anilib.feature.settings.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.tracker.api;
    requires fr.vriege.anilib.feature.tracker.runtime;
    requires fr.vriege.anilib.feature.tracker.ui;

    exports fr.vriege.anilib.feature.tracker.bundle;
}
