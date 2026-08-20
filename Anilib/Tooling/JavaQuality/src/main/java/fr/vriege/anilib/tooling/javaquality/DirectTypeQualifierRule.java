package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DirectTypeQualifierRule implements AnilibJavaRule {
    static final Pattern DIRECT_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_$])(?:[a-z_][A-Za-z0-9_$]*\\.){2,}[A-Z][A-Za-z0-9_$]*");
    private static final Pattern DECLARATION = Pattern.compile("^\\s*(?:package|import)\\s+.*");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_$.]+)");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|record|enum|object)\\s+([A-Z][A-Za-z0-9_$]*)");

    public DirectTypeQualifierRule() {
    }

    @Override
    public String name() {
        return "direct-type-qualifier";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        repository.javaSources().stream()
                .filter(source -> !source.isModuleDescriptor())
                .forEach(source -> analyze(source.path(), source.lines(), diagnostics));
        repository.kotlinSources().forEach(source -> analyze(source.path(), source.lines(), diagnostics));
        return diagnostics;
    }

    private void analyze(Path path, List<String> lines, List<Diagnostic> diagnostics) {
        Scan scan = scan(lines);
        List<String> codeLines = scan.codeLines();
        Set<String> importableTypes = scan.importableTypes();
        for (int index = 0; index < lines.size(); index++) {
            String code = codeLines.get(index);
            if (DECLARATION.matcher(code).matches()) {
                continue;
            }
            Matcher matcher = DIRECT_TYPE.matcher(code);
            while (matcher.find()) {
                String qualifiedType = matcher.group();
                if (!importableTypes.contains(qualifiedType)) {
                    continue;
                }
                diagnostics.add(new Diagnostic(name(), path, index + 1,
                        "Import " + qualifiedType + " instead of using a fully qualified type in code"));
            }
        }
    }

    static Scan scan(List<String> lines) {
        CodeMask mask = new CodeMask();
        List<String> codeLines = new ArrayList<>(lines.size());
        Map<String, Set<String>> typesBySimpleName = new HashMap<>();
        Set<String> declaredTypes = new HashSet<>();
        for (String line : lines) {
            String code = mask.apply(line);
            codeLines.add(code);
            collectImportedType(code, typesBySimpleName);
            collectDirectTypes(code, typesBySimpleName);
            Matcher declaration = TYPE_DECLARATION.matcher(code);
            while (declaration.find()) {
                declaredTypes.add(declaration.group(1));
            }
        }

        Set<String> importableTypes = new HashSet<>();
        for (Set<String> qualifiedTypes : typesBySimpleName.values()) {
            if (qualifiedTypes.size() != 1) {
                continue;
            }
            String qualifiedType = qualifiedTypes.iterator().next();
            if (!declaredTypes.contains(simpleName(qualifiedType))) {
                importableTypes.add(qualifiedType);
            }
        }
        return new Scan(List.copyOf(codeLines), Set.copyOf(importableTypes));
    }

    private static void collectImportedType(String code, Map<String, Set<String>> typesBySimpleName) {
        Matcher imported = IMPORT.matcher(code);
        if (!imported.find()) {
            return;
        }
        String qualifiedType = imported.group(1);
        if (!qualifiedType.endsWith(".*")) {
            typesBySimpleName.computeIfAbsent(simpleName(qualifiedType), ignored -> new HashSet<>())
                    .add(qualifiedType);
        }
    }

    private static void collectDirectTypes(String code, Map<String, Set<String>> typesBySimpleName) {
        Matcher directType = DIRECT_TYPE.matcher(code);
        while (directType.find()) {
            String qualifiedType = directType.group();
            typesBySimpleName.computeIfAbsent(simpleName(qualifiedType), ignored -> new HashSet<>())
                    .add(qualifiedType);
        }
    }

    private static String simpleName(String qualifiedType) {
        return qualifiedType.substring(qualifiedType.lastIndexOf('.') + 1);
    }

    record Scan(List<String> codeLines, Set<String> importableTypes) {
    }

    private static final class CodeMask {
        private boolean blockComment;
        private boolean textBlock;

        private String apply(String line) {
            StringBuilder code = new StringBuilder(line.length());
            int index = 0;
            while (index < line.length()) {
                if (blockComment) {
                    int end = line.indexOf("*/", index);
                    if (end < 0) {
                        code.append(" ".repeat(line.length() - index));
                        return code.toString();
                    }
                    code.append(" ".repeat(end + 2 - index));
                    index = end + 2;
                    blockComment = false;
                    continue;
                }
                if (textBlock) {
                    int end = line.indexOf("\"\"\"", index);
                    if (end < 0) {
                        code.append(" ".repeat(line.length() - index));
                        return code.toString();
                    }
                    code.append(" ".repeat(end + 3 - index));
                    index = end + 3;
                    textBlock = false;
                    continue;
                }
                if (line.startsWith("//", index)) {
                    code.append(" ".repeat(line.length() - index));
                    break;
                }
                if (line.startsWith("/*", index)) {
                    blockComment = true;
                    code.append("  ");
                    index += 2;
                    continue;
                }
                if (line.startsWith("\"\"\"", index)) {
                    textBlock = true;
                    code.append("   ");
                    index += 3;
                    continue;
                }
                char character = line.charAt(index);
                if (character == '"' || character == '\'') {
                    int end = quotedEnd(line, index, character);
                    code.append(" ".repeat(end - index));
                    index = end;
                    continue;
                }
                code.append(character);
                index++;
            }
            return code.toString();
        }

        private static int quotedEnd(String line, int start, char quote) {
            boolean escaped = false;
            for (int index = start + 1; index < line.length(); index++) {
                char character = line.charAt(index);
                if (character == quote && !escaped) {
                    return index + 1;
                }
                escaped = character == '\\' && !escaped;
                if (character != '\\') {
                    escaped = false;
                }
            }
            return line.length();
        }
    }
}
