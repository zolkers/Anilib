package fr.vriege.anilib.kernel;

import java.util.Collection;

/** Validates and starts one immutable product graph. */
public interface PluginEngine {
    StartedAnilib start(Collection<? extends AnilibPlugin> plugins);
}
