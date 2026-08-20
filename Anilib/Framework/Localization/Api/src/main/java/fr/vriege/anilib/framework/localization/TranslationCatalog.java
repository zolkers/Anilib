package fr.vriege.anilib.framework.localization;

import fr.vriege.anilib.foundation.component.ComponentId;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public final class TranslationCatalog {
    private final ComponentId owner;
    private final Map<String, String> english;
    private final Map<String, String> french;
    private final Map<String, String> englishAliases;
    private final List<MessageTemplate> frenchTemplates;

    private TranslationCatalog(
            ComponentId owner,
            Map<String, String> english,
            Map<String, String> french) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.english = validated(english, "English");
        this.french = validated(french, "French");
        if (!this.english.keySet().equals(this.french.keySet())) {
            throw new IllegalArgumentException("English and French translation keys must match for " + owner);
        }
        this.englishAliases = aliases(this.english);
        this.frenchTemplates = templates(this.english, this.french);
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

    public Optional<String> translate(String languageTag, String source) {
        Objects.requireNonNull(languageTag, "languageTag");
        Objects.requireNonNull(source, "source");
        String key = key(source);
        String language = Locale.forLanguageTag(languageTag).getLanguage();
        if (Locale.ENGLISH.getLanguage().equals(language) && key != null) {
            return Optional.of(english.get(key));
        }
        if (Locale.FRENCH.getLanguage().equals(language)) {
            if (key != null) {
                return Optional.of(french.get(key));
            }
            for (MessageTemplate template : frenchTemplates) {
                Optional<String> translated = template.apply(source, french, englishAliases);
                if (translated.isPresent()) {
                    return translated;
                }
            }
        }
        return Optional.empty();
    }

    private String key(String keyOrEnglishMessage) {
        if (english.containsKey(keyOrEnglishMessage)) {
            return keyOrEnglishMessage;
        }
        return englishAliases.get(keyOrEnglishMessage);
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

    private static List<MessageTemplate> templates(
            Map<String, String> english,
            Map<String, String> translated) {
        List<MessageTemplate> templates = new ArrayList<>();
        english.forEach((key, source) -> MessageTemplate.create(source, translated.get(key))
                .ifPresent(templates::add));
        templates.sort(Comparator.comparingInt(MessageTemplate::specificity).reversed());
        return List.copyOf(templates);
    }

    private static Map<String, String> aliases(Map<String, String> messages) {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        messages.forEach((key, message) -> aliases.putIfAbsent(message, key));
        return Map.copyOf(aliases);
    }

    private record MessageTemplate(
            Pattern pattern,
            List<Integer> argumentIndexes,
            String translated,
            int specificity) {
        private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

        private static Optional<MessageTemplate> create(String source, String translated) {
            var placeholders = PLACEHOLDER.matcher(source);
            if (!placeholders.find()) {
                return Optional.empty();
            }
            StringBuilder expression = new StringBuilder("^");
            List<Integer> indexes = new ArrayList<>();
            int cursor = 0;
            int literalLength = 0;
            do {
                String literal = source.substring(cursor, placeholders.start());
                expression.append(Pattern.quote(literal)).append("(.*?)");
                literalLength += literal.length();
                indexes.add(Integer.parseInt(placeholders.group(1)));
                cursor = placeholders.end();
            } while (placeholders.find());
            String tail = source.substring(cursor);
            expression.append(Pattern.quote(tail)).append('$');
            literalLength += tail.length();
            Set<Integer> sourceIndexes = Set.copyOf(indexes);
            Set<Integer> translatedIndexes = new HashSet<>();
            var translatedPlaceholders = PLACEHOLDER.matcher(translated);
            while (translatedPlaceholders.find()) {
                translatedIndexes.add(Integer.parseInt(translatedPlaceholders.group(1)));
            }
            if (!sourceIndexes.equals(translatedIndexes)) {
                throw new IllegalArgumentException("Translation template placeholders must match: " + source);
            }
            return Optional.of(new MessageTemplate(
                    Pattern.compile(expression.toString(), Pattern.DOTALL),
                    List.copyOf(indexes),
                    translated,
                    literalLength));
        }

        private Optional<String> apply(
                String source,
                Map<String, String> translatedMessages,
                Map<String, String> englishAliases) {
            var matcher = pattern.matcher(source);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            String result = translated;
            for (int capture = 0; capture < argumentIndexes.size(); capture++) {
                String argument = matcher.group(capture + 1);
                String translatedArgument = translateExact(argument, translatedMessages, englishAliases);
                result = result.replace("{" + argumentIndexes.get(capture) + "}", translatedArgument);
            }
            return Optional.of(result);
        }

        private static String translateExact(
                String source,
                Map<String, String> translatedMessages,
                Map<String, String> englishAliases) {
            String key = englishAliases.get(source);
            return key == null ? source : translatedMessages.get(key);
        }
    }
}
