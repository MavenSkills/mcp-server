package io.github.mavenmcp.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.mavenmcp.model.TestFailure;
import org.junit.jupiter.api.BeforeEach;
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

        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNotNull();
        assertThat(result.summary().testsRun()).isEqualTo(3);
        assertThat(result.summary().testsFailed()).isEqualTo(0);
        assertThat(result.summary().testsErrored()).isEqualTo(0);
        assertThat(result.summary().testsSkipped()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void shouldParseTestFailures() throws IOException {
        copyFixture("TEST-com.example.FailingTest.xml");

        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNotNull();
        assertThat(result.summary().testsRun()).isEqualTo(4);
        assertThat(result.summary().testsFailed()).isEqualTo(2);
        assertThat(result.failures()).hasSize(2);

        TestFailure first = result.failures().getFirst();
        assertThat(first.testClass()).isEqualTo("com.example.FailingTest");
        assertThat(first.testMethod()).isEqualTo("shouldReturnUser");
        assertThat(first.message()).contains("expected");
        assertThat(first.stackTrace()).isNotNull();
    }

    @Test
    void shouldParseTestErrors() throws IOException {
        copyFixture("TEST-com.example.ErrorTest.xml");

        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNotNull();
        assertThat(result.summary().testsRun()).isEqualTo(2);
        assertThat(result.summary().testsErrored()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);

        TestFailure error = result.failures().getFirst();
        assertThat(error.testClass()).isEqualTo("com.example.ErrorTest");
        assertThat(error.testMethod()).isEqualTo("shouldNotThrow");
        assertThat(error.message()).contains("Connection refused");
    }

    @Test
    void shouldParseSkippedTests() throws IOException {
        copyFixture("TEST-com.example.SkippedTest.xml");

        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNotNull();
        assertThat(result.summary().testsRun()).isEqualTo(2);
        assertThat(result.summary().testsSkipped()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void shouldAggregateMultipleReports() throws IOException {
        copyFixture("TEST-com.example.PassingTest.xml");
        copyFixture("TEST-com.example.FailingTest.xml");
        copyFixture("TEST-com.example.ErrorTest.xml");

        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNotNull();
        // 3 (passing) + 4 (failing) + 2 (error) = 9
        assertThat(result.summary().testsRun()).isEqualTo(9);
        // 2 failures + 1 error in test failure records
        assertThat(result.failures()).hasSize(3);
    }

    @Test
    void shouldReturnNullWhenNoReportsDirectory() {
        Path noReportsDir = tempDir.resolve("nonexistent");

        var result = SurefireReportParser.parse(noReportsDir, 50);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenEmptyReportsDirectory() {
        // reportsDir exists but has no XML files
        var result = SurefireReportParser.parse(tempDir, 50);

        assertThat(result).isNull();
    }

    @Test
    void shouldTruncateStackTrace() throws IOException {
        copyFixture("TEST-com.example.FailingTest.xml");

        // Truncate to 2 lines
        var result = SurefireReportParser.parse(tempDir, 2);

        assertThat(result.failures()).isNotEmpty();
        for (TestFailure failure : result.failures()) {
            if (failure.stackTrace() != null) {
                long lineCount = failure.stackTrace().lines().count();
                assertThat(lineCount).isLessThanOrEqualTo(2);
            }
        }
    }

    private void copyFixture(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("surefire-reports/" + filename)) {
            if (is == null) throw new RuntimeException("Fixture not found: " + filename);
            Files.copy(is, reportsDir.resolve(filename));
        }
    }
}
