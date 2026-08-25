package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionAbiVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ExtensionAbiInventory {
    private ExtensionAbiInventory() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one extension JAR directory");
        }
        Path directory = Path.of(arguments[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Extension ABI inventory path is not a directory: " + directory);
        }
        List<Path> archives;
        try (var files = Files.walk(directory)) {
            archives = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        if (archives.isEmpty()) {
            throw new IllegalArgumentException("Extension ABI inventory contains no JAR archives: " + directory);
        }
        ExtensionAbiVerifier verifier = new ExtensionAbiVerifier();
        List<String> failures = new ArrayList<>();
        for (Path archive : archives) {
            ExtensionAbiVerifier.Report report = verifier.inspect(archive);
            if (report.compatible()) {
                System.out.println("COMPATIBLE " + archive.getFileName());
            } else {
                System.out.println("INCOMPATIBLE " + archive.getFileName());
                report.missingSymbols().forEach(symbol -> System.out.println("  " + symbol));
                failures.add(archive.getFileName() + ": " + String.join(", ", report.missingSymbols()));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Extension ABI inventory found " + failures.size()
                    + " incompatible archive(s):\n" + String.join("\n", failures));
        }
    }
}
