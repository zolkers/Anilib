module fr.vriege.anilib.configuration.standard {
    requires transitive fr.vriege.anilib.framework.http.api;
    requires fr.vriege.anilib.framework.http.runtime;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.kernel.runtime;
    requires fr.vriege.anilib.feature.library.bundle;
    requires fr.vriege.anilib.feature.source.bundle;
    requires fr.vriege.anilib.feature.localsource.bundle;
    requires transitive fr.vriege.anilib.feature.network.api;
    requires fr.vriege.anilib.feature.network.bundle;
    requires fr.vriege.anilib.feature.settings.bundle;
    requires fr.vriege.anilib.feature.discovery.bundle;
    requires fr.vriege.anilib.feature.extensionrepository.bundle;
    requires fr.vriege.anilib.feature.reader.bundle;
    requires fr.vriege.anilib.feature.downloads.bundle;
    requires transitive fr.vriege.anilib.feature.player.api;
    requires fr.vriege.anilib.feature.player.bundle;
    requires transitive fr.vriege.anilib.feature.tracker.api;
    requires fr.vriege.anilib.feature.tracker.bundle;
    requires fr.vriege.anilib.feature.tracker.anilist;
    requires fr.vriege.anilib.feature.tracker.kitsu;
    requires transitive fr.vriege.anilib.feature.updates.api;
    requires fr.vriege.anilib.feature.updates.bundle;
    requires fr.vriege.anilib.feature.applicationupdate.bundle;
    requires fr.vriege.anilib.feature.backup.bundle;

    exports fr.vriege.anilib.configuration.standard;
}
