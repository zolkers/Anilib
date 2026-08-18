module fr.vriege.anilib.feature.extensionrepository.runtime {
    requires fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.framework.http.api;
    requires fr.vriege.anilib.feature.source.api;
    requires transitive fr.vriege.anilib.feature.extensionrepository.api;

    exports fr.vriege.anilib.feature.extensionrepository.runtime;
}
