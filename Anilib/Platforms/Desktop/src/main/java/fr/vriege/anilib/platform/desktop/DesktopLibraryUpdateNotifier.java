package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.feature.updates.LibraryUpdateNotification;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotificationType;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

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
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(103, 80, 164));
            graphics.fillRoundRect(1, 1, 30, 30, 10, 10);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(8, 8, 4, 16);
            graphics.fillRect(20, 8, 4, 16);
            graphics.fillRect(12, 8, 8, 4);
            graphics.fillRect(12, 20, 8, 4);
        } finally {
            graphics.dispose();
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
}
