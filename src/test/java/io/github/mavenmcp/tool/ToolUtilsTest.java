package io.github.mavenmcp.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolUtilsTest {

    @Test
    void tailLines_nullInput_returnsNull() {
        assertNull(ToolUtils.tailLines(null, 50));
    }

    @Test
    void tailLines_withinLimit_returnsUnchanged() {
        String input = "line1\nline2\nline3";
        assertSame(input, ToolUtils.tailLines(input, 50));
    }

    @Test
    void tailLines_exceedsLimit_returnsLastNLines() {
        String input = "line1\nline2\nline3\nline4\nline5";
        assertEquals("line4\nline5", ToolUtils.tailLines(input, 2));
    }

    @Test
    void tailLines_exactBoundary_returnsUnchanged() {
        String input = "line1\nline2\nline3";
        assertSame(input, ToolUtils.tailLines(input, 3));
    }

    @Test
    void tailLines_singleLine_returnsUnchanged() {
        String input = "single line";
        assertSame(input, ToolUtils.tailLines(input, 50));
    }

    @Test
    void defaultOutputTailLines_is50() {
        assertEquals(50, ToolUtils.DEFAULT_OUTPUT_TAIL_LINES);
    }
}
