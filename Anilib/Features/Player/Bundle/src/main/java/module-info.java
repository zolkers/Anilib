module fr.vriege.anilib.feature.player.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.settings.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.player.api;
    requires fr.vriege.anilib.feature.player.runtime;
    requires fr.vriege.anilib.feature.player.ui;

    exports fr.vriege.anilib.feature.player.bundle;
}
