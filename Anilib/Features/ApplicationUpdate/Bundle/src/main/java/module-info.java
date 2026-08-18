module fr.vriege.anilib.feature.applicationupdate.bundle {
    requires fr.vriege.anilib.foundation;
    requires fr.vriege.anilib.framework.http.api;
    requires transitive fr.vriege.anilib.kernel.api;
    requires fr.vriege.anilib.feature.network.api;
    requires transitive fr.vriege.anilib.feature.applicationupdate.api;
    requires fr.vriege.anilib.feature.applicationupdate.runtime;
    requires fr.vriege.anilib.feature.applicationupdate.ui;

    exports fr.vriege.anilib.feature.applicationupdate.bundle;
}
