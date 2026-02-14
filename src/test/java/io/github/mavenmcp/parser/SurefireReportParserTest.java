package io.github.mavenmcp.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.parser.SurefireReportParser.SurefireResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SurefireReportParserTest {

    @TempDir
    Path tempDir;
    Path reportsDir;

    @BeforeEach
    void setUp() throws IOException {
        reportsDir = tempDir.resolve("target/surefire-reports");
        Files.createDirectories(reportsDir);
    }

    @Test
    void shouldParseAllPassingTests() throws IOException {
        copyFixture("TEST-com.example.PassingTest.xml");

        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isPresent();
        SurefireResult sr = result.get();
        assertThat(sr.summary().testsRun()).isEqualTo(3);
        assertThat(sr.summary().testsFailed()).isEqualTo(0);
        assertThat(sr.summary().testsErrored()).isEqualTo(0);
        assertThat(sr.summary().testsSkipped()).isEqualTo(0);
        assertThat(sr.failures()).isEmpty();
    }

    @Test
    void shouldParseTestFailures() throws IOException {
        copyFixture("TEST-com.example.FailingTest.xml");

        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isPresent();
        SurefireResult sr = result.get();
        assertThat(sr.summary().testsRun()).isEqualTo(4);
        assertThat(sr.summary().testsFailed()).isEqualTo(2);
        assertThat(sr.failures()).hasSize(2);

        TestFailure first = sr.failures().getFirst();
        assertThat(first.testClass()).isEqualTo("com.example.FailingTest");
        assertThat(first.testMethod()).isEqualTo("shouldReturnUser");
        assertThat(first.message()).contains("expected");
        assertThat(first.stackTrace()).isNotNull();
    }

    @Test
    void shouldParseTestErrors() throws IOException {
        copyFixture("TEST-com.example.ErrorTest.xml");

        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isPresent();
        SurefireResult sr = result.get();
        assertThat(sr.summary().testsRun()).isEqualTo(2);
        assertThat(sr.summary().testsErrored()).isEqualTo(1);
        assertThat(sr.failures()).hasSize(1);

        TestFailure error = sr.failures().getFirst();
        assertThat(error.testClass()).isEqualTo("com.example.ErrorTest");
        assertThat(error.testMethod()).isEqualTo("shouldNotThrow");
        assertThat(error.message()).contains("Connection refused");
    }

    @Test
    void shouldParseSkippedTests() throws IOException {
        copyFixture("TEST-com.example.SkippedTest.xml");

        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isPresent();
        SurefireResult sr = result.get();
        assertThat(sr.summary().testsRun()).isEqualTo(2);
        assertThat(sr.summary().testsSkipped()).isEqualTo(1);
        assertThat(sr.failures()).isEmpty();
    }

    @Test
    void shouldAggregateMultipleReports() throws IOException {
        copyFixture("TEST-com.example.PassingTest.xml");
        copyFixture("TEST-com.example.FailingTest.xml");
        copyFixture("TEST-com.example.ErrorTest.xml");

        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isPresent();
        SurefireResult sr = result.get();
        // 3 (passing) + 4 (failing) + 2 (error) = 9
        assertThat(sr.summary().testsRun()).isEqualTo(9);
        // 2 failures + 1 error in test failure records
        assertThat(sr.failures()).hasSize(3);
    }

    @Test
    void shouldReturnEmptyWhenNoReportsDirectory() {
        Path noReportsDir = tempDir.resolve("nonexistent");

        var result = SurefireReportParser.parse(noReportsDir);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenEmptyReportsDirectory() {
        // reportsDir exists but has no XML files
        var result = SurefireReportParser.parse(tempDir);

        assertThat(result).isEmpty();
    }

    @Nested
    class LogExtraction {

        @Test
        void shouldExtractSystemOutFromFailingTest() throws IOException {
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            var result = SurefireReportParser.parse(tempDir);

            assertThat(result).isPresent();
            var failures = result.get().failures();
            assertThat(failures).hasSize(2);

            // First failure has both stdout and stderr
            TestFailure first = failures.getFirst();
            assertThat(first.testOutput()).isNotNull();
            assertThat(first.testOutput()).contains("DEBUG: Initializing connection pool");
            assertThat(first.testOutput()).contains("[STDERR]");
            assertThat(first.testOutput()).contains("STDERR: Connection refused");
        }

        @Test
        void shouldExtractSystemOutOnlyFromFailingTest() throws IOException {
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            var result = SurefireReportParser.parse(tempDir);

            assertThat(result).isPresent();
            var failures = result.get().failures();

            // Second failure has only stdout
            TestFailure second = failures.get(1);
            assertThat(second.testOutput()).isNotNull();
            assertThat(second.testOutput()).contains("Processing batch");
            assertThat(second.testOutput()).doesNotContain("[STDERR]");
        }

        @Test
        void shouldReturnNullOutputWhenNoSystemElements() throws IOException {
            copyFixture("TEST-com.example.FailingTest.xml");

            var result = SurefireReportParser.parse(tempDir);

            assertThat(result).isPresent();
            for (TestFailure failure : result.get().failures()) {
                assertThat(failure.testOutput()).isNull();
            }
        }

        @Test
        void shouldSkipExtractionWhenIncludeTestLogsFalse() throws IOException {
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            var result = SurefireReportParser.parse(tempDir, false, 2000);

            assertThat(result).isPresent();
            for (TestFailure failure : result.get().failures()) {
                assertThat(failure.testOutput()).isNull();
            }
        }

        @Test
        void shouldTruncatePerTestOutput() throws IOException {
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            // Very small per-test limit
            var result = SurefireReportParser.parse(tempDir, true, 50);

            assertThat(result).isPresent();
            for (TestFailure failure : result.get().failures()) {
                if (failure.testOutput() != null) {
                    // The truncation marker is prepended, so actual output portion is <= 50 chars
                    assertThat(failure.testOutput()).contains("chars truncated");
                }
            }
        }

        @Test
        void shouldApplyTotalOutputLimit() throws IOException {
            // Create a fixture where each test has 4000 chars of output
            // With per-test limit of 4000, no per-test truncation
            // Total: 3 × 4000 = 12000 > 10000 total limit → 3rd test should be null
            String bigOutput = "X".repeat(4000);
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="com.example.BigTest" time="1.0" tests="3" errors="0" skipped="0" failures="3">
                      <testcase name="test1" classname="com.example.BigTest" time="0.1">
                        <failure message="fail1" type="java.lang.AssertionError">java.lang.AssertionError: fail1</failure>
                        <system-out>%s</system-out>
                      </testcase>
                      <testcase name="test2" classname="com.example.BigTest" time="0.1">
                        <failure message="fail2" type="java.lang.AssertionError">java.lang.AssertionError: fail2</failure>
                        <system-out>%s</system-out>
                      </testcase>
                      <testcase name="test3" classname="com.example.BigTest" time="0.1">
                        <failure message="fail3" type="java.lang.AssertionError">java.lang.AssertionError: fail3</failure>
                        <system-out>%s</system-out>
                      </testcase>
                    </testsuite>
                    """.formatted(bigOutput, bigOutput, bigOutput);

            Files.writeString(reportsDir.resolve("TEST-com.example.BigTest.xml"), xml);

            // Per-test limit large enough to not truncate, total limit of 10000
            var result = SurefireReportParser.parse(tempDir, true, 5000);

            assertThat(result).isPresent();
            var failures = result.get().failures();
            assertThat(failures).hasSize(3);

            // First two should have output (cumulative: 8000)
            assertThat(failures.get(0).testOutput()).isNotNull();
            assertThat(failures.get(1).testOutput()).isNotNull();
            // Third would push to 12000 > 10000 → null
            assertThat(failures.get(2).testOutput()).isNull();
        }

        @Test
        void shouldHandleTestWithOnlyStderr() throws IOException {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="com.example.StderrTest" time="1.0" tests="1" errors="0" skipped="0" failures="1">
                      <testcase name="test1" classname="com.example.StderrTest" time="0.1">
                        <failure message="fail" type="java.lang.AssertionError">java.lang.AssertionError: fail</failure>
                        <system-err>Error output only</system-err>
                      </testcase>
                    </testsuite>
                    """;

            Files.writeString(reportsDir.resolve("TEST-com.example.StderrTest.xml"), xml);

            var result = SurefireReportParser.parse(tempDir);

            assertThat(result).isPresent();
            var failure = result.get().failures().getFirst();
            assertThat(failure.testOutput()).isNotNull();
            assertThat(failure.testOutput()).startsWith("[STDERR]");
            assertThat(failure.testOutput()).contains("Error output only");
        }
    }

    @Test
    void shouldTruncateTestOutputFromBeginningKeepingTail() {
        String output = "AAAA\nBBBB\nCCCC\nDDDD";
        String result = SurefireReportParser.truncateTestOutput(output, 10);

        assertThat(result).contains("chars truncated");
        assertThat(result).endsWith(output.substring(output.length() - 10));
    }

    private void copyFixture(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("surefire-reports/" + filename)) {
            if (is == null) throw new RuntimeException("Fixture not found: " + filename);
            Files.copy(is, reportsDir.resolve(filename));
        }
    }
}
