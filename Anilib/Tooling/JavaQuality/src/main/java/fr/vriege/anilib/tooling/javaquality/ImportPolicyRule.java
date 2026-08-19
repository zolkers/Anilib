package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportPolicyRule implements AnilibJavaRule {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+)\\s*;");

    public ImportPolicyRule() {
    }

    @Override
    public String name() {
        return "import-policy";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (JavaSource source : repository.javaSources()) {
            for (int index = 0; index < source.lines().size(); index++) {
                Matcher matcher = IMPORT.matcher(source.lines().get(index));
                if (!matcher.matches()) {
                    continue;
                }
                String imported = matcher.group(1);
                if (imported.endsWith(".*")) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Wildcard imports are forbidden"));
                }
                if (!isAllowed(imported, source)) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "External import is forbidden: " + imported));
                }
            }
        }
        return diagnostics;
    }

    private static boolean isAllowed(String imported, JavaSource source) {
        return imported.startsWith("java.")
                || imported.startsWith("javax.")
                || imported.startsWith("org.w3c.dom.")
                || imported.startsWith("org.xml.sax.")
                || imported.startsWith("fr.vriege.anilib.")
                || isJdkHttpServer(imported, source)
                || isDesktopExtensionHostDependency(imported, source);
    }

    private static boolean isJdkHttpServer(String imported, JavaSource source) {
        return (source.module().id().equals("tooling.architecture-tests")
                || source.module().id().equals("platform.desktop-extension-host"))
                && imported.startsWith("com.sun.net.httpserver.");
    }

    private static boolean isDesktopExtensionHostDependency(String imported, JavaSource source) {
        if (!source.module().id().equals("platform.desktop-extension-host")) {
            return false;
        }
        return imported.startsWith("com.googlecode.d2j.dex.")
                || imported.startsWith("kotlinx.serialization.json.")
                || imported.startsWith("net.dongliu.apk.parser.")
                || imported.startsWith("okhttp3.")
                || imported.startsWith("org.jsoup.")
                || imported.startsWith("org.objectweb.asm.");
    }
}
