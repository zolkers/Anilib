module fr.vriege.anilib.feature.extensionrepository.ui {
    requires transitive fr.vriege.anilib.framework.localization.api;
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires fr.vriege.anilib.foundation;
    requires transitive fr.vriege.anilib.kernel.api;
    requires transitive fr.vriege.anilib.feature.extensionrepository.api;
    requires transitive fr.vriege.anilib.feature.settings.api;

    exports fr.vriege.anilib.feature.extensionrepository.ui;
}
