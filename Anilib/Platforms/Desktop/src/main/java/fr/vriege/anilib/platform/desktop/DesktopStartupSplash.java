package fr.vriege.anilib.platform.desktop;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

final class DesktopStartupSplash implements AutoCloseable {
    private static final int WINDOW_SIZE = 256;
    private static final int FRAME_MILLIS = 16;
    private static final long INTRO_NANOS = 420_000_000L;
    private static final long EXIT_NANOS = 180_000_000L;

    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile JWindow window;
    private volatile SplashPanel panel;
    private volatile Timer timer;

    private DesktopStartupSplash() {
    }

    static DesktopStartupSplash open() {
        DesktopStartupSplash splash = new DesktopStartupSplash();
        splash.show();
        return splash;
    }

    void setReducedMotion(boolean reducedMotion) {
        EventQueue.invokeLater(() -> {
            SplashPanel current = panel;
            if (current != null) {
                current.setReducedMotion(reducedMotion);
            }
        });
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        EventQueue.invokeLater(() -> {
            SplashPanel current = panel;
            if (current == null || current.reducedMotion()) {
                dispose();
            } else {
                current.beginExit();
            }
        });
    }

    private void show() {
        try {
            if (EventQueue.isDispatchThread()) {
                createWindow();
            } else {
                EventQueue.invokeAndWait(this::createWindow);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            dispose();
        } catch (InvocationTargetException exception) {
            dispose();
        }
    }

    private void createWindow() {
        BufferedImage icon = loadIcon();
        if (icon == null) {
            return;
        }
        SplashPanel splashPanel = new SplashPanel(icon);
        JWindow splashWindow = new JWindow();
        splashWindow.setType(Window.Type.UTILITY);
        splashWindow.setBackground(new Color(0, 0, 0, 0));
        splashWindow.setFocusableWindowState(false);
        splashWindow.setAlwaysOnTop(true);
        splashWindow.setContentPane(splashPanel);
        splashWindow.setSize(WINDOW_SIZE, WINDOW_SIZE);
        splashWindow.setLocationRelativeTo(null);
        Timer animationTimer = new Timer(FRAME_MILLIS, ignored -> {
            splashPanel.repaint();
            if (splashPanel.exitFinished()) {
                dispose();
            }
        });
        animationTimer.setCoalesce(true);
        window = splashWindow;
        panel = splashPanel;
        timer = animationTimer;
        splashWindow.setVisible(true);
        animationTimer.start();
    }

    private void dispose() {
        EventQueue.invokeLater(() -> {
            Timer currentTimer = timer;
            if (currentTimer != null) {
                currentTimer.stop();
            }
            JWindow currentWindow = window;
            if (currentWindow != null) {
                currentWindow.setVisible(false);
                currentWindow.dispose();
            }
            timer = null;
            panel = null;
            window = null;
        });
    }

    private static BufferedImage loadIcon() {
        try (InputStream input = DesktopStartupSplash.class.getResourceAsStream("/assets/anilib-icon.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static final class SplashPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final transient BufferedImage icon;
        private final long startedAt = System.nanoTime();
        private volatile boolean reducedMotion;
        private volatile long exitStartedAt;

        private SplashPanel(BufferedImage icon) {
            this.icon = icon;
            setOpaque(false);
        }

        private void setReducedMotion(boolean value) {
            reducedMotion = value;
            repaint();
        }

        private boolean reducedMotion() {
            return reducedMotion;
        }

        private void beginExit() {
            exitStartedAt = System.nanoTime();
        }

        private boolean exitFinished() {
            long exit = exitStartedAt;
            return exit != 0L && System.nanoTime() - exit >= EXIT_NANOS;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            long now = System.nanoTime();
            float intro = reducedMotion ? 1.0f : clamp((float) (now - startedAt) / INTRO_NANOS);
            float easedIntro = 1.0f - (float) Math.pow(1.0f - intro, 3.0d);
            float exit = exitStartedAt == 0L ? 0.0f : clamp((float) (now - exitStartedAt) / EXIT_NANOS);
            float opacity = 1.0f - exit;
            float pulse = reducedMotion || intro < 1.0f
                    ? 0.0f
                    : (float) Math.sin((now - startedAt) / 420_000_000.0d) * 0.008f;
            float scale = reducedMotion ? 0.88f : 0.76f + easedIntro * 0.12f + pulse + exit * 0.025f;
            int iconSize = Math.round(WINDOW_SIZE * scale);
            int offset = (WINDOW_SIZE - iconSize) / 2;
            Graphics2D output = (Graphics2D) graphics.create();
            try {
                output.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                output.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                output.setComposite(AlphaComposite.SrcOver.derive(opacity * easedIntro));
                output.drawImage(icon, offset, offset, iconSize, iconSize, null);
            } finally {
                output.dispose();
            }
        }

        private static float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }
}
