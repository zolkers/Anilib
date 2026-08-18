package fr.vriege.anilib.feature.player;

public final class PlayerBackends {
    private static final PlayerBackend UNAVAILABLE = new UnavailablePlayerBackend();

    private PlayerBackends() {
    }

    public static PlayerBackend unavailable() {
        return UNAVAILABLE;
    }
}
