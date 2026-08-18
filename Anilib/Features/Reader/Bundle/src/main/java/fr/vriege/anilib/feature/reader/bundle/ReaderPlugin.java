package fr.vriege.anilib.feature.reader.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.reader.runtime.DefaultReaderService;
import fr.vriege.anilib.feature.reader.ui.DefaultReaderPresentation;
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.util.Objects;

public final class ReaderPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.reader", "Reader", "0.1.0"))
            .requires(SourceCapabilities.REGISTRY)
            .requires(LibraryCapabilities.CATALOG)
            .provides(ReaderCapabilities.SERVICE)
            .provides(ReaderCapabilities.CONTENT_REGISTRAR)
            .provides(ReaderUiCapabilities.PRESENTATION)
            .build();

    private final ReaderPolicy policy;

    public ReaderPlugin() {
        this(ReaderPolicy.standard());
    }

    public ReaderPlugin(ReaderPolicy policy) {
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
        DefaultReaderService service = context.own(new DefaultReaderService(sources, library, policy));
        context.publish(ReaderCapabilities.SERVICE, service);
        context.publish(ReaderCapabilities.CONTENT_REGISTRAR, service);
        context.publish(ReaderUiCapabilities.PRESENTATION, new DefaultReaderPresentation(service));
    }
}
