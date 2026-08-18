package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackageLayoutRule implements AnilibJavaRule {
    private static final String ROOT_PACKAGE = "fr.vriege.anilib";

    public PackageLayoutRule() {
    }

    @Override
    public String name() {
        return "package-layout";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (JavaSource source : repository.javaSources()) {
            if (source.isModuleDescriptor()) {
                continue;
            }
            String packageName = source.packageName().orElse("");
            if (!packageName.startsWith(ROOT_PACKAGE)) {
                diagnostics.add(new Diagnostic(name(), source.path(), 1,
                        "Java package must start with " + ROOT_PACKAGE));
                continue;
            }
            Path sourceRoot = source.absolutePath().getParent();
            int packageSegments = packageName.split("\\.").length;
            for (int index = 0; index < packageSegments; index++) {
                sourceRoot = sourceRoot.getParent();
            }
            Path expected = sourceRoot.resolve(packageName.replace('.', java.io.File.separatorChar))
                    .resolve(source.absolutePath().getFileName())
                    .normalize();
            if (!expected.equals(source.absolutePath())) {
                diagnostics.add(new Diagnostic(name(), source.path(), 1,
                        "Package does not match the source directory"));
            }
        }
        return diagnostics;
    }
}
