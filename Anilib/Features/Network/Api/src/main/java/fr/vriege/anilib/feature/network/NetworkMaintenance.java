package fr.vriege.anilib.feature.network;

import java.net.URI;
import java.util.List;

public interface NetworkMaintenance {
    NetworkPolicy policy();

    void savePolicy(NetworkPolicy policy);

    NetworkDiagnostic diagnose(String sourceId, URI endpoint);

    List<NetworkDiagnostic> diagnostics();

    void clearCookies();

    void clearResponseCache();
}
