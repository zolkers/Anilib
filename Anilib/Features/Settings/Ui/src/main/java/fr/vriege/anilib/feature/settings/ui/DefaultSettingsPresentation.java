package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.ThemeMode;

import java.util.Objects;
import java.util.function.Consumer;

/** Presentation adapter that keeps persistence outside platform code. */
public final class DefaultSettingsPresentation implements SettingsPresentation {
    private final SettingsService service;

    public DefaultSettingsPresentation(SettingsService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
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
}
