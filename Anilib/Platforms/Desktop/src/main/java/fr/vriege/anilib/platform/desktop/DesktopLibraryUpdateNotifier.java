package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.feature.updates.LibraryUpdateNotification;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotificationType;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class DesktopLibraryUpdateNotifier implements LibraryUpdateNotifier {
    private final TrayIcon icon;

    public DesktopLibraryUpdateNotifier() {
        icon = createIcon();
    }

    @Override
    public boolean available() {
        return icon != null;
    }

    @Override
    public void publish(LibraryUpdateNotification notification) {
        if (icon == null) {
            return;
        }
        if (notification.type() == LibraryUpdateNotificationType.CLEAR_PROGRESS) {
            icon.setToolTip("Anilib");
        } else if (notification.type() == LibraryUpdateNotificationType.PROGRESS) {
            icon.setToolTip(notification.title() + " — " + notification.message());
        } else {
            TrayIcon.MessageType type = notification.type() == LibraryUpdateNotificationType.FAILURE
                    ? TrayIcon.MessageType.ERROR
                    : TrayIcon.MessageType.INFO;
            icon.displayMessage(notification.title(), notification.message(), type);
        }
    }

    @Override
    public void close() {
        if (icon != null) {
            SystemTray.getSystemTray().remove(icon);
        }
    }

    private static TrayIcon createIcon() {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            return null;
        }
        Image image = loadIcon();
        if (image == null) {
            return null;
        }
        TrayIcon trayIcon = new TrayIcon(image, "Anilib");
        trayIcon.setImageAutoSize(true);
        try {
            SystemTray.getSystemTray().add(trayIcon);
            return trayIcon;
        } catch (AWTException | SecurityException exception) {
            return null;
        }
    }

    private static Image loadIcon() {
        try (InputStream input = DesktopLibraryUpdateNotifier.class
                .getResourceAsStream("/assets/anilib-icon.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException exception) {
            return null;
        }
    }
}
