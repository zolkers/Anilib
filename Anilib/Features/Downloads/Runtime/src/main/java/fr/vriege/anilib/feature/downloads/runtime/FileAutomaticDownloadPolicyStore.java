package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.AutomaticDownloadCategoryRule;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadPolicy;
import fr.vriege.anilib.feature.downloads.DownloadCleanupPolicy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class FileAutomaticDownloadPolicyStore {
    private static final int MAGIC = 0x41444C50;
    private static final int VERSION = 1;
    private static final int MAXIMUM_RULES = 256;
    private final Path file;

    FileAutomaticDownloadPolicyStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    AutomaticDownloadPolicy load() throws IOException {
        if (!Files.exists(file)) {
            return AutomaticDownloadPolicy.disabled();
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported automatic download policy format");
            }
            boolean enabled = input.readBoolean();
            boolean favoritesOnly = input.readBoolean();
            boolean includeUncategorized = input.readBoolean();
            int episodeLimit = input.readInt();
            int chapterLimit = input.readInt();
            DownloadCleanupPolicy cleanup = readCleanup(input);
            int retention = input.readInt();
            int count = input.readInt();
            if (count < 0 || count > MAXIMUM_RULES) {
                throw new IOException("Invalid automatic download category rule count");
            }
            List<AutomaticDownloadCategoryRule> rules = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                rules.add(new AutomaticDownloadCategoryRule(
                        input.readUTF(),
                        input.readInt(),
                        input.readInt()));
            }
            return new AutomaticDownloadPolicy(
                    enabled,
                    favoritesOnly,
                    includeUncategorized,
                    episodeLimit,
                    chapterLimit,
                    cleanup,
                    retention,
                    rules);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid automatic download policy", exception);
        }
    }

    void save(AutomaticDownloadPolicy policy) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeBoolean(policy.enabled());
            output.writeBoolean(policy.favoritesOnly());
            output.writeBoolean(policy.includeUncategorized());
            output.writeInt(policy.defaultEpisodeLimit());
            output.writeInt(policy.defaultChapterLimit());
            output.writeUTF(policy.cleanupPolicy().name());
            output.writeInt(policy.retainedCompletedPerTitle());
            output.writeInt(policy.categoryRules().size());
            for (AutomaticDownloadCategoryRule rule : policy.categoryRules()) {
                output.writeUTF(rule.category());
                output.writeInt(rule.episodeLimit());
                output.writeInt(rule.chapterLimit());
            }
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static DownloadCleanupPolicy readCleanup(DataInputStream input) throws IOException {
        try {
            return DownloadCleanupPolicy.valueOf(input.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid automatic download cleanup policy", exception);
        }
    }
}
