package fr.vriege.anilib.feature.network;

@FunctionalInterface
public interface NetworkStatus {
    boolean allowsLargeTransfers();
}
