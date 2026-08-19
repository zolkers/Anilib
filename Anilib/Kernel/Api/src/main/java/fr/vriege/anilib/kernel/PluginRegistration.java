package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

public interface PluginRegistration extends AutoCloseable {
    ComponentDescriptor component();

    @Override
    void close();
}
