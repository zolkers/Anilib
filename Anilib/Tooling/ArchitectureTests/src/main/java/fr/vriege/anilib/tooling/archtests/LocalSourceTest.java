package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.localsource.LocalContentSource;
import fr.vriege.anilib.feature.localsource.LocalPage;
import fr.vriege.anilib.feature.localsource.LocalPublication;
import fr.vriege.anilib.feature.localsource.LocalPublicationId;
import fr.vriege.anilib.feature.localsource.LocalPublicationType;
import fr.vriege.anilib.feature.localsource.LocalSourceException;
import fr.vriege.anilib.feature.localsource.runtime.FileSystemLocalContentSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Black-box folder, ZIP/CBZ, ordering, reading, and traversal checks. */
final class LocalSourceTest {
    private static final byte[] FIRST_IMAGE = {1, 2, 3};
    private static final byte[] SECOND_IMAGE = {4, 5, 6, 7};

    private LocalSourceTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-local-source");
            prepareFolder(root);
            prepareArchive(root);
            verifySource(root, counter);
        } catch (IOException exception) {
            throw new AssertionError("Local source test failed", exception);
        } finally {
            if (root != null) {
                try {
                    deleteTree(root);
                } catch (IOException exception) {
                    throw new AssertionError("Unable to clean local source test", exception);
                }
            }
        }
        rejectsTraversal(counter);
        return counter.value;
    }

    private static void verifySource(Path root, Counter counter) throws IOException {
        LocalContentSource source = new FileSystemLocalContentSource(root);
        List<LocalPublication> publications = source.publications();
        counter.check(publications.size() == 2, "local source must discover folder and CBZ publications");
        counter.check(publications.get(0).title().equals("Archive"),
                "local publications must use deterministic title ordering");

        LocalPublication archive = publication(publications, LocalPublicationType.ZIP_ARCHIVE);
        List<LocalPage> archivePages = source.pages(archive.id());
        counter.check(archivePages.stream().map(LocalPage::entryName).toList()
                        .equals(List.of("chapter/002.jpg", "chapter/010.png")),
                "archive pages must be ordered and unsafe entries ignored");
        counter.check(java.util.Arrays.equals(source.read(archivePages.get(0)), FIRST_IMAGE),
                "archive page bytes must be readable");
        LocalPage forged = new LocalPage(
                archive.id(),
                archivePages.get(0).entryName(),
                99,
                archivePages.get(0).size());
        counter.expectSourceFailure(() -> source.read(forged),
                "forged page metadata must be rejected");

        LocalPublication folder = publication(publications, LocalPublicationType.DIRECTORY);
        List<LocalPage> folderPages = source.pages(folder.id());
        counter.check(folderPages.stream().map(LocalPage::entryName).toList()
                        .equals(List.of("chapter/001.jpg", "chapter/002.png")),
                "folder pages must be recursively indexed in deterministic order");
        counter.check(java.util.Arrays.equals(source.read(folderPages.get(1)), SECOND_IMAGE),
                "folder page bytes must be readable");
    }

    private static LocalPublication publication(
            List<LocalPublication> publications,
            LocalPublicationType type) {
        return publications.stream()
                .filter(publication -> publication.id().type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static void prepareFolder(Path root) throws IOException {
        Path chapter = Files.createDirectories(root.resolve("Folder Title").resolve("chapter"));
        Files.write(chapter.resolve("002.png"), SECOND_IMAGE);
        Files.write(chapter.resolve("001.jpg"), FIRST_IMAGE);
        Files.writeString(chapter.resolve("notes.txt"), "ignored");
    }

    private static void prepareArchive(Path root) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(root.resolve("Archive.cbz")))) {
            writeEntry(output, "chapter/010.png", SECOND_IMAGE);
            writeEntry(output, "chapter/002.jpg", FIRST_IMAGE);
            writeEntry(output, "chapter/notes.txt", new byte[]{9});
            writeEntry(output, "../escape.jpg", new byte[]{8});
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static void rejectsTraversal(Counter counter) {
        try {
            new LocalPublicationId(LocalPublicationType.DIRECTORY, "../escape");
            throw new AssertionError("Expected traversal identity rejection");
        } catch (IllegalArgumentException expected) {
            counter.value++;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectSourceFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (LocalSourceException expected) {
                value++;
            }
        }
    }
}
