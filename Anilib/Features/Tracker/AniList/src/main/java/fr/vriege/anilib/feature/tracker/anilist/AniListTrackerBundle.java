package fr.vriege.anilib.feature.tracker.anilist;

import fr.vriege.anilib.feature.tracker.TrackerExtensionManifest;
import fr.vriege.anilib.feature.tracker.TrackerExtensionPlugin;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerNetworkOrigin;
import fr.vriege.anilib.feature.tracker.TrackerPermission;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.net.URI;
import java.util.Set;

public final class AniListTrackerBundle implements AnilibPlugin {
    private static final TrackerId TRACKER_ID = TrackerId.of("anilist");
    private final TrackerExtensionPlugin delegate;

    public AniListTrackerBundle() {
        this("");
    }

    public AniListTrackerBundle(String clientId) {
        this(clientId, AniListTracker.DEFAULT_CALLBACK);
    }

    public AniListTrackerBundle(String clientId, URI callbackUri) {
        delegate = new TrackerExtensionPlugin(
                new TrackerExtensionManifest(
                        ComponentDescriptor.of("tracker.anilist", "AniList tracker", "1.0.0"),
                        TRACKER_ID,
                        Set.of(TrackerPermission.NETWORK),
                        Set.of(TrackerNetworkOrigin.of("https", "graphql.anilist.co"))),
                context -> new AniListTracker(context.httpClient(), clientId, callbackUri));
    }

    @Override
    public PluginManifest manifest() {
        return delegate.manifest();
    }

    @Override
    public void install(PluginInstallationContext context) {
        delegate.install(context);
    }
}
