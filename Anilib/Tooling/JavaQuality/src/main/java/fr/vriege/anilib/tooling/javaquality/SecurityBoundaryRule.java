package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SecurityBoundaryRule implements AnilibJavaRule {
    public SecurityBoundaryRule() {
    }

    @Override
    public String name() {
        return "security-boundary";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        requireTokens(repository, diagnostics,
                Path.of("Anilib", "Features", "ExtensionRepository", "Runtime", "src", "main", "java",
                        "fr", "vriege", "anilib", "feature", "extensionrepository", "runtime",
                        "InMemoryModuleArchive.java"),
                "MAX_ENTRY_BYTES", "MAX_EXPANDED_BYTES", "MAX_ENTRIES", "name.contains(\"../\")",
                "putIfAbsent");
        requireTokens(repository, diagnostics,
                Path.of("Anilib", "Features", "LocalSource", "Runtime", "src", "main", "java", "fr",
                        "vriege", "anilib", "feature", "localsource", "runtime",
                        "FileSystemLocalContentSource.java"),
                "MAX_PAGE_BYTES", "MAX_ARCHIVE_ENTRIES", "Files.isSymbolicLink", "isSafeEntry");
        requireTokens(repository, diagnostics,
                Path.of("Anilib", "Features", "Backup", "Runtime", "src", "main", "java", "fr", "vriege",
                        "anilib", "feature", "backup", "runtime", "BackupArchiveStore.java"),
                "MAXIMUM_ARCHIVE_BYTES", "MAXIMUM_SECTIONS", "MAXIMUM_SECTION_BYTES",
                "Files.isSymbolicLink", "MessageDigest.isEqual");
        requireTokens(repository, diagnostics,
                Path.of("Anilib", "Framework", "Http", "Runtime", "src", "main", "java", "fr", "vriege",
                        "anilib", "framework", "http", "runtime", "MediaHeaderProxy.java"),
                "127.0.0.1", "MAXIMUM_REQUEST_BYTES", "MAXIMUM_PLAYLIST_BYTES", "BLOCKED_REQUEST_HEADERS",
                "FORWARDED_RESPONSE_HEADERS", "SecureRandom");
        requireTokens(repository, diagnostics,
                Path.of("Anilib", "Features", "ApplicationUpdate", "Runtime", "src", "main", "java", "fr",
                        "vriege", "anilib", "feature", "applicationupdate", "runtime",
                        "ApplicationReleaseManifest.java"),
                "MAX_MANIFEST_BYTES", "Ed25519", "anilib-update-v1", "zolkers/Anilib",
                ".github/workflows/application-release.yml");
        requireTokens(repository, diagnostics, Path.of("Anilib", "SECURITY_REVIEW.md"),
                "Extension repositories", "Loopback media relay", "Backup import", "Application updater",
                "Release supply chain");
        return List.copyOf(diagnostics);
    }

    private void requireTokens(
            RepositorySnapshot repository,
            List<Diagnostic> diagnostics,
            Path relativePath,
            String... tokens) {
        Path file = repository.root().resolve(relativePath);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            diagnostics.add(new Diagnostic(name(), relativePath, 1,
                    "Required security boundary file is missing or symbolic"));
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : tokens) {
                if (!content.contains(token)) {
                    diagnostics.add(new Diagnostic(name(), relativePath, 1,
                            "Security boundary contract is missing: " + token));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect " + file, exception);
        }
    }
}
