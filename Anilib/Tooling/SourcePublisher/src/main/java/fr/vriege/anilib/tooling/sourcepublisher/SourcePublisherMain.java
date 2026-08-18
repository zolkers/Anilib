package fr.vriege.anilib.tooling.sourcepublisher;

import java.nio.file.Path;
import java.util.Arrays;

public final class SourcePublisherMain {
    private SourcePublisherMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length == 0) {
            usage();
            System.exit(2);
        }
        try {
            switch (arguments[0]) {
                case "keygen" -> keygen(arguments);
                case "pack" -> pack(arguments);
                case "publish" -> publish(arguments);
                default -> throw new IllegalArgumentException("Unknown command: " + arguments[0]);
            }
        } catch (RuntimeException exception) {
            System.err.println("SourcePublisher: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void keygen(String[] arguments) {
        requireArguments(arguments, 3, "keygen <private-key-file> <public-key-file>");
        SourcePublisher.generateKeys(Path.of(arguments[1]), Path.of(arguments[2]));
        System.out.println("Generated Ed25519 publisher key pair.");
    }

    private static void pack(String[] arguments) {
        requireArguments(arguments, 4, "pack <classes-directory> <descriptor-file> <bundle.jar>");
        SourcePublisher.pack(Path.of(arguments[1]), Path.of(arguments[2]), Path.of(arguments[3]));
        System.out.println("Built portable source Bundle: " + Path.of(arguments[3]).toAbsolutePath().normalize());
    }

    private static void publish(String[] arguments) {
        if (arguments.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: publish <private-key-file> <output-directory> <package.properties>...");
        }
        SourcePublisher.publish(
                Path.of(arguments[1]),
                Path.of(arguments[2]),
                Arrays.stream(arguments).skip(3).map(Path::of).toList());
        System.out.println("Published source repository: " + Path.of(arguments[2]).toAbsolutePath().normalize());
    }

    private static void requireArguments(String[] arguments, int count, String usage) {
        if (arguments.length != count) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void usage() {
        System.err.println("Usage: SourcePublisher <keygen|pack|publish> ...");
    }
}
