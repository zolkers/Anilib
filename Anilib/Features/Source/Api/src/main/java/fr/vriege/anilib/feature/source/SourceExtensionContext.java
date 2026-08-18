package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.framework.http.AnilibHttpClient;

import java.util.Set;

public interface SourceExtensionContext {
    Set<SourcePermission> grantedPermissions();

    AnilibHttpClient httpClient();
}
