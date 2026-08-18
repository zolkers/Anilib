module fr.vriege.anilib.feature.discovery.runtime {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.backup.api;
    requires fr.vriege.anilib.feature.library.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.discovery.api;

    exports fr.vriege.anilib.feature.discovery.runtime;
}
