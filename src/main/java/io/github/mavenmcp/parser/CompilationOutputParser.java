package io.github.mavenmcp.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.mavenmcp.model.CompilationError;

/**
 * Parses Maven/javac compilation output to extract structured error and warning information.
 * <p>
 * Handles two formats:
 * <ul>
 *   <li>{@code [ERROR] /path/File.java:[line,col] message} (with column)</li>
 *   <li>{@code [ERROR] /path/File.java:[line] message} (without column)</li>
 * </ul>
 * Same patterns apply for {@code [WARNING]}.
 */
public final class CompilationOutputParser {

    // [ERROR] /path/File.java:[line,col] message
    private static final Pattern ERROR_WITH_COL =
            Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.+)");
    // [ERROR] /path/File.java:[line] message
    private static final Pattern ERROR_NO_COL =
            Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+)\\]\\s+(.+)");
    // [WARNING] /path/File.java:[line,col] message
    private static final Pattern WARN_WITH_COL =
            Pattern.compile("\\[WARNING\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.+)");
    // [WARNING] /path/File.java:[line] message
    private static final Pattern WARN_NO_COL =
            Pattern.compile("\\[WARNING\\]\\s+(.+\\.java):\\[(\\d+)\\]\\s+(.+)");

    private CompilationOutputParser() {
        // utility class
    }

    /**
     * Parse Maven stdout for compilation errors and warnings.
     *
     * @param stdout     the complete Maven stdout output
     * @param projectDir the project root directory for path relativization
     * @return a ParseResult containing separate lists of errors and warnings
     */
    public static ParseResult parse(String stdout, Path projectDir) {
        List<CompilationError> errors = new ArrayList<>();
        List<CompilationError> warnings = new ArrayList<>();

        if (stdout == null || stdout.isEmpty()) {
            return new ParseResult(errors, warnings);
        }

        for (String line : stdout.split("\n")) {
            // Try error patterns first
            CompilationError error = tryMatch(line, ERROR_WITH_COL, true, "ERROR", projectDir);
            if (error == null) {
                error = tryMatch(line, ERROR_NO_COL, false, "ERROR", projectDir);
            }
            if (error != null) {
                errors.add(error);
                continue;
            }

            // Try warning patterns
            CompilationError warning = tryMatch(line, WARN_WITH_COL, true, "WARNING", projectDir);
            if (warning == null) {
                warning = tryMatch(line, WARN_NO_COL, false, "WARNING", projectDir);
            }
            if (warning != null) {
                warnings.add(warning);
            }
        }

        return new ParseResult(errors, warnings);
    }

    private static CompilationError tryMatch(String line, Pattern pattern, boolean hasColumn,
                                             String severity, Path projectDir) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        String filePath = relativizePath(matcher.group(1), projectDir);
        int lineNum = Integer.parseInt(matcher.group(2));
        Integer column;
        String message;

        if (hasColumn) {
            column = Integer.parseInt(matcher.group(3));
            message = matcher.group(4);
        } else {
            column = null;
            message = matcher.group(3);
        }

        return new CompilationError(filePath, lineNum, column, message, severity);
    }

    private static String relativizePath(String absolutePath, Path projectDir) {
        try {
            Path filePath = Path.of(absolutePath);
            if (filePath.isAbsolute() && filePath.startsWith(projectDir)) {
                return projectDir.relativize(filePath).toString();
            }
        } catch (Exception e) {
            // If relativization fails, return original
        }
        return absolutePath;
    }

    /**
     * Result of parsing compilation output, containing separate error and warning lists.
     */
    public record ParseResult(List<CompilationError> errors, List<CompilationError> warnings) {
    }
}
