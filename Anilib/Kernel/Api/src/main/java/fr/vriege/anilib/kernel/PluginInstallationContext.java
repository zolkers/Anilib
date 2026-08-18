package fr.vriege.anilib.kernel;

public interface PluginInstallationContext {
    <T> T require(CapabilityKey<T> key);

    <T> void publish(CapabilityKey<T> key, T value);

    <T> void contribute(ContributionPoint<T> point, int priority, T value);

    <T extends AutoCloseable> T own(T resource);

    void onClose(Runnable cleanup);
}
