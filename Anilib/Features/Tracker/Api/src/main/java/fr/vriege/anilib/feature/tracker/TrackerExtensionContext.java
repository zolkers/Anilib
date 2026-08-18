package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.framework.http.AnilibHttpClient;

import java.util.Set;

/** Capability view supplied to a tracker factory after graph validation. */
public interface TrackerExtensionContext {
    Set<TrackerPermission> grantedPermissions();

    AnilibHttpClient httpClient();
}
