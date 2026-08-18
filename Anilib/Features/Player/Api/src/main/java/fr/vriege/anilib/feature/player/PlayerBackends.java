package fr.vriege.anilib.feature.player;

/** Dependency-free backend choices for products without a graphical media engine. */
public final class PlayerBackends {
    private static final PlayerBackend UNAVAILABLE = new UnavailablePlayerBackend();

    private PlayerBackends() {
    }

    public static PlayerBackend unavailable() {
        return UNAVAILABLE;
    }
}
