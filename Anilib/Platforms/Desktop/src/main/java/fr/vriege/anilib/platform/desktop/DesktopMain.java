package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.kernel.StartedAnilib;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

/** JDK-only desktop entry point for the Standard Anilib product. */
public final class DesktopMain {
    private DesktopMain() {
    }

    public static void main(String[] arguments) throws InvocationTargetException, InterruptedException {
        StartedAnilib application = StandardAnilib.start();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println(summary(application));
            application.close();
            return;
        }

        SwingUtilities.invokeAndWait(() -> showWindow(application));
    }

    private static void showWindow(StartedAnilib application) {
        LibraryCatalog catalog = application.capability(LibraryCapabilities.CATALOG);
        JFrame frame = new JFrame("Anilib");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                application.close();
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel title = new JLabel("Anilib", SwingConstants.LEADING);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28.0F));
        root.add(title, BorderLayout.NORTH);

        DefaultListModel<String> titles = new DefaultListModel<>();
        for (LibraryItem item : catalog.snapshot()) {
            titles.addElement(item.title() + " · " + item.kind());
        }
        if (titles.isEmpty()) {
            titles.addElement("Your library is empty — source and import features come next.");
        }
        root.add(new JScrollPane(new JList<>(titles)), BorderLayout.CENTER);

        JLabel status = new JLabel(application.components().size() + " feature bundle(s) active");
        root.add(status, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static String summary(StartedAnilib application) {
        int itemCount = application.capability(LibraryCapabilities.CATALOG).snapshot().size();
        return "Anilib started headlessly with " + application.components().size()
                + " bundle(s) and " + itemCount + " library item(s).";
    }
}
