package fr.vriege.anilib.feature.settings.runtime;

import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.AccentColor;
import fr.vriege.anilib.feature.settings.LanguagePack;
import fr.vriege.anilib.feature.settings.NavigationStyle;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeFamily;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.TypographyScale;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class FileSettingsService implements SettingsService {
    private static final String LANGUAGE = "appearance.language";
    private static final String THEME = "appearance.theme";
    private static final String THEME_FAMILY = "appearance.theme-family";
    private static final String ACCENT = "appearance.accent";
    private static final String TYPOGRAPHY = "appearance.typography";
    private static final String NAVIGATION = "appearance.navigation";
    private static final String START_SCREEN = "appearance.start-screen";
    private static final String ADULT_CONTENT = "content.show-adult";
    private static final String INCOGNITO = "privacy.incognito";
    private static final String DOWNLOAD_WIFI = "downloads.wifi-only";
    private static final String UPDATE_WIFI = "updates.wifi-only";

    private final Path file;
    private final List<Consumer<SettingsSnapshot>> observers = new CopyOnWriteArrayList<>();
    private SettingsSnapshot current;

    public FileSettingsService(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        current = load();
    }

    @Override
    public synchronized SettingsSnapshot snapshot() {
        return current;
    }

    @Override
    public void replace(SettingsSnapshot settings) {
        SettingsSnapshot next = Objects.requireNonNull(settings, "settings must not be null");
        synchronized (this) {
            if (current.equals(next)) {
                return;
            }
            persist(next);
            current = next;
        }
        observers.forEach(observer -> observer.accept(next));
    }

    @Override
    public AutoCloseable observe(Consumer<SettingsSnapshot> observer) {
        Consumer<SettingsSnapshot> checked = Objects.requireNonNull(observer, "observer must not be null");
        observers.add(checked);
        checked.accept(snapshot());
        return () -> observers.remove(checked);
    }

    private SettingsSnapshot load() {
        SettingsSnapshot defaults = SettingsSnapshot.defaults();
        if (!Files.exists(file)) {
            return defaults;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("Settings path must be a regular file");
        }
        Properties values = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException exception) {
            throw failure("load settings", exception);
        }
        return new SettingsSnapshot(
                enumValue(values.getProperty(LANGUAGE), defaults.languagePack(), LanguagePack.class),
                enumValue(values.getProperty(THEME), defaults.themeMode(), ThemeMode.class),
                enumValue(values.getProperty(THEME_FAMILY), defaults.themeFamily(), ThemeFamily.class),
                enumValue(values.getProperty(ACCENT), defaults.accentColor(), AccentColor.class),
                enumValue(values.getProperty(TYPOGRAPHY), defaults.typographyScale(), TypographyScale.class),
                enumValue(values.getProperty(NAVIGATION), defaults.navigationStyle(), NavigationStyle.class),
                enumValue(values.getProperty(START_SCREEN), defaults.startScreen(), StartScreen.class),
                flag(values, ADULT_CONTENT, defaults.showAdultContent()),
                flag(values, INCOGNITO, defaults.incognitoMode()),
                flag(values, DOWNLOAD_WIFI, defaults.downloadOnlyOnWifi()),
                flag(values, UPDATE_WIFI, defaults.updateOnlyOnWifi()));
    }

    private void persist(SettingsSnapshot settings) {
        Properties values = new Properties();
        values.setProperty(LANGUAGE, setting(settings.languagePack()));
        values.setProperty(THEME, settings.themeMode().name().toLowerCase(Locale.ROOT));
        values.setProperty(THEME_FAMILY, setting(settings.themeFamily()));
        values.setProperty(ACCENT, setting(settings.accentColor()));
        values.setProperty(TYPOGRAPHY, setting(settings.typographyScale()));
        values.setProperty(NAVIGATION, setting(settings.navigationStyle()));
        values.setProperty(START_SCREEN, settings.startScreen().name().toLowerCase(Locale.ROOT));
        values.setProperty(ADULT_CONTENT, Boolean.toString(settings.showAdultContent()));
        values.setProperty(INCOGNITO, Boolean.toString(settings.incognitoMode()));
        values.setProperty(DOWNLOAD_WIFI, Boolean.toString(settings.downloadOnlyOnWifi()));
        values.setProperty(UPDATE_WIFI, Boolean.toString(settings.updateOnlyOnWifi()));
        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".settings-", ".tmp");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedWriter writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8))) {
                values.store(writer, "Anilib settings");
                writer.flush();
                channel.force(true);
            }
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw failure("store settings", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private static String setting(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E enumValue(String value, E fallback, Class<E> type) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static boolean flag(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        if (value == null) {
            return fallback;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic settings replacement is unavailable", exception);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The primary operation reports the actionable error.
        }
    }

    private static IllegalStateException failure(String operation, IOException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
