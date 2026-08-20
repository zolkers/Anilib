package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DirectTypeQualifierFormatter {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_$.]+)");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+.*");

    public DirectTypeQualifierFormatter() {
    }

    public int format(RepositorySnapshot repository) {
        int changed = 0;
        for (JavaSource source : repository.javaSources()) {
            if (!source.isModuleDescriptor() && format(source.absolutePath(), source.lines())) {
                changed++;
            }
        }
        for (KotlinSource source : repository.kotlinSources()) {
            if (format(source.absolutePath(), source.lines())) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean format(Path path, List<String> originalLines) {
        List<String> sourceLines = originalLines;
        DirectTypeQualifierRule.Scan scan = DirectTypeQualifierRule.scan(sourceLines);
        Set<String> qualifiedTypes = scan.importableTypes();
        if (qualifiedTypes.isEmpty()) {
            return false;
        }

        Set<String> importedTypes = importedTypes(scan.codeLines());
        List<String> lines = new ArrayList<>(sourceLines.size() + qualifiedTypes.size());
        boolean replaced = false;
        for (int index = 0; index < sourceLines.size(); index++) {
            String line = sourceLines.get(index);
            String code = scan.codeLines().get(index);
            if (!IMPORT.matcher(code).find() && !PACKAGE.matcher(code).matches()) {
                String rewritten = replaceQualifiedTypes(line, code, qualifiedTypes);
                replaced |= !rewritten.equals(line);
                line = rewritten;
            }
            lines.add(line);
        }
        if (!replaced) {
            return false;
        }

        List<String> importsToAdd = qualifiedTypes.stream()
                .filter(type -> !importedTypes.contains(type))
                .sorted()
                .map(type -> "import " + type + (isKotlin(path) ? "" : ";"))
                .toList();
        if (!importsToAdd.isEmpty()) {
            DirectTypeQualifierRule.Scan rewrittenScan = DirectTypeQualifierRule.scan(lines);
            lines.addAll(importInsertionIndex(rewrittenScan.codeLines()), importsToAdd);
        }
        write(path, lines);
        return true;
    }

    private static String replaceQualifiedTypes(String line, String code, Set<String> qualifiedTypes) {
        Matcher matcher = DirectTypeQualifierRule.DIRECT_TYPE.matcher(code);
        StringBuilder rewritten = new StringBuilder(line.length());
        int cursor = 0;
        while (matcher.find()) {
            String qualifiedType = matcher.group();
            if (!qualifiedTypes.contains(qualifiedType)) {
                continue;
            }
            rewritten.append(line, cursor, matcher.start());
            rewritten.append(simpleName(qualifiedType));
            cursor = matcher.end();
        }
        if (cursor == 0) {
            return line;
        }
        return rewritten.append(line, cursor, line.length()).toString();
    }

    private static Set<String> importedTypes(List<String> codeLines) {
        Set<String> importedTypes = new HashSet<>();
        for (String code : codeLines) {
            Matcher matcher = IMPORT.matcher(code);
            if (matcher.find()) {
                importedTypes.add(matcher.group(1));
            }
        }
        return importedTypes;
    }

    private static int importInsertionIndex(List<String> codeLines) {
        int lastImport = -1;
        int packageLine = -1;
        for (int index = 0; index < codeLines.size(); index++) {
            String line = codeLines.get(index);
            if (IMPORT.matcher(line).find()) {
                lastImport = index;
            } else if (PACKAGE.matcher(line).matches()) {
                packageLine = index;
            }
        }
        return lastImport >= 0 ? lastImport + 1 : packageLine + 1;
    }

    private static String simpleName(String qualifiedType) {
        return qualifiedType.substring(qualifiedType.lastIndexOf('.') + 1);
    }

    private static boolean isKotlin(Path path) {
        return path.getFileName().toString().endsWith(".kt");
    }

    private static void write(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to format " + path, exception);
        }
    }
}
