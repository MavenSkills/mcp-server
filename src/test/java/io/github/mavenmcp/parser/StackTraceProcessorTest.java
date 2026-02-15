package io.github.mavenmcp.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceProcessorTest {

    private static final String APP_PACKAGE = "io.github.mavenmcp";

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(StackTraceProcessor.process(null, APP_PACKAGE, 50)).isNull();
    }

    @Test
    void shouldReturnNullForBlankInput() {
        assertThat(StackTraceProcessor.process("   ", APP_PACKAGE, 50)).isNull();
    }

    @Test
    void shouldProcessSimpleTraceWithNoChain() {
        String trace = """
                java.lang.AssertionError: expected:<200> but was:<404>
                \tat io.github.mavenmcp.tool.TestTool.execute(TestTool.java:42)
                \tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:150)
                \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:138)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 50);

        assertThat(result).isNotNull();
        assertThat(result).contains("java.lang.AssertionError");
        assertThat(result).contains("io.github.mavenmcp.tool.TestTool.execute");
        assertThat(result).contains("framework frames omitted");
        // Framework frames should be collapsed
        assertThat(result).doesNotContain("org.junit.jupiter");
    }

    @Test
    void shouldPreserveNestedCausedByChain() {
        String trace = """
                org.springframework.web.client.RestClientException: Request failed
                \tat org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:744)
                \tat io.github.mavenmcp.service.ApiClient.call(ApiClient.java:23)
                Caused by: java.net.ConnectException: Connection refused
                \tat java.net.Socket.connect(Socket.java:591)
                \tat io.github.mavenmcp.service.ApiClient.openConnection(ApiClient.java:45)
                Caused by: java.io.IOException: Network unreachable
                \tat java.net.PlainSocketImpl.socketConnect(PlainSocketImpl.java:101)
                \tat io.github.mavenmcp.net.SocketFactory.create(SocketFactory.java:12)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 50);

        assertThat(result).isNotNull();
        // All three exception headers preserved
        assertThat(result).contains("org.springframework.web.client.RestClientException: Request failed");
        assertThat(result).contains("Caused by: java.net.ConnectException: Connection refused");
        assertThat(result).contains("Caused by: java.io.IOException: Network unreachable");
        // Application frames preserved
        assertThat(result).contains("io.github.mavenmcp.service.ApiClient.call");
        assertThat(result).contains("io.github.mavenmcp.net.SocketFactory.create");
        // Root cause application frame preserved
        assertThat(result).contains("SocketFactory.create");
    }

    @Test
    void shouldKeepAllFramesWhenNoAppPackage() {
        String trace = """
                java.lang.RuntimeException: oops
                \tat org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:744)
                \tat com.example.Foo.bar(Foo.java:10)
                \tat org.junit.jupiter.api.Test.run(Test.java:55)""";

        String resultNull = StackTraceProcessor.process(trace, null, 50);
        String resultEmpty = StackTraceProcessor.process(trace, "", 50);

        // All frames should be preserved (no filtering)
        for (String result : new String[]{resultNull, resultEmpty}) {
            assertThat(result).contains("org.springframework.web.client.RestTemplate");
            assertThat(result).contains("com.example.Foo.bar");
            assertThat(result).contains("org.junit.jupiter.api.Test.run");
            assertThat(result).doesNotContain("framework frames omitted");
        }
    }

    @Test
    void shouldApplyHardCapTruncation() {
        String trace = """
                java.lang.RuntimeException: top
                \tat io.github.mavenmcp.A.a(A.java:1)
                \tat org.framework.X.x(X.java:1)
                \tat org.framework.Y.y(Y.java:1)
                Caused by: java.lang.IllegalStateException: middle
                \tat io.github.mavenmcp.B.b(B.java:1)
                \tat org.framework.Z.z(Z.java:1)
                Caused by: java.io.IOException: root
                \tat io.github.mavenmcp.C.c(C.java:1)
                \tat org.framework.W.w(W.java:1)""";

        // Very tight cap
        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 5);

        assertThat(result).isNotNull();
        long lineCount = result.lines().count();
        assertThat(lineCount).isLessThanOrEqualTo(5);
        // Root cause should still be preserved
        assertThat(result).contains("java.lang.RuntimeException: top");
    }

    @Test
    void shouldNotCapWhenZeroStackTraceLines() {
        String trace = """
                java.lang.RuntimeException: oops
                \tat io.github.mavenmcp.A.a(A.java:1)
                \tat org.framework.X.x(X.java:1)
                \tat org.framework.Y.y(Y.java:1)
                \tat org.framework.Z.z(Z.java:1)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        assertThat(result).isNotNull();
        // All content should be present (no truncation)
        assertThat(result).contains("java.lang.RuntimeException: oops");
        assertThat(result).contains("io.github.mavenmcp.A.a");
    }

    @Test
    void shouldCollapseConsecutiveFrameworkFrames() {
        String trace = """
                java.lang.RuntimeException: test
                \tat org.framework.A.a(A.java:1)
                \tat org.framework.B.b(B.java:2)
                \tat org.framework.C.c(C.java:3)
                \tat io.github.mavenmcp.MyClass.myMethod(MyClass.java:10)
                \tat org.other.D.d(D.java:4)
                \tat org.other.E.e(E.java:5)
                \tat io.github.mavenmcp.MyClass.anotherMethod(MyClass.java:20)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 50);

        assertThat(result).contains("... 3 framework frames omitted");
        assertThat(result).contains("io.github.mavenmcp.MyClass.myMethod");
        assertThat(result).contains("... 2 framework frames omitted");
        assertThat(result).contains("io.github.mavenmcp.MyClass.anotherMethod");
    }

    @Test
    void shouldHandleDeepCausedByChainPreservingAllHeaders() {
        String trace = """
                java.lang.RuntimeException: level 0
                \tat org.framework.X.x(X.java:1)
                Caused by: java.lang.IllegalStateException: level 1
                \tat org.framework.Y.y(Y.java:1)
                Caused by: java.lang.IllegalArgumentException: level 2
                \tat org.framework.Z.z(Z.java:1)
                Caused by: java.io.IOException: level 3
                \tat org.framework.W.w(W.java:1)
                Caused by: java.net.ConnectException: level 4
                \tat io.github.mavenmcp.Net.connect(Net.java:5)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 50);

        assertThat(result).contains("java.lang.RuntimeException: level 0");
        assertThat(result).contains("Caused by: java.lang.IllegalStateException: level 1");
        assertThat(result).contains("Caused by: java.lang.IllegalArgumentException: level 2");
        assertThat(result).contains("Caused by: java.io.IOException: level 3");
        assertThat(result).contains("Caused by: java.net.ConnectException: level 4");
        assertThat(result).contains("io.github.mavenmcp.Net.connect");
    }

    @Test
    void shouldClassifyApplicationFramesByPackagePrefix() {
        assertThat(StackTraceProcessor.isApplicationFrame(
                "\tat io.github.mavenmcp.tool.TestTool.execute(TestTool.java:42)", APP_PACKAGE))
                .isTrue();
        assertThat(StackTraceProcessor.isApplicationFrame(
                "\tat org.springframework.test.TestRunner.run(TestRunner.java:10)", APP_PACKAGE))
                .isFalse();
        assertThat(StackTraceProcessor.isApplicationFrame(
                "\t... 42 more", APP_PACKAGE))
                .isFalse();
        // Null package → everything is application
        assertThat(StackTraceProcessor.isApplicationFrame(
                "\tat org.springframework.test.TestRunner.run(TestRunner.java:10)", null))
                .isTrue();
    }

    @Test
    void shouldRecognizeStructuralLines() {
        // Suppressed: with tab prefix → structural
        assertThat(StackTraceProcessor.isStructuralLine(
                "\tSuppressed: java.lang.Exception: cleanup failed"))
                .isTrue();
        // Indented Caused by: with tab prefix → structural
        assertThat(StackTraceProcessor.isStructuralLine(
                "\t\tCaused by: java.lang.RuntimeException: inner"))
                .isTrue();
        // JDK elision marker "... N more" → structural
        assertThat(StackTraceProcessor.isStructuralLine(
                "\t\t... 3 more"))
                .isTrue();
        // Top-level Caused by: (no whitespace) → NOT structural
        assertThat(StackTraceProcessor.isStructuralLine(
                "Caused by: java.lang.Exception: root"))
                .isFalse();
        // Regular at frame → NOT structural
        assertThat(StackTraceProcessor.isStructuralLine(
                "\tat org.framework.Runner.execute(Runner.java:50)"))
                .isFalse();
        // Empty line → NOT structural
        assertThat(StackTraceProcessor.isStructuralLine(""))
                .isFalse();
    }

    @Test
    void shouldPreserveSuppressedHeaderBetweenFrameworkFrames() {
        String trace = """
                java.lang.Exception: main
                \tat org.framework.A.a(A.java:1)
                \tat org.framework.B.b(B.java:2)
                \tSuppressed: java.lang.Exception: cleanup failed
                \t\tat org.framework.C.c(C.java:3)
                \t\tat org.framework.D.d(D.java:4)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        assertThat(result).contains("\tSuppressed: java.lang.Exception: cleanup failed");
        // Two separate groups of 2 framework frames collapsed independently
        long collapseCount = result.lines()
                .filter(l -> l.contains("... 2 framework frames omitted"))
                .count();
        assertThat(collapseCount).isEqualTo(2);
    }

    @Test
    void shouldPreserveIndentedCausedByInsideSuppressedBlock() {
        String trace = """
                java.lang.Exception: main
                \tat io.github.mavenmcp.Main.run(Main.java:10)
                \tSuppressed: java.lang.Exception: sup
                \t\tat org.framework.Pool.release(Pool.java:80)
                \t\tCaused by: java.lang.RuntimeException: inner
                \t\t\tat org.framework.IO.sync(IO.java:100)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        assertThat(result).contains("\t\tCaused by: java.lang.RuntimeException: inner");
    }

    @Test
    void shouldCollapseFrameworkFramesInsideSuppressedBlock() {
        String trace = """
                java.lang.Exception: main
                \tat io.github.mavenmcp.Main.run(Main.java:10)
                \tSuppressed: java.lang.Exception: sup
                \t\tat io.github.mavenmcp.Resource.close(Resource.java:20)
                \t\tat org.framework.Pool.release(Pool.java:80)
                \t\tat org.framework.Pool.cleanup(Pool.java:85)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        assertThat(result).contains("\tSuppressed: java.lang.Exception: sup");
        assertThat(result).contains("io.github.mavenmcp.Resource.close");
        assertThat(result).contains("... 2 framework frames omitted");
    }

    @Test
    void shouldPreserveAllHeadersInEndToEndSuppressedTrace() {
        String trace = """
                java.lang.Exception: main
                \tat io.github.mavenmcp.Main.run(Main.java:10)
                \tat org.framework.Runner.execute(Runner.java:50)
                \tSuppressed: java.lang.Exception: cleanup failed
                \t\tat io.github.mavenmcp.Resource.close(Resource.java:20)
                \t\tat org.framework.Pool.release(Pool.java:80)
                \t\tCaused by: java.lang.RuntimeException: IO error
                \t\t\tat io.github.mavenmcp.Resource.flush(Resource.java:30)
                \t\t\tat org.framework.IO.sync(IO.java:100)
                Caused by: java.lang.IllegalStateException: root cause
                \tat io.github.mavenmcp.Main.init(Main.java:5)
                \tat org.framework.Bootstrap.start(Bootstrap.java:200)""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        // All 4 exception headers present
        assertThat(result).contains("java.lang.Exception: main");
        assertThat(result).contains("\tSuppressed: java.lang.Exception: cleanup failed");
        assertThat(result).contains("\t\tCaused by: java.lang.RuntimeException: IO error");
        assertThat(result).contains("Caused by: java.lang.IllegalStateException: root cause");

        // App frames preserved
        assertThat(result).contains("io.github.mavenmcp.Main.run");
        assertThat(result).contains("io.github.mavenmcp.Resource.close");
        assertThat(result).contains("io.github.mavenmcp.Resource.flush");
        assertThat(result).contains("io.github.mavenmcp.Main.init");

        // Framework frames collapsed (not present verbatim)
        assertThat(result).doesNotContain("org.framework.Runner.execute");
        assertThat(result).doesNotContain("org.framework.Pool.release");
        assertThat(result).doesNotContain("org.framework.IO.sync");
        assertThat(result).doesNotContain("org.framework.Bootstrap.start");
    }

    @Test
    void shouldPassSuppressedBlockUnchangedWhenNoAppPackage() {
        String trace = """
                java.lang.Exception: main
                \tat com.example.Main.run(Main.java:10)
                \tSuppressed: java.lang.Exception: sup
                \t\tat com.example.Resource.close(Resource.java:20)
                \t\tCaused by: java.lang.RuntimeException: inner
                \t\t\tat com.example.Resource.flush(Resource.java:30)
                Caused by: java.lang.Exception: root
                \tat com.example.Main.init(Main.java:5)""";

        String result = StackTraceProcessor.process(trace, null, 0);

        // All lines preserved verbatim — no collapsing
        assertThat(result).contains("\tat com.example.Main.run(Main.java:10)");
        assertThat(result).contains("\tSuppressed: java.lang.Exception: sup");
        assertThat(result).contains("\t\tat com.example.Resource.close(Resource.java:20)");
        assertThat(result).contains("\t\tCaused by: java.lang.RuntimeException: inner");
        assertThat(result).contains("\t\t\tat com.example.Resource.flush(Resource.java:30)");
        assertThat(result).contains("Caused by: java.lang.Exception: root");
        assertThat(result).doesNotContain("framework frames omitted");
    }

    @Test
    void shouldPreserveJdkElisionMarkerInsideSuppressedBlock() {
        String trace = """
                java.lang.Exception: main
                \tat io.github.mavenmcp.Main.run(Main.java:10)
                \tSuppressed: java.io.IOException: close failed
                \t\tat io.github.mavenmcp.Resource.close(Resource.java:20)
                \t\t... 3 more""";

        String result = StackTraceProcessor.process(trace, APP_PACKAGE, 0);

        assertThat(result).contains("\tSuppressed: java.io.IOException: close failed");
        assertThat(result).contains("io.github.mavenmcp.Resource.close");
        // JDK "... 3 more" marker preserved verbatim, not replaced by framework collapse
        assertThat(result).contains("... 3 more");
        assertThat(result).doesNotContain("framework frames omitted");
    }
}
