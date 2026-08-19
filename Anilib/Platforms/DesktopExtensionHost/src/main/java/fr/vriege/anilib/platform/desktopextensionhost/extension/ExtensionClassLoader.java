package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

final class ExtensionClassLoader extends URLClassLoader {
    private static final String COMPATIBILITY_PACKAGE =
            "fr.vriege.anilib.platform.desktopextensionhost.compat.";

    ExtensionClassLoader(Path archive) throws java.net.MalformedURLException {
        super(new URL[]{archive.toUri().toURL()}, ExtensionClassLoader.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (parentFirst(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private static boolean parentFirst(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith(COMPATIBILITY_PACKAGE);
    }
}
