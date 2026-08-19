package fr.vriege.anilib.tooling.extensionportability;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ExtensionPortabilityMain {
    private ExtensionPortabilityMain() {
    }

    public static void main(String[] arguments) {
        try {
            run(arguments);
        } catch (RuntimeException exception) {
            System.err.println("ExtensionPortability: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] arguments) {
        if (arguments.length < 2 || !arguments[0].equals("analyze")) {
            throw new IllegalArgumentException(
                    "Usage: analyze <repository> [--package <pkg>] [--source-ids <id,id>] "
                            + "[--scaffold <directory> --kind <anime|manga> --lang <tag>] "
                            + "--output <directory>");
        }
        Path repository = Path.of(arguments[1]);
        Optional<String> packageOverride = option(arguments, "--package");
        List<String> sourceIds = option(arguments, "--source-ids")
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::strip)
                        .filter(item -> !item.isEmpty())
                        .toList())
                .orElse(List.of());
        Path output = Path.of(requiredOption(arguments, "--output"));
        PortabilityReport report = ExtensionPortability.analyze(repository, packageOverride, sourceIds);
        ExtensionPortability.writeReports(report, output);
        Optional<String> scaffold = option(arguments, "--scaffold");
        if (scaffold.isPresent()) {
            ExtensionPortability.scaffold(
                    report,
                    Path.of(scaffold.orElseThrow()),
                    requiredOption(arguments, "--kind"),
                    requiredOption(arguments, "--lang"));
        }
        System.out.println("Portability report: " + output.toAbsolutePath().normalize());
        System.out.println("Findings: " + report.findings().size());
    }

    private static String requiredOption(String[] arguments, String name) {
        return option(arguments, name)
                .orElseThrow(() -> new IllegalArgumentException("Missing option " + name));
    }

    private static Optional<String> option(String[] arguments, String name) {
        for (int index = 2; index < arguments.length; index++) {
            if (arguments[index].equals(name)) {
                if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + name);
                }
                return Optional.of(arguments[index + 1]);
            }
        }
        return Optional.empty();
    }
}
