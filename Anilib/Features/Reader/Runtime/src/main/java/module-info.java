module fr.vriege.anilib.feature.reader.runtime {
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires transitive fr.vriege.anilib.feature.reader.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.source.api;

    exports fr.vriege.anilib.feature.reader.runtime;
}
