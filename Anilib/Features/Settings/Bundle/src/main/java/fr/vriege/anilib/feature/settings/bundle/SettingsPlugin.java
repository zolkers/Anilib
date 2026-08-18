package fr.vriege.anilib.feature.settings.bundle;

import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.runtime.FileSettingsService;
import fr.vriege.anilib.feature.settings.runtime.FileDiagnosticService;
import fr.vriege.anilib.feature.settings.runtime.DefaultUnusedDataMaintenance;
import fr.vriege.anilib.feature.settings.ui.DefaultSettingsPresentation;
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public final class SettingsPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.settings", "Settings", "1.2.0"))
            .provides(SettingsCapabilities.SERVICE)
            .provides(SettingsCapabilities.DIAGNOSTICS)
            .provides(SettingsCapabilities.UNUSED_DATA_REGISTRAR)
            .provides(SettingsUiCapabilities.PRESENTATION)
            .build();

    private final Path settingsFile;

    public SettingsPlugin(Path settingsFile) {
        this.settingsFile = Objects.requireNonNull(settingsFile, "settingsFile must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        FileSettingsService service = new FileSettingsService(settingsFile);
        FileDiagnosticService diagnostics = new FileDiagnosticService(settingsFile.getParent());
        DefaultUnusedDataMaintenance maintenance = new DefaultUnusedDataMaintenance();
        diagnostics.recordLog("Anilib settings and diagnostics started");
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler handler = (thread, failure) -> {
            try {
                diagnostics.recordCrash(
                        "Uncaught exception on " + thread.getName(),
                        stackTrace(failure));
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, failure);
                }
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        context.onClose(() -> {
            if (Thread.getDefaultUncaughtExceptionHandler() == handler) {
                Thread.setDefaultUncaughtExceptionHandler(previous);
            }
        });
        context.publish(SettingsCapabilities.SERVICE, service);
        context.publish(SettingsCapabilities.DIAGNOSTICS, diagnostics);
        context.publish(SettingsCapabilities.UNUSED_DATA_REGISTRAR, maintenance);
        context.publish(
                SettingsUiCapabilities.PRESENTATION,
                new DefaultSettingsPresentation(service, maintenance, diagnostics));
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
