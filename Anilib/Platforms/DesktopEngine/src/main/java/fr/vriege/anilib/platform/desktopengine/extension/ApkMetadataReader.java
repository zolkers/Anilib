package fr.vriege.anilib.platform.desktopengine.extension;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApkMetadataReader {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String NAME = "tachiyomi.extension.name";
    private static final String MANGA_CLASS = "tachiyomi.extension.class";
    private static final String MANGA_FACTORY = "tachiyomi.extension.factory";
    private static final String MANGA_ADULT = "tachiyomi.extension.nsfw";
    private static final String ANIME_CLASS = "tachiyomi.animeextension.class";
    private static final String ANIME_FACTORY = "tachiyomi.animeextension.factory";
    private static final String ANIME_ADULT = "tachiyomi.animeextension.nsfw";
    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;

    public ExtensionApkMetadata read(Path apkPath) {
        Path apk = requireApk(apkPath);
        ExtensionToolBridge.RawApkMetadata apkMetadata = ExtensionToolBridge.readMetadata(apk);
        try {
            Map<String, String> metadata = manifestMetadata(apkMetadata.manifestXml());
            String packageName = required(apkMetadata.packageName(), "APK package name");
            boolean anime = hasText(metadata.get(ANIME_CLASS)) || hasText(metadata.get(ANIME_FACTORY));
            ExtensionKind kind = anime ? ExtensionKind.ANIME : ExtensionKind.MANGA;
            String classKey = anime ? ANIME_CLASS : MANGA_CLASS;
            String factoryKey = anime ? ANIME_FACTORY : MANGA_FACTORY;
            String adultKey = anime ? ANIME_ADULT : MANGA_ADULT;
            List<String> sources = parseClasses(metadata.get(classKey), packageName);
            Optional<String> factory = optionalClass(metadata.get(factoryKey), packageName);
            String fallbackName = packageName.substring(packageName.lastIndexOf('.') + 1);
            return new ExtensionApkMetadata(
                    packageName,
                    textOr(metadata.get(NAME), fallbackName),
                    textOr(apkMetadata.versionName(), "0"),
                    apkMetadata.versionCode(),
                    kind,
                    "1".equals(metadata.get(adultKey)),
                    sources,
                    factory);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    private static Path requireApk(Path value) {
        Path path = value.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("Extension APK must be a regular non-link file");
            }
            long size = Files.size(path);
            if (size == 0 || size > MAX_APK_BYTES) {
                throw new IllegalArgumentException("Extension APK size is outside the accepted range");
            }
            return path;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect extension APK", exception);
        }
    }

    private static Map<String, String> manifestMetadata(String manifest) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest)));
            NodeList applications = document.getElementsByTagName("application");
            if (applications.getLength() != 1) {
                throw new IllegalArgumentException("APK manifest must contain one application element");
            }
            Map<String, String> result = new LinkedHashMap<>();
            NodeList children = applications.item(0).getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node node = children.item(index);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"meta-data".equals(node.getNodeName())) {
                    continue;
                }
                Element element = (Element) node;
                String name = androidAttribute(element, "name");
                String value = androidAttribute(element, "value");
                if (hasText(name)) {
                    result.put(name, value);
                }
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse extension manifest", exception);
        }
    }

    private static String androidAttribute(Element element, String name) {
        String value = element.getAttributeNS(ANDROID_NAMESPACE, name);
        return hasText(value) ? value : element.getAttribute("android:" + name);
    }

    private static List<String> parseClasses(String value, String packageName) {
        if (!hasText(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String candidate : value.split(";")) {
            if (hasText(candidate)) {
                result.add(resolveClass(candidate.strip(), packageName));
            }
        }
        return List.copyOf(result);
    }

    private static Optional<String> optionalClass(String value, String packageName) {
        return hasText(value) ? Optional.of(resolveClass(value.strip(), packageName)) : Optional.empty();
    }

    private static String resolveClass(String value, String packageName) {
        String resolved = value.startsWith(".") ? packageName + value : value;
        if (!resolved.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
            throw new IllegalArgumentException("Invalid extension entry point: " + value);
        }
        return resolved;
    }

    private static String required(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value.strip();
    }

    private static String textOr(String value, String fallback) {
        return hasText(value) ? value.strip() : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
