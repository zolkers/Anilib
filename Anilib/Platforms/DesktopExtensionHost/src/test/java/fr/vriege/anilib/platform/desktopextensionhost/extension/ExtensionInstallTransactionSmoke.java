package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class ExtensionInstallTransactionSmoke {
    private ExtensionInstallTransactionSmoke() {}

    public static void verify() throws Exception {
        Path directory = Files.createTempDirectory("anilib-extension-update-test-");
        try {
            Path apk = Files.writeString(directory.resolve("test.source.apk"), "old apk");
            Path jar = Files.writeString(directory.resolve("test.source.jar"), "old jar");
            Path stagedJar = Files.writeString(directory.resolve("staged.jar"), "new jar");
            try {
                ExtensionInstallTransaction.commit(
                        directory.resolve("missing.apk"), stagedJar, apk, jar);
                throw new IllegalStateException("Broken update was committed");
            } catch (IOException expected) {
                if (!Files.readString(apk).equals("old apk")
                        || !Files.readString(jar).equals("old jar")) {
                    throw new IllegalStateException(
                            "Failed update damaged the previous APK/JAR pair", expected);
                }
            }
            Path interrupted =
                    Files.createDirectory(directory.resolve(".anilib-update-interrupted"));
            Files.writeString(interrupted.resolve("previous.apk"), "old apk");
            Files.writeString(interrupted.resolve("previous.jar"), "old jar");
            Files.writeString(
                    interrupted.resolve("manifest"), "package=test.source\napk=true\njar=true\n");
            Files.createFile(interrupted.resolve("pending"));
            Files.writeString(apk, "incomplete update");
            ExtensionInstallTransaction.recover(directory);
            if (!Files.readString(apk).equals("old apk") || Files.exists(interrupted)) {
                throw new IllegalStateException("Interrupted update was not recovered");
            }
            Path goodApk = Files.writeString(directory.resolve("good.apk"), "new apk");
            Path goodJar = Files.writeString(directory.resolve("good.jar"), "new jar");
            ExtensionInstallTransaction.commit(goodApk, goodJar, apk, jar);
            if (!Files.readString(apk).equals("new apk")
                    || !Files.readString(jar).equals("new jar")) {
                throw new IllegalStateException("Healthy update did not replace both files");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }
}
