package fr.vriege.anilib.feature.network;

public final class NetworkStatuses {
    private static final NetworkStatus UNMETERED = () -> true;

    private NetworkStatuses() {
    }

    public static NetworkStatus unmetered() {
        return UNMETERED;
    }
}
