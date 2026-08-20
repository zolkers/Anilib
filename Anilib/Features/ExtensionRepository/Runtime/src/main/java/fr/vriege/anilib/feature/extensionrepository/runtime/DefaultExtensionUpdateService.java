package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateCandidate;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateResult;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateService;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class DefaultExtensionUpdateService implements ExtensionUpdateService, AutoCloseable {
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration CHECK_INTERVAL = Duration.ofHours(6);

    private final ExtensionRepositoryService repositories;
    private final ExtensionInstallationService installation;
    private final FileExtensionUpdatePolicyStore policyStore;
    private final ScheduledExecutorService executor;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean automaticUpdatesEnabled;
    private final BooleanSupplier adultContentAllowed;

    public DefaultExtensionUpdateService(
            ExtensionRepositoryService repositories,
            ExtensionInstallationService installation,
            FileExtensionUpdatePolicyStore policyStore) {
        this(repositories, installation, policyStore, () -> true);
    }

    public DefaultExtensionUpdateService(
            ExtensionRepositoryService repositories,
            ExtensionInstallationService installation,
            FileExtensionUpdatePolicyStore policyStore,
            BooleanSupplier adultContentAllowed) {
        this.repositories = Preconditions.requireNonNull(repositories, "repositories");
        this.installation = Preconditions.requireNonNull(installation, "installation");
        this.policyStore = Preconditions.requireNonNull(policyStore, "policyStore");
        this.adultContentAllowed = Preconditions.requireNonNull(adultContentAllowed, "adultContentAllowed");
        automaticUpdatesEnabled = policyStore.load();
        executor = ManagedExecutors.scheduled("anilib-extension-updates");
        executor.scheduleWithFixedDelay(
                this::runAutomaticUpdateSafely,
                INITIAL_DELAY.toSeconds(),
                CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    @Override
    public synchronized List<ExtensionUpdateCandidate> availableUpdates() {
        Map<String, ExtensionPackageMetadata> available = new LinkedHashMap<>();
        for (ExtensionPackageMetadata extensionPackage : repositories.packages()) {
            if (extensionPackage.adult() && !adultContentAllowed.getAsBoolean()) {
                continue;
            }
            available.put(extensionPackage.packageName(), extensionPackage);
        }
        List<ExtensionUpdateCandidate> candidates = new ArrayList<>();
        for (InstalledExtensionPackage installed : installation.installed()) {
            ExtensionPackageMetadata update = available.get(installed.packageName());
            if (update == null || update.versionCode() <= installed.versionCode() || portable(update).isEmpty()) {
                continue;
            }
            candidates.add(new ExtensionUpdateCandidate(installed, update, samePublisher(installed, update)));
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.available().displayName()));
        return List.copyOf(candidates);
    }

    @Override
    public synchronized List<ExtensionUpdateCandidate> checkForUpdates() {
        repositories.refreshAll();
        List<ExtensionUpdateCandidate> updates = availableUpdates();
        notifyListeners();
        return updates;
    }

    @Override
    public synchronized ExtensionUpdateResult updateAllAvailable() {
        checkForUpdates();
        return apply(availableUpdates(), false);
    }

    @Override
    public boolean automaticUpdatesEnabled() {
        return automaticUpdatesEnabled;
    }

    @Override
    public synchronized void setAutomaticUpdatesEnabled(boolean enabled) {
        if (automaticUpdatesEnabled == enabled) {
            return;
        }
        policyStore.save(enabled);
        automaticUpdatesEnabled = enabled;
        notifyListeners();
        if (enabled) {
            executor.execute(this::runAutomaticUpdateSafely);
        }
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
        executor.shutdownNow();
    }

    private synchronized ExtensionUpdateResult apply(
            List<ExtensionUpdateCandidate> candidates,
            boolean automaticOnly) {
        List<InstalledExtensionPackage> updated = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (ExtensionUpdateCandidate candidate : candidates) {
            if (automaticOnly && !candidate.automaticEligible()) {
                continue;
            }
            try {
                updated.add(installation.update(candidate.available()));
            } catch (RuntimeException exception) {
                failures.put(candidate.available().packageName(), failureMessage(exception));
            }
        }
        ExtensionUpdateResult result = new ExtensionUpdateResult(updated, failures);
        notifyListeners();
        return result;
    }

    private void runAutomaticUpdateSafely() {
        if (!automaticUpdatesEnabled) {
            return;
        }
        try {
            synchronized (this) {
                repositories.refreshAll();
                apply(availableUpdates(), true);
            }
        } catch (RuntimeException ignored) {
            notifyListeners();
        }
    }

    private static boolean samePublisher(
            InstalledExtensionPackage installed,
            ExtensionPackageMetadata available) {
        Optional<String> publisher = portable(available).flatMap(ExtensionArtifactMetadata::signingKeyId);
        return installed.signingKeyId().isPresent() && installed.signingKeyId().equals(publisher);
    }

    private static Optional<ExtensionArtifactMetadata> portable(ExtensionPackageMetadata extensionPackage) {
        return extensionPackage.artifacts().stream()
                .filter(artifact -> artifact.format() == ExtensionArtifactFormat.ANILIB_BUNDLE)
                .findFirst();
    }

    private static String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
