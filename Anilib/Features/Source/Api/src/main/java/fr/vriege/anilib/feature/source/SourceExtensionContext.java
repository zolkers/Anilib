package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.framework.http.AnilibHttpClient;

import java.util.Set;

/** Capability-limited construction context supplied only to an isolated source factory. */
public interface SourceExtensionContext {
    Set<SourcePermission> grantedPermissions();

    AnilibHttpClient httpClient();
}
