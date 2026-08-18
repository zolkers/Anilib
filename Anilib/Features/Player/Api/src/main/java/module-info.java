module fr.vriege.anilib.feature.player.api {
    requires transitive fr.vriege.anilib.framework.backup.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires transitive fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.source.api;

    exports fr.vriege.anilib.feature.player;
}
