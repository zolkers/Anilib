package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionSourceMetadata;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class MihonRepositoryIndexParser {
    private static final int MAX_PACKAGES = 10_000;
    private static final int MAX_SOURCES_PER_PACKAGE = 10_000;

    List<ExtensionPackageMetadata> parse(URI indexUri, byte[] content) {
        URI repository = AniyomiRepositoryIndexParser.requireRepositoryUri(indexUri);
        ProtobufReader root = new ProtobufReader(content);
        List<ExtensionPackageMetadata> packages = new ArrayList<>();
        while (root.hasRemaining()) {
            int tag = root.readTag();
            if (ProtobufReader.fieldNumber(tag) == 101) {
                root.requireWireType(tag, 2, "extension list");
                parseExtensionList(repository, root.readBytes(), packages);
            } else {
                root.skip(tag);
            }
        }
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("Mihon repository does not embed an extension list");
        }
        if (packages.size() > MAX_PACKAGES) {
            throw new IllegalArgumentException("Extension repository contains too many packages");
        }
        Set<String> packageNames = new HashSet<>();
        for (ExtensionPackageMetadata extensionPackage : packages) {
            if (!packageNames.add(extensionPackage.packageName())) {
                throw new IllegalArgumentException(
                        "Duplicate extension package: " + extensionPackage.packageName());
            }
        }
        packages.sort(Comparator.comparing(ExtensionPackageMetadata::packageName));
        return List.copyOf(packages);
    }

    private void parseExtensionList(
            URI repository,
            byte[] content,
            List<ExtensionPackageMetadata> packages) {
        ProtobufReader list = new ProtobufReader(content);
        while (list.hasRemaining()) {
            int tag = list.readTag();
            if (ProtobufReader.fieldNumber(tag) == 1) {
                list.requireWireType(tag, 2, "extension");
                packages.add(parseExtension(repository, list.readBytes()));
                if (packages.size() > MAX_PACKAGES) {
                    throw new IllegalArgumentException("Extension repository contains too many packages");
                }
            } else {
                list.skip(tag);
            }
        }
    }

    private ExtensionPackageMetadata parseExtension(URI repository, byte[] content) {
        ProtobufReader extension = new ProtobufReader(content);
        String name = null;
        String packageName = null;
        Resources resources = null;
        long versionCode = -1;
        String versionName = null;
        int contentWarning = 0;
        List<ExtensionSourceMetadata> sources = new ArrayList<>();
        while (extension.hasRemaining()) {
            int tag = extension.readTag();
            switch (ProtobufReader.fieldNumber(tag)) {
                case 1 -> name = extension.readString(tag, "extension name");
                case 2 -> packageName = extension.readString(tag, "package name");
                case 3 -> {
                    extension.requireWireType(tag, 2, "resources");
                    resources = parseResources(extension.readBytes());
                }
                case 5 -> versionCode = extension.readInteger(tag, "version code");
                case 6 -> versionName = extension.readString(tag, "version name");
                case 7 -> contentWarning = Math.toIntExact(extension.readInteger(tag, "content warning"));
                case 8 -> {
                    extension.requireWireType(tag, 2, "source");
                    sources.add(parseSource(extension.readBytes()));
                    if (sources.size() > MAX_SOURCES_PER_PACKAGE) {
                        throw new IllegalArgumentException("Extension package contains too many sources");
                    }
                }
                default -> extension.skip(tag);
            }
        }
        if (resources == null) {
            throw new IllegalArgumentException("Mihon extension is missing resources");
        }
        ExtensionArtifactMetadata artifact = new ExtensionArtifactMetadata(
                ExtensionArtifactFormat.ANIYOMI_APK,
                requireAbsoluteUri(resources.apkUrl(), "APK URL", repository),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        String identity = requireValue(packageName, "package name");
        return new ExtensionPackageMetadata(
                requireValue(name, "extension name"),
                identity,
                packageLanguage(sources),
                requireVersionCode(versionCode),
                requireValue(versionName, "version name"),
                contentWarning >= 2,
                contentKind(identity),
                sources,
                List.of(artifact),
                Optional.empty(),
                Optional.of(requireAbsoluteUri(resources.iconUrl(), "icon URL", repository)));
    }

    private Resources parseResources(byte[] content) {
        ProtobufReader resources = new ProtobufReader(content);
        String apkUrl = null;
        String iconUrl = null;
        while (resources.hasRemaining()) {
            int tag = resources.readTag();
            switch (ProtobufReader.fieldNumber(tag)) {
                case 1 -> apkUrl = resources.readString(tag, "APK URL");
                case 2 -> iconUrl = resources.readString(tag, "icon URL");
                default -> resources.skip(tag);
            }
        }
        return new Resources(requireValue(apkUrl, "APK URL"), requireValue(iconUrl, "icon URL"));
    }

    private ExtensionSourceMetadata parseSource(byte[] content) {
        ProtobufReader source = new ProtobufReader(content);
        Long id = null;
        String name = null;
        String language = null;
        String homeUrl = "";
        while (source.hasRemaining()) {
            int tag = source.readTag();
            switch (ProtobufReader.fieldNumber(tag)) {
                case 1 -> id = source.readInteger(tag, "source ID");
                case 2 -> name = source.readString(tag, "source name");
                case 3 -> language = source.readString(tag, "source language");
                case 4 -> homeUrl = source.readString(tag, "source home URL");
                default -> source.skip(tag);
            }
        }
        return new ExtensionSourceMetadata(
                requireValue(name, "source name"),
                requireValue(language, "source language"),
                Long.toString(requireValue(id, "source ID")),
                sourceBaseUri(homeUrl));
    }

    private Optional<URI> sourceBaseUri(String value) {
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(value).normalize();
            String scheme = uri.getScheme();
            if (("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null) {
                return Optional.of(uri);
            }
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String packageLanguage(List<ExtensionSourceMetadata> sources) {
        Set<String> languages = new HashSet<>();
        for (ExtensionSourceMetadata source : sources) {
            languages.add(source.languageTag());
        }
        return languages.size() == 1 ? languages.iterator().next() : "all";
    }

    private ExtensionContentKind contentKind(String packageName) {
        if (packageName.contains(".animeextension.")) {
            return ExtensionContentKind.ANIME;
        }
        if (packageName.contains(".extension.")) {
            return ExtensionContentKind.MANGA;
        }
        return ExtensionContentKind.UNKNOWN;
    }

    private URI requireAbsoluteUri(URI value, String label, URI repository) {
        URI uri = value.isAbsolute() ? value : repository.resolve(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(label + " must use HTTPS");
        }
        return uri;
    }

    private URI requireAbsoluteUri(String value, String label, URI repository) {
        return requireAbsoluteUri(URI.create(requireValue(value, label)), label, repository);
    }

    private long requireVersionCode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Mihon extension is missing version code");
        }
        return value;
    }

    private <T> T requireValue(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Mihon extension is missing " + label);
        }
        return value;
    }

    private String requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mihon extension is missing " + label);
        }
        return value;
    }

    private record Resources(String apkUrl, String iconUrl) {
    }

    private static final class ProtobufReader {
        private final byte[] content;
        private int position;

        private ProtobufReader(byte[] content) {
            this.content = content.clone();
        }

        private boolean hasRemaining() {
            return position < content.length;
        }

        private int readTag() {
            long value = readVarint();
            if (value == 0 || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid Protobuf field tag");
            }
            return (int) value;
        }

        private static int fieldNumber(int tag) {
            return tag >>> 3;
        }

        private long readInteger(int tag, String label) {
            requireWireType(tag, 0, label);
            return readVarint();
        }

        private String readString(int tag, String label) {
            requireWireType(tag, 2, label);
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(readBytes()))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException(label + " is not valid UTF-8", exception);
            }
        }

        private byte[] readBytes() {
            long declaredLength = readVarint();
            if (declaredLength > Integer.MAX_VALUE || declaredLength > content.length - position) {
                throw new IllegalArgumentException("Truncated Protobuf length-delimited field");
            }
            int length = (int) declaredLength;
            byte[] value = java.util.Arrays.copyOfRange(content, position, position + length);
            position += length;
            return value;
        }

        private long readVarint() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (!hasRemaining()) {
                    throw new IllegalArgumentException("Truncated Protobuf varint");
                }
                int current = content[position++] & 0xff;
                value |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Protobuf varint exceeds 64 bits");
        }

        private void skip(int tag) {
            switch (tag & 7) {
                case 0 -> readVarint();
                case 1 -> skipBytes(8);
                case 2 -> skipBytes(readLength());
                case 5 -> skipBytes(4);
                default -> throw new IllegalArgumentException("Unsupported Protobuf wire type: " + (tag & 7));
            }
        }

        private int readLength() {
            long length = readVarint();
            if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Protobuf field is too large");
            }
            return (int) length;
        }

        private void skipBytes(int count) {
            if (count < 0 || count > content.length - position) {
                throw new IllegalArgumentException("Truncated Protobuf field");
            }
            position += count;
        }

        private void requireWireType(int tag, int expected, String label) {
            if ((tag & 7) != expected) {
                throw new IllegalArgumentException(label + " uses an invalid Protobuf wire type");
            }
        }
    }
}
