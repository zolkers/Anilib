package fr.vriege.anilib.kernel;

public interface AnilibPlugin {
    PluginManifest manifest();

    void install(PluginInstallationContext context) throws Exception;
}
