package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.UnusedDataCleanupResult;
import fr.vriege.anilib.feature.settings.UnusedDataMaintenance;

import java.util.Objects;
import java.util.function.Consumer;

public final class DefaultSettingsPresentation implements SettingsPresentation {
    private final SettingsService service;
    private final UnusedDataMaintenance maintenance;

    public DefaultSettingsPresentation(SettingsService service) {
        this(service, UnusedDataCleanupResult::empty);
    }

    public DefaultSettingsPresentation(SettingsService service, UnusedDataMaintenance maintenance) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance must not be null");
    }

    @Override
    public SettingsSnapshot snapshot() {
        return service.snapshot();
    }

    @Override
    public AutoCloseable observe(Consumer<SettingsSnapshot> observer) {
        return service.observe(observer);
    }

    @Override
    public void setThemeMode(ThemeMode themeMode) {
        service.replace(service.snapshot().withThemeMode(themeMode));
    }

    @Override
    public void setStartScreen(StartScreen startScreen) {
        service.replace(service.snapshot().withStartScreen(startScreen));
    }

    @Override
    public void setShowAdultContent(boolean enabled) {
        service.replace(service.snapshot().withShowAdultContent(enabled));
    }

    @Override
    public void setIncognitoMode(boolean enabled) {
        service.replace(service.snapshot().withIncognitoMode(enabled));
    }

    @Override
    public void setDownloadOnlyOnWifi(boolean enabled) {
        service.replace(service.snapshot().withDownloadOnlyOnWifi(enabled));
    }

    @Override
    public void setUpdateOnlyOnWifi(boolean enabled) {
        service.replace(service.snapshot().withUpdateOnlyOnWifi(enabled));
    }

    @Override
    public UnusedDataCleanupResult cleanUnusedData() {
        return maintenance.clean();
    }
}
