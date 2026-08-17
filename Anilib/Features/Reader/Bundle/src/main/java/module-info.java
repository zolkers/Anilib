module fr.vriege.anilib.feature.reader.bundle {
    requires fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.reader.api;
    requires fr.vriege.anilib.feature.reader.runtime;
    requires fr.vriege.anilib.feature.reader.ui;

    exports fr.vriege.anilib.feature.reader.bundle;
}
