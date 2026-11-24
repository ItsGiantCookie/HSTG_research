package de.germanrangers.hstglabs.guard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads Java sources for the guard tests (docs/design.md §12.2). Finds every reference to a
 * package, whether imported or written fully qualified in code, so a guard cannot be dodged by
 * skipping the import. Comments and string literals are ignored.
 */
final class SourceScanner {

    /** Base package of our sources. */
    static final String BASE = "de.germanrangers.hstglabs";
    private static final Pattern QUALIFIED = Pattern.compile("\\b((?:[a-z_][a-z0-9_]*\\.)+[A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** One source file: its package and its code with comments and strings blanked out. */
    record Source(Path path, String pkg, String code, String raw) {

        /** Package relative to {@link #BASE}, e.g. "mek.client"; empty for the base package. */
        String relativePackage() {
            return pkg.equals(BASE) ? "" : pkg.startsWith(BASE + ".") ? pkg.substring(BASE.length() + 1) : pkg;
        }

        boolean inPackage(String relative) {
            String rel = relativePackage();
            return rel.equals(relative) || rel.startsWith(relative + ".");
        }

        /** Every dotted name starting with {@code prefix} referenced in code (imports included). */
        List<String> references(String prefix) {
            List<String> found = new ArrayList<>();
            Matcher m = QUALIFIED.matcher(code);
            while (m.find()) {
                String name = m.group(1);
                if (name.startsWith(prefix) && !isOwnPackageLine(m.start())) {
                    found.add(name);
                }
            }
            return found;
        }

        private boolean isOwnPackageLine(int index) {
            int lineStart = code.lastIndexOf('\n', index) + 1;
            return code.startsWith("package", skipSpaces(code, lineStart));
        }
    }

    private SourceScanner() {
    }

    static Path projectDir() {
        String dir = System.getProperty("hstglabs.projectDir");
        return Path.of(dir != null ? dir : ".").toAbsolutePath().normalize();
    }

    static Path mainSources() {
        return projectDir().resolve("src/main/java");
    }

    static List<Source> scan(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().map(SourceScanner::read).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Source read(Path path) {
        try {
            return parse(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Source parse(Path path, String raw) {
        String code = stripCommentsAndStrings(raw);
        Matcher pkg = PACKAGE.matcher(code);
        return new Source(path, pkg.find() ? pkg.group(1) : "", code, raw);
    }

    /** Replaces comments, string and char literals with spaces, keeping offsets and newlines. */
    static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(src, i, end, out);
                i = end;
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                blank(src, i, end, out);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                int end = Math.min(n, j + 1);
                blank(src, i, end, out);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(String src, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(src.charAt(k) == '\n' ? '\n' : ' ');
        }
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    static String describe(Source source, String detail) {
        Path rel = source.path().isAbsolute() && source.path().startsWith(projectDir())
                   ? projectDir().relativize(source.path()) : source.path();
        return rel.toString().replace('\\', '/') + ": " + detail;
    }
}
