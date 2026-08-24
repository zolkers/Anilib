package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DesktopReleaseRule implements AnilibJavaRule {
    private static final Path ROOT_BUILD = Path.of("build.gradle");
    private static final Path DESKTOP_BUILD = Path.of("Anilib", "Platforms", "Desktop", "build.gradle");
    private static final Path DESKTOP_HOST_BUILD =
            Path.of("Anilib", "Platforms", "DesktopExtensionHost", "build.gradle");
    private static final Path WINDOWS_INSTALLER_PATCH = Path.of(
            "Anilib", "Platforms", "Desktop", "src", "main", "jpackage", "windows", "patch-safe-upgrade.ps1");
    private static final Path WINDOWS_DATA_MIGRATION = Path.of(
            "Anilib", "Platforms", "Desktop", "src", "main", "jpackage", "windows", "migrate-legacy-data.vbs");
    private static final Path WORKFLOW = Path.of(".github", "workflows", "desktop-release.yml");

    public DesktopReleaseRule() {
    }

    @Override
    public String name() {
        return "desktop-release";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        requireTokens(
                repository,
                ROOT_BUILD,
                diagnostics,
                "preserveFileTimestamps = false",
                "reproducibleFileOrder = true",
                "failOnDynamicVersions()",
                "failOnChangingVersions()");
        requireTokens(
                repository,
                DESKTOP_BUILD,
                diagnostics,
                "TargetFormat.Dmg",
                "TargetFormat.Msi",
                "TargetFormat.Deb",
                "anilibPackageVersion",
                "installationPath = 'AnilibApp'",
                "upgradeUuid =",
                "bundleID = 'fr.vriege.anilib'",
                "project(':Anilib:Platforms:DesktopExtensionHost')",
                "includeAllModules = true",
                "licenseFile.set(rootProject.file('LICENSE'))",
                "writeDesktopReleaseChecksums",
                "packageSafeUpgradeMsi",
                "MessageDigest.getInstance('SHA-256')");
        requireTokens(
                repository,
                WINDOWS_INSTALLER_PATCH,
                diagnostics,
                "JpMigrateLegacyData",
                "AnilibMigrateLegacyData",
                "NewGuid().ToString('B').ToUpperInvariant()",
                "JP_UPGRADABLE_FOUND",
                "-bor 512",
                "'ProductCode'",
                "@(6, $record)",
                "@(1, $record)",
                "'NOT REMOVE',1440",
                "``Sequence``=1450",
                "InvokeMember('Commit'");
        requireTokens(
                repository,
                WINDOWS_DATA_MIGRATION,
                diagnostics,
                "%LOCALAPPDATA%",
                "AnilibData",
                "IsProgramEntry",
                "AliasAttribute");
        requireTokens(
                repository,
                DESKTOP_HOST_BUILD,
                diagnostics,
                "dex-translator:2.4.38",
                "apk-parser:2.6.10",
                "kotlinx-coroutines-core-jvm:1.10.2",
                "okhttp:5.4.0");
        requireTokens(
                repository,
                WORKFLOW,
                diagnostics,
                "windows-2025",
                "ubuntu-24.04",
                "macos-15",
                "actions/checkout@v6.0.2",
                "actions/setup-java@v5.6.0",
                "java-version: 21.0.10",
                "writeDesktopReleaseChecksums",
                "actions/upload-artifact@v7.0.1");
        return List.copyOf(diagnostics);
    }

    private void requireTokens(
            RepositorySnapshot repository,
            Path relativePath,
            List<Diagnostic> diagnostics,
            String... requiredTokens) {
        Path file = repository.root().resolve(relativePath);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            diagnostics.add(new Diagnostic(name(), relativePath, 1,
                    "Required desktop release file is missing or symbolic"));
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : requiredTokens) {
                if (!content.contains(token)) {
                    diagnostics.add(new Diagnostic(name(), relativePath, 1,
                            "Desktop release contract is missing: " + token));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect " + file, exception);
        }
    }
}
