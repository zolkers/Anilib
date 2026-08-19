package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class TranslationCatalog {
    private final ComponentId owner;
    private final Map<String, String> english;
    private final Map<String, String> french;

    public TranslationCatalog(
            ComponentId owner,
            Map<String, String> english,
            Map<String, String> french) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.english = validated(english, "English");
        this.french = validated(french, "French");
        if (!this.english.keySet().equals(this.french.keySet())) {
            throw new IllegalArgumentException("English and French translation keys must match for " + owner);
        }
    }

    public static TranslationCatalog french(String owner, Map<String, String> messages) {
        Objects.requireNonNull(messages, "messages");
        LinkedHashMap<String, String> english = new LinkedHashMap<>();
        messages.keySet().forEach(source -> english.put(source, source));
        return new TranslationCatalog(ComponentId.of(owner), english, messages);
    }

    public static TranslationCatalog resources(String owner, Class<?> anchor, String resourceRoot) {
        Objects.requireNonNull(anchor, "anchor");
        String root = Objects.requireNonNull(resourceRoot, "resourceRoot");
        if (root.isBlank() || root.startsWith("/") || root.contains("..")) {
            throw new IllegalArgumentException("resourceRoot must be a normalized classpath location");
        }
        return new TranslationCatalog(
                ComponentId.of(owner),
                load(anchor, root + "/en.properties"),
                load(anchor, root + "/fr.properties"));
    }

    public ComponentId owner() {
        return owner;
    }

    public Map<String, String> english() {
        return english;
    }

    public Map<String, String> french() {
        return french;
    }

    public Optional<String> translate(String languageTag, String source) {
        Objects.requireNonNull(languageTag, "languageTag");
        Objects.requireNonNull(source, "source");
        String key = key(source);
        if (key == null) {
            return Optional.empty();
        }
        String language = Locale.forLanguageTag(languageTag).getLanguage();
        if (Locale.ENGLISH.getLanguage().equals(language)) {
            return Optional.of(english.get(key));
        }
        if (Locale.FRENCH.getLanguage().equals(language)) {
            return Optional.of(french.get(key));
        }
        return Optional.empty();
    }

    private String key(String keyOrEnglishMessage) {
        if (english.containsKey(keyOrEnglishMessage)) {
            return keyOrEnglishMessage;
        }
        for (Map.Entry<String, String> entry : english.entrySet()) {
            if (entry.getValue().equals(keyOrEnglishMessage)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, String> validated(Map<String, String> messages, String language) {
        Objects.requireNonNull(messages, "messages");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        messages.forEach((key, message) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(language + " translation key must not be blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException(language + " translation must not be blank for " + key);
            }
            copy.put(key, message);
        });
        return Map.copyOf(copy);
    }

    private static Map<String, String> load(Class<?> anchor, String resource) {
        ClassLoader loader = Objects.requireNonNull(anchor.getClassLoader(), "anchor class loader");
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Translation resource is missing: " + resource);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            LinkedHashMap<String, String> messages = new LinkedHashMap<>();
            properties.stringPropertyNames().stream().sorted()
                    .forEach(key -> messages.put(key, properties.getProperty(key)));
            return Map.copyOf(messages);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read translation resource " + resource, exception);
        }
    }
}
