package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;

import java.util.List;

public interface StartedAnilib extends AutoCloseable {
    <T> T capability(CapabilityKey<T> key);

    <T> List<Contribution<T>> contributions(ContributionPoint<T> point);

    List<ComponentDescriptor> components();

    PluginRegistration install(AnilibPlugin plugin);

    @Override
    void close();
}
