module fr.vriege.anilib.feature.updates.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.updates.api;
    requires fr.vriege.anilib.feature.updates.runtime;
    requires fr.vriege.anilib.feature.updates.ui;

    exports fr.vriege.anilib.feature.updates.bundle;
}
