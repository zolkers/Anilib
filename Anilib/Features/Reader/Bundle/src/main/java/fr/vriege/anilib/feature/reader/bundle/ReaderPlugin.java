package fr.vriege.anilib.feature.reader.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.reader.runtime.DefaultReaderService;
import fr.vriege.anilib.feature.reader.runtime.FileReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.runtime.FileReaderInteractionPreferenceStore;
import fr.vriege.anilib.feature.reader.runtime.FileReaderReadStateStore;
import fr.vriege.anilib.feature.reader.runtime.PrivacyAwareReaderReadStateStore;
import fr.vriege.anilib.feature.reader.ui.DefaultReaderPresentation;
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.util.Objects;
import java.nio.file.Path;

public final class ReaderPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.reader", "Reader", "0.1.0"))
            .requires(SourceCapabilities.REGISTRY)
            .requires(LibraryCapabilities.CATALOG)
            .requires(SettingsCapabilities.SERVICE)
            .provides(ReaderCapabilities.SERVICE)
            .provides(ReaderCapabilities.CONTENT_REGISTRAR)
            .provides(ReaderUiCapabilities.PRESENTATION)
            .build();

    private final ReaderPolicy policy;
    private final Path interactionPreferences;
    private final Path displayPreferences;
    private final Path readState;

    public ReaderPlugin() {
        this(
                Path.of("reader-interactions.properties"),
                Path.of("reader-display.properties"),
                Path.of("reader-read-state.properties"),
                ReaderPolicy.standard());
    }

    public ReaderPlugin(ReaderPolicy policy) {
        this(
                Path.of("reader-interactions.properties"),
                Path.of("reader-display.properties"),
                Path.of("reader-read-state.properties"),
                policy);
    }

    public ReaderPlugin(Path interactionPreferences, Path displayPreferences, Path readState) {
        this(interactionPreferences, displayPreferences, readState, ReaderPolicy.standard());
    }

    public ReaderPlugin(
            Path interactionPreferences,
            Path displayPreferences,
            Path readState,
            ReaderPolicy policy) {
        this.interactionPreferences = Objects.requireNonNull(
                interactionPreferences,
                "interactionPreferences must not be null");
        this.displayPreferences = Objects.requireNonNull(displayPreferences, "displayPreferences must not be null");
        this.readState = Objects.requireNonNull(readState, "readState must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        SourceRegistry sources = context.require(SourceCapabilities.REGISTRY);
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        SettingsService settings = context.require(SettingsCapabilities.SERVICE);
        DefaultReaderService service = context.own(new DefaultReaderService(
                sources,
                library,
                policy,
                () -> !settings.snapshot().incognitoMode()));
        context.publish(ReaderCapabilities.SERVICE, service);
        context.publish(ReaderCapabilities.CONTENT_REGISTRAR, service);
        context.publish(ReaderUiCapabilities.PRESENTATION, new DefaultReaderPresentation(
                service,
                new FileReaderInteractionPreferenceStore(interactionPreferences),
                new FileReaderDisplayPreferenceStore(displayPreferences),
                new PrivacyAwareReaderReadStateStore(
                        new FileReaderReadStateStore(readState),
                        () -> !settings.snapshot().incognitoMode())));
    }
}
