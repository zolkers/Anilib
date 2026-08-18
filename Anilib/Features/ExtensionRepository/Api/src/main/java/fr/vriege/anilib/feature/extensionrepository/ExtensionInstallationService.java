package fr.vriege.anilib.feature.extensionrepository;

import java.util.List;

/** Explicit trust and lifecycle boundary for user-supplied extension artifacts. */
public interface ExtensionInstallationService {
    List<InstalledExtensionPackage> installed();

    List<String> trustedKeyIds();

    void trust(String keyId, String x509PublicKeyBase64);

    boolean forgetTrust(String keyId);

    InstalledExtensionPackage install(ExtensionPackageMetadata extensionPackage);

    InstalledExtensionPackage update(ExtensionPackageMetadata extensionPackage);

    InstalledExtensionPackage setEnabled(String packageName, boolean enabled);

    boolean remove(String packageName);
}
