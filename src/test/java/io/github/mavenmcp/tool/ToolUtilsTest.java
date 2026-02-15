package io.github.mavenmcp.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolUtilsTest {

    @Test
    void tailLines_nullInput_returnsNull() {
        assertThat(ToolUtils.tailLines(null, 50)).isNull();
    }

    @Test
    void tailLines_withinLimit_returnsUnchanged() {
        String input = "line1\nline2\nline3";
        assertThat(ToolUtils.tailLines(input, 50)).isSameAs(input);
    }

    @Test
    void tailLines_exceedsLimit_returnsLastNLines() {
        String input = "line1\nline2\nline3\nline4\nline5";
        assertThat(ToolUtils.tailLines(input, 2)).isEqualTo("line4\nline5");
    }

    @Test
    void tailLines_exactBoundary_returnsUnchanged() {
        String input = "line1\nline2\nline3";
        assertThat(ToolUtils.tailLines(input, 3)).isSameAs(input);
    }

    @Test
    void tailLines_singleLine_returnsUnchanged() {
        String input = "single line";
        assertThat(ToolUtils.tailLines(input, 50)).isSameAs(input);
    }

    @Test
    void defaultOutputTailLines_is50() {
        assertThat(ToolUtils.DEFAULT_OUTPUT_TAIL_LINES).isEqualTo(50);
    }
}
