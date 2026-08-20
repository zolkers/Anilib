package fr.vriege.anilib.feature.backup.runtime;

import fr.vriege.anilib.feature.backup.BackupException;
import fr.vriege.anilib.framework.backup.BackupSectionId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.io.OutputStream;
import java.util.Arrays;

final class BackupArchiveStore {
    static final String EXTENSION = ".anibak";
    static final long MAXIMUM_ARCHIVE_BYTES = 256L * 1024L * 1024L;
    private static final int MAGIC = 0x414E424B;
    private static final int CURRENT_VERSION = 1;
    private static final int CHECKSUM_BYTES = 32;
    private static final int MAXIMUM_SECTIONS = 128;
    private static final int MAXIMUM_SECTION_BYTES = 128 * 1024 * 1024;

    Archive read(Path path) {
        Path normalized = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
                throw new BackupException("Backup path must be a regular non-symbolic file");
            }
            long size = Files.size(normalized);
            if (size <= CHECKSUM_BYTES || size > MAXIMUM_ARCHIVE_BYTES) {
                throw new BackupException("Backup archive size is outside the supported bounds");
            }
            byte[] archiveBytes = Files.readAllBytes(normalized);
            int bodyLength = archiveBytes.length - CHECKSUM_BYTES;
            byte[] expected = Arrays.copyOfRange(archiveBytes, bodyLength, archiveBytes.length);
            byte[] body = Arrays.copyOf(archiveBytes, bodyLength);
            if (!MessageDigest.isEqual(expected, digest(body))) {
                throw new BackupException("Backup archive checksum does not match");
            }
            return decode(body);
        } catch (IOException exception) {
            throw new BackupException("Unable to read backup archive", exception);
        }
    }

    void write(Path destination, Archive archive) throws IOException {
        byte[] body = encode(archive);
        byte[] checksum = digest(body);
        if ((long) body.length + checksum.length > MAXIMUM_ARCHIVE_BYTES) {
            throw new BackupException("Backup archive exceeds the maximum supported size");
        }
        try (FileChannel channel = FileChannel.open(
                destination,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             OutputStream output = Channels.newOutputStream(channel)) {
            output.write(body);
            output.write(checksum);
            output.flush();
            channel.force(true);
        }
    }

    private static byte[] encode(Archive archive) throws IOException {
        if (archive.sections().isEmpty() || archive.sections().size() > MAXIMUM_SECTIONS) {
            throw new BackupException("Backup archive must contain between one and 128 sections");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_VERSION);
            output.writeLong(archive.createdAt().getEpochSecond());
            output.writeInt(archive.createdAt().getNano());
            output.writeInt(archive.sections().size());
            Set<BackupSectionId> identifiers = new HashSet<>();
            for (EncodedSection section : archive.sections()) {
                if (!identifiers.add(section.id())) {
                    throw new BackupException("Backup archive contains duplicate section ids");
                }
                byte[] payload = section.payload();
                if (payload.length > MAXIMUM_SECTION_BYTES) {
                    throw new BackupException("Backup section exceeds the maximum supported size");
                }
                output.writeUTF(section.id().value());
                output.writeInt(section.version());
                output.writeInt(section.entryCount());
                output.writeInt(payload.length);
                output.write(digest(payload));
                output.write(payload);
            }
        }
        return bytes.toByteArray();
    }

    private static Archive decode(byte[] body) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            if (input.readInt() != MAGIC) {
                throw new BackupException("Invalid Anilib backup signature");
            }
            int archiveVersion = input.readInt();
            if (archiveVersion != CURRENT_VERSION) {
                throw new BackupException("Unsupported Anilib backup version: " + archiveVersion);
            }
            Instant createdAt = Instant.ofEpochSecond(input.readLong(), input.readInt());
            int count = input.readInt();
            if (count < 1 || count > MAXIMUM_SECTIONS) {
                throw new BackupException("Invalid backup section count: " + count);
            }
            List<EncodedSection> sections = new ArrayList<>(count);
            Set<BackupSectionId> identifiers = new HashSet<>();
            for (int index = 0; index < count; index++) {
                BackupSectionId id = BackupSectionId.of(input.readUTF());
                int version = input.readInt();
                int entryCount = input.readInt();
                int payloadLength = input.readInt();
                if (version < 0 || entryCount < 0 || payloadLength < 0
                        || payloadLength > MAXIMUM_SECTION_BYTES) {
                    throw new BackupException("Invalid metadata for backup section " + id);
                }
                byte[] expected = input.readNBytes(CHECKSUM_BYTES);
                byte[] payload = input.readNBytes(payloadLength);
                if (expected.length != CHECKSUM_BYTES || payload.length != payloadLength) {
                    throw new BackupException("Truncated backup section " + id);
                }
                if (!MessageDigest.isEqual(expected, digest(payload))) {
                    throw new BackupException("Checksum does not match for backup section " + id);
                }
                if (!identifiers.add(id)) {
                    throw new BackupException("Duplicate backup section " + id);
                }
                sections.add(new EncodedSection(id, version, entryCount, payload));
            }
            if (input.read() != -1) {
                throw new BackupException("Unexpected trailing data in backup archive");
            }
            return new Archive(createdAt, sections);
        } catch (EOFException exception) {
            throw new BackupException("Truncated backup archive", exception);
        } catch (IOException | DateTimeException | IllegalArgumentException exception) {
            throw new BackupException("Invalid backup archive", exception);
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("Java 21 must provide SHA-256", exception);
        }
    }

    record Archive(Instant createdAt, List<EncodedSection> sections) {
        Archive {
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            sections = List.copyOf(sections);
        }
    }

    record EncodedSection(BackupSectionId id, int version, int entryCount, byte[] payload) {
        EncodedSection {
            Objects.requireNonNull(id, "id must not be null");
            if (version < 0 || entryCount < 0) {
                throw new IllegalArgumentException("version and entryCount must not be negative");
            }
            payload = Objects.requireNonNull(payload, "payload must not be null").clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
