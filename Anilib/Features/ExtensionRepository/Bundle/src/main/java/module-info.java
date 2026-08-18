module fr.vriege.anilib.feature.extensionrepository.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.http.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.network.api;
    requires transitive fr.vriege.anilib.feature.extensionrepository.api;
    requires fr.vriege.anilib.feature.extensionrepository.runtime;

    exports fr.vriege.anilib.feature.extensionrepository.bundle;
}
