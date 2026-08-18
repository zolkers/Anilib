package fr.vriege.anilib.tooling.sourcepublisher;

import java.nio.file.Path;
import java.util.List;

public final class SourcePublisher {
    private SourcePublisher() {
    }

    public static void generateKeys(Path privateFile, Path publicFile) {
        PublisherKeys.generate(privateFile, publicFile);
    }

    public static void pack(Path classesDirectory, Path descriptorFile, Path outputFile) {
        BundlePackager.pack(classesDirectory, descriptorFile, outputFile);
    }

    public static void publish(Path privateKeyFile, Path outputDirectory, List<Path> configurationFiles) {
        RepositoryPublisher.publish(privateKeyFile, outputDirectory, configurationFiles);
    }
}
