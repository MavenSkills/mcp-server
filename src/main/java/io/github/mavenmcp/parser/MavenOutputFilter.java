package io.github.mavenmcp.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters raw Maven console output to keep only actionable lines.
 * Removes download progress, plugin banners, generic INFO noise, and blank lines.
 */
public final class MavenOutputFilter {

    private static final Pattern PLUGIN_BANNER = Pattern.compile(
            "^\\[INFO] --- .+:.+:.+ .+---$");
    private static final Pattern DOWNLOAD_LINE = Pattern.compile(
            "^(Downloading|Downloaded|Progress \\().*");

    private MavenOutputFilter() {
    }

    /**
     * Filter raw Maven stdout, keeping only actionable lines.
     *
     * @param rawOutput raw Maven console output
     * @return filtered output with only actionable lines, or null if input is null/empty
     */
    public static String filter(String rawOutput) {
        if (rawOutput == null) {
            return null;
        }

        List<String> kept = new ArrayList<>();
        boolean inFailureBlock = false;

        for (String line : rawOutput.split("\n")) {
            if (line.isBlank()) {
                continue;
            }

            if (DOWNLOAD_LINE.matcher(line).matches()) {
                continue;
            }

            if (PLUGIN_BANNER.matcher(line).matches()) {
                continue;
            }

            if (line.startsWith("[ERROR]")) {
                kept.add(line);
                inFailureBlock = true;
                continue;
            }

            if (line.startsWith("[WARNING]")) {
                kept.add(line);
                continue;
            }

            // Keep lines that contain key failure/test keywords
            if (containsActionableKeyword(line)) {
                kept.add(line);
                inFailureBlock = true;
                continue;
            }

            // In a failure block, keep non-INFO continuation lines
            // (e.g. indented error details after BUILD FAILURE)
            if (inFailureBlock && !line.startsWith("[INFO]")) {
                kept.add(line);
                continue;
            }

            // INFO lines: only keep if they contain build-critical keywords
            if (line.startsWith("[INFO]")) {
                if (line.contains("BUILD FAILURE") || line.contains("BUILD SUCCESS")
                        || line.contains("Reactor Summary")) {
                    kept.add(line);
                    if (line.contains("BUILD FAILURE")) {
                        inFailureBlock = true;
                    }
                }
                // Otherwise drop the INFO line
                continue;
            }

            // Non-prefixed lines in failure context
            if (inFailureBlock) {
                kept.add(line);
            }
        }

        return kept.isEmpty() ? null : String.join("\n", kept);
    }

    private static boolean containsActionableKeyword(String line) {
        return line.contains("BUILD FAILURE")
                || line.contains("Reactor Summary")
                || line.contains("Failed to execute goal")
                || line.contains("Tests run:")
                || line.contains("Failed tests:")
                || line.contains("Tests in error:");
    }
}
