package fr.vriege.anilib.kernel;

/** The sole runtime extension unit selected by an Anilib configuration. */
public interface AnilibPlugin {
    PluginManifest manifest();

    void install(PluginInstallationContext context) throws Exception;
}
