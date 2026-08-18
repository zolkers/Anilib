package fr.vriege.anilib.kernel;

import java.util.Collection;

public interface PluginEngine {
    StartedAnilib start(Collection<? extends AnilibPlugin> plugins);
}
