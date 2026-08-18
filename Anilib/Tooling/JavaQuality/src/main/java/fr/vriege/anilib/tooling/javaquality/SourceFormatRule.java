package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;

public final class SourceFormatRule implements AnilibJavaRule {
    private static final int MAX_LINE_LENGTH = 120;

    public SourceFormatRule() {
    }

    @Override
    public String name() {
        return "source-format";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (JavaSource source : repository.javaSources()) {
            for (int index = 0; index < source.lines().size(); index++) {
                String line = source.lines().get(index);
                if (line.indexOf('\t') >= 0) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Tabs are forbidden in Java sources"));
                }
                if (!line.isEmpty() && Character.isWhitespace(line.charAt(line.length() - 1))) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Trailing whitespace is forbidden"));
                }
                if (line.length() > MAX_LINE_LENGTH) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Line exceeds " + MAX_LINE_LENGTH + " characters"));
                }
                if (line.contains("/" + "**")) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Javadoc blocks are forbidden"));
                }
            }
        }
        return diagnostics;
    }
}
