module fr.vriege.anilib.feature.downloads.bundle {
    requires fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.kernel.api;
    requires transitive fr.vriege.anilib.feature.downloads.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.network.api;
    requires fr.vriege.anilib.feature.reader.api;
    requires fr.vriege.anilib.feature.settings.api;
    requires fr.vriege.anilib.feature.source.api;
    requires fr.vriege.anilib.feature.downloads.runtime;
    requires fr.vriege.anilib.feature.downloads.ui;

    exports fr.vriege.anilib.feature.downloads.bundle;
}
