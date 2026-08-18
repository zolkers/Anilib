module fr.vriege.anilib.feature.backup.bundle {
    requires fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.discovery.api;
    requires fr.vriege.anilib.feature.backup.api;
    requires fr.vriege.anilib.feature.backup.runtime;
    requires fr.vriege.anilib.feature.backup.ui;

    exports fr.vriege.anilib.feature.backup.bundle;
}
