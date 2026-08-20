module fr.vriege.anilib.feature.downloads.runtime {
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires transitive fr.vriege.anilib.feature.downloads.api;
    requires fr.vriege.anilib.feature.library.api;
    requires transitive fr.vriege.anilib.feature.reader.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.updates.api;

    exports fr.vriege.anilib.feature.downloads.runtime;
}
