package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.framework.http.AnilibHttpClient;

import java.util.Set;

public interface TrackerExtensionContext {
    Set<TrackerPermission> grantedPermissions();

    AnilibHttpClient httpClient();
}
