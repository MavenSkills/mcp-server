package io.github.mavenmcp.tool;

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
}
