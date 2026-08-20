package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionBrowsePreferenceStore;
import fr.vriege.anilib.feature.extensionrepository.ExtensionBrowsePreferences;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageIdentifiers;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateService;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DefaultExtensionRepositoryPresentation
        implements ExtensionRepositoryPresentation, AutoCloseable {
    private final ExtensionRepositoryService service;
    private final ExtensionInstallationService installation;
    private final ExtensionUpdateService updates;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "anilib-extension-repositories");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AutoCloseable updateObservation;
    private final AutoCloseable settingsObservation;
    private final SettingsService settings;
    private final ExtensionBrowsePreferenceStore browsePreferences;

    public DefaultExtensionRepositoryPresentation(
            ExtensionRepositoryService service,
            ExtensionInstallationService installation,
            ExtensionUpdateService updates,
            SettingsService settings,
            ExtensionBrowsePreferenceStore browsePreferences) {
        this.service = Preconditions.requireNonNull(service, "service");
        this.installation = Preconditions.requireNonNull(installation, "installation");
        this.updates = Preconditions.requireNonNull(updates, "updates");
        this.settings = Preconditions.requireNonNull(settings, "settings");
        this.browsePreferences = Preconditions.requireNonNull(browsePreferences, "browsePreferences");
        updateObservation = updates.observe(this::notifyListeners);
        settingsObservation = settings.observe(ignored -> notifyListeners());
    }

    @Override
    public ExtensionRepositoryView snapshot() {
        ExtensionBrowsePreferences preferences = browsePreferences.snapshot();
        List<ExtensionPackageMetadata> availablePackages = service.packages();
        List<String> languages = availablePackages.stream()
                .map(ExtensionPackageMetadata::languageTag)
                .distinct()
                .sorted()
                .toList();
        Set<String> enabledLanguages = effectiveLanguages(preferences, languages);
        List<ExtensionPackageMetadata> filteredPackages = availablePackages.stream()
                .filter(extension -> enabledLanguages.contains(extension.languageTag()))
                .sorted(Comparator
                        .comparing((ExtensionPackageMetadata extension) ->
                                !preferences.pinnedPackages().contains(extension.packageName()))
                        .thenComparing(ExtensionPackageMetadata::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ExtensionPackageMetadata::packageName))
                .toList();
        Map<URI, ExtensionRepositorySnapshot> snapshots = service.snapshots().stream()
                .collect(Collectors.toMap(
                        ExtensionRepositorySnapshot::indexUri,
                        value -> value));
        List<ExtensionRepositoryRow> rows = new ArrayList<>();
        for (URI repository : service.repositories()) {
            ExtensionRepositorySnapshot snapshot = snapshots.get(repository);
            rows.add(snapshot == null
                    ? new ExtensionRepositoryRow(repository, Optional.empty(), 0, Optional.empty())
                    : new ExtensionRepositoryRow(
                            repository,
                            Optional.of(snapshot.fetchedAt()),
                            (int) snapshot.packages().stream()
                                    .filter(extension -> enabledLanguages.contains(extension.languageTag()))
                                    .count(),
                            snapshot.failure()));
        }
        return new ExtensionRepositoryView(
                rows,
                filteredPackages,
                installation.installed(),
                updates.availableUpdates(),
                updates.automaticUpdatesEnabled(),
                settings.snapshot().showAdultContent(),
                languages,
                enabledLanguages,
                preferences.pinnedPackages(),
                installation.trustedKeyIds());
    }

    @Override
    public void add(String indexUrl) {
        service.add(URI.create(Preconditions.requireNonBlank(indexUrl, "indexUrl")));
        notifyListeners();
    }

    @Override
    public boolean remove(String indexUrl) {
        boolean removed = service.remove(URI.create(Preconditions.requireNonBlank(indexUrl, "indexUrl")));
        if (removed) {
            notifyListeners();
        }
        return removed;
    }

    @Override
    public CompletableFuture<ExtensionRepositoryView> refreshAll() {
        return CompletableFuture.supplyAsync(() -> {
            service.refreshAll();
            ExtensionRepositoryView view = snapshot();
            notifyListeners();
            return view;
        }, executor);
    }

    @Override
    public CompletableFuture<ExtensionRepositoryView> updateAllAvailable() {
        return lifecycle(updates::updateAllAvailable);
    }

    @Override
    public void setAutomaticUpdatesEnabled(boolean enabled) {
        updates.setAutomaticUpdatesEnabled(enabled);
        notifyListeners();
    }

    @Override
    public void setLanguageEnabled(String languageTag, boolean enabled) {
        String language = normalizeLanguage(languageTag);
        ExtensionBrowsePreferences current = browsePreferences.snapshot();
        List<String> available = service.packages().stream()
                .map(ExtensionPackageMetadata::languageTag)
                .distinct()
                .sorted()
                .toList();
        if (!available.contains(language)) {
            throw new IllegalArgumentException("Unknown extension language: " + language);
        }
        Set<String> selected = new LinkedHashSet<>(effectiveLanguages(current, available));
        if (enabled) {
            selected.add(language);
        } else {
            if (selected.size() == 1 && selected.contains(language)) {
                throw new IllegalArgumentException("At least one extension language must remain enabled");
            }
            selected.remove(language);
        }
        Set<String> stored = selected.size() == available.size() ? Set.of() : Set.copyOf(selected);
        browsePreferences.save(new ExtensionBrowsePreferences(stored, current.pinnedPackages()));
        notifyListeners();
    }

    @Override
    public void setPinned(String packageName, boolean pinned) {
        String packageId = ExtensionPackageIdentifiers.requireValid(packageName);
        ExtensionBrowsePreferences current = browsePreferences.snapshot();
        Set<String> packages = new LinkedHashSet<>(current.pinnedPackages());
        if (pinned) {
            packages.add(packageId);
        } else {
            packages.remove(packageId);
        }
        browsePreferences.save(new ExtensionBrowsePreferences(current.enabledLanguages(), packages));
        notifyListeners();
    }

    @Override
    public void trustKey(String keyId, String x509PublicKeyBase64) {
        installation.trust(keyId, x509PublicKeyBase64);
        notifyListeners();
    }

    @Override
    public boolean forgetTrust(String keyId) {
        boolean forgotten = installation.forgetTrust(keyId);
        if (forgotten) {
            notifyListeners();
        }
        return forgotten;
    }

    @Override
    public CompletableFuture<ExtensionRepositoryView> install(ExtensionPackageMetadata extensionPackage) {
        return lifecycle(() -> installation.install(extensionPackage));
    }

    @Override
    public CompletableFuture<ExtensionRepositoryView> update(ExtensionPackageMetadata extensionPackage) {
        return lifecycle(() -> installation.update(extensionPackage));
    }

    @Override
    public void setEnabled(String packageName, boolean enabled) {
        installation.setEnabled(packageName, enabled);
        notifyListeners();
    }

    @Override
    public boolean removeInstalled(String packageName) {
        boolean removed = installation.remove(packageName);
        if (removed) {
            notifyListeners();
        }
        return removed;
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Preconditions.requireNonNull(listener, "listener");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    @Override
    public void close() {
        listeners.clear();
        try {
            updateObservation.close();
            settingsObservation.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to close extension update observation", exception);
        }
        executor.shutdownNow();
    }

    private static Set<String> effectiveLanguages(
            ExtensionBrowsePreferences preferences,
            List<String> available) {
        if (preferences.enabledLanguages().isEmpty()) {
            return Set.copyOf(available);
        }
        Set<String> retained = new LinkedHashSet<>(preferences.enabledLanguages());
        retained.retainAll(available);
        return retained.isEmpty() ? Set.copyOf(available) : Set.copyOf(retained);
    }

    private static String normalizeLanguage(String languageTag) {
        return Preconditions.requireNonBlank(languageTag, "languageTag")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private CompletableFuture<ExtensionRepositoryView> lifecycle(Runnable operation) {
        return CompletableFuture.supplyAsync(() -> {
            operation.run();
            ExtensionRepositoryView view = snapshot();
            notifyListeners();
            return view;
        }, executor);
    }
}
