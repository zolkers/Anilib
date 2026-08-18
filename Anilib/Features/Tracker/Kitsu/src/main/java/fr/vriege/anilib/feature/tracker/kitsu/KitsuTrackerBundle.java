package fr.vriege.anilib.feature.tracker.kitsu;

import fr.vriege.anilib.feature.tracker.TrackerExtensionManifest;
import fr.vriege.anilib.feature.tracker.TrackerExtensionPlugin;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerNetworkOrigin;
import fr.vriege.anilib.feature.tracker.TrackerPermission;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.util.Set;

public final class KitsuTrackerBundle implements AnilibPlugin {
    private static final TrackerId TRACKER_ID = TrackerId.of("kitsu");
    private final TrackerExtensionPlugin delegate = new TrackerExtensionPlugin(
            new TrackerExtensionManifest(
                    ComponentDescriptor.of("tracker.kitsu", "Kitsu tracker", "1.0.0"),
                    TRACKER_ID,
                    Set.of(TrackerPermission.NETWORK),
                    Set.of(TrackerNetworkOrigin.of("https", "kitsu.io"))),
            context -> new KitsuTracker(context.httpClient()));

    public KitsuTrackerBundle() {
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
