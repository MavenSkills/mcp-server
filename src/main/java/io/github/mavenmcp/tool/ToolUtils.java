package io.github.mavenmcp.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared utilities for MCP tool handlers.
 */
final class ToolUtils {

    private ToolUtils() {
    }

    /**
     * Extract the "args" parameter from the tool call arguments.
     *
     * @param params the tool call parameters map
     * @return list of extra Maven arguments, empty if not provided
     */
    @SuppressWarnings("unchecked")
    static List<String> extractArgs(Map<String, Object> params) {
        Object argsObj = params.get("args");
        if (argsObj instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Extract a boolean parameter from tool call arguments.
     *
     * @param params       the tool call parameters map
     * @param key          the parameter key
     * @param defaultValue value to return if key is absent or not a boolean
     * @return the boolean value or default
     */
    static boolean extractBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    /**
     * Extract an integer parameter from tool call arguments.
     *
     * @param params       the tool call parameters map
     * @param key          the parameter key
     * @param defaultValue value to return if key is absent or not a number
     * @return the integer value or default
     */
    static int extractInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    /** Default number of lines to keep from raw Maven output (tail). */
    static final int DEFAULT_OUTPUT_TAIL_LINES = 50;

    /**
     * Return the last {@code maxLines} lines of the given string.
     * Returns the input unchanged if it has fewer lines, or null if input is null.
     */
    static String tailLines(String output, int maxLines) {
        if (output == null) {
            return null;
        }
        String[] lines = output.split("\n", -1);
        if (lines.length <= maxLines) {
            return output;
        }
        return String.join("\n", Arrays.copyOfRange(lines, lines.length - maxLines, lines.length));
    }
}
