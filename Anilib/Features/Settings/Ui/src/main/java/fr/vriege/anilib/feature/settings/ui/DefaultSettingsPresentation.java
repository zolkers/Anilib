package fr.vriege.anilib.feature.settings.ui;

import fr.vriege.anilib.feature.settings.DiagnosticResetArea;
import fr.vriege.anilib.feature.settings.DiagnosticResetPlan;
import fr.vriege.anilib.feature.settings.DiagnosticService;
import fr.vriege.anilib.feature.settings.DiagnosticSnapshot;
import fr.vriege.anilib.feature.settings.BrowserPolicy;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.AccentColor;
import fr.vriege.anilib.feature.settings.LanguagePack;
import fr.vriege.anilib.feature.settings.NavigationStyle;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeFamily;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.TypographyScale;
import fr.vriege.anilib.feature.settings.UnusedDataCleanupResult;
import fr.vriege.anilib.feature.settings.UnusedDataMaintenance;

import java.util.Objects;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

public final class DefaultSettingsPresentation implements SettingsPresentation {
    private final SettingsService service;
    private final UnusedDataMaintenance maintenance;
    private final DiagnosticService diagnostics;

    public DefaultSettingsPresentation(
            SettingsService service,
            UnusedDataMaintenance maintenance,
            DiagnosticService diagnostics) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
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
    public void setLanguagePack(LanguagePack languagePack) {
        service.replace(service.snapshot().withLanguagePack(languagePack));
    }

    @Override
    public void setThemeMode(ThemeMode themeMode) {
        service.replace(service.snapshot().withThemeMode(themeMode));
    }

    @Override
    public void setThemeFamily(ThemeFamily themeFamily) {
        service.replace(service.snapshot().withThemeFamily(themeFamily));
    }

    @Override
    public void setAccentColor(AccentColor accentColor) {
        service.replace(service.snapshot().withAccentColor(accentColor));
    }

    @Override
    public void setTypographyScale(TypographyScale typographyScale) {
        service.replace(service.snapshot().withTypographyScale(typographyScale));
    }

    @Override
    public void setReducedMotion(boolean enabled) {
        service.replace(service.snapshot().withReducedMotion(enabled));
    }

    @Override
    public void setNavigationStyle(NavigationStyle navigationStyle) {
        service.replace(service.snapshot().withNavigationStyle(navigationStyle));
    }

    @Override
    public void setBrowserPolicy(BrowserPolicy browserPolicy) {
        service.replace(service.snapshot().withBrowserPolicy(browserPolicy));
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

    @Override
    public DiagnosticSnapshot diagnostics() {
        return diagnostics.snapshot();
    }

    @Override
    public Path exportDiagnostics() {
        return diagnostics.export();
    }

    @Override
    public DiagnosticResetPlan planReset(Set<DiagnosticResetArea> areas) {
        return diagnostics.planReset(areas);
    }

    @Override
    public void executeReset(DiagnosticResetPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        diagnostics.executeReset(plan.confirmationToken());
        if (plan.areas().contains(DiagnosticResetArea.SETTINGS)) {
            service.replace(SettingsSnapshot.defaults());
        }
    }
}
