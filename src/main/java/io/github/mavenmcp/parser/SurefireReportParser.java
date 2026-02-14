package io.github.mavenmcp.parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;

import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.model.TestSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses Maven Surefire XML test reports from target/surefire-reports/.
 * Uses JAXP DOM parser (built into JDK).
 */
public final class SurefireReportParser {

    private static final Logger log = LoggerFactory.getLogger(SurefireReportParser.class);
    private static final String REPORTS_DIR = "target/surefire-reports";
    public static final int DEFAULT_STACK_TRACE_LINES = 50;
    public static final int DEFAULT_PER_TEST_OUTPUT_LIMIT = 2000;
    public static final int DEFAULT_TOTAL_OUTPUT_LIMIT = 10000;

    private SurefireReportParser() {
    }

    /**
     * Parse Surefire XML reports for the given project.
     *
     * @param projectDir project root directory
     * @return parsed test results, or empty if no reports found
     */
    public static Optional<SurefireResult> parse(Path projectDir) {
        return parse(projectDir, true, DEFAULT_PER_TEST_OUTPUT_LIMIT);
    }

    /**
     * Parse Surefire XML reports with log extraction options.
     *
     * @param projectDir      project root directory
     * @param includeTestLogs whether to extract system-out/system-err from test cases
     * @param testOutputLimit per-test character limit for extracted output (default 2000)
     * @return parsed test results, or empty if no reports found
     */
    public static Optional<SurefireResult> parse(Path projectDir,
                                                  boolean includeTestLogs, int testOutputLimit) {
        Path reportsDir = projectDir.resolve(REPORTS_DIR);

        if (!Files.isDirectory(reportsDir)) {
            log.debug("Surefire reports directory not found: {}", reportsDir);
            return Optional.empty();
        }

        File[] xmlFiles = reportsDir.toFile().listFiles(
                (dir, name) -> name.startsWith("TEST-") && name.endsWith(".xml"));

        if (xmlFiles == null || xmlFiles.length == 0) {
            log.debug("No TEST-*.xml files found in {}", reportsDir);
            return Optional.empty();
        }

        int totalTests = 0, totalFailures = 0, totalErrors = 0, totalSkipped = 0;
        List<TestFailure> failures = new ArrayList<>();

        try {
            DocumentBuilder builder = XmlUtils.newSecureDocumentBuilder();

            for (File xmlFile : xmlFiles) {
                try {
                    Document doc = builder.parse(xmlFile);
                    Element testsuite = doc.getDocumentElement();

                    totalTests += intAttr(testsuite, "tests");
                    totalFailures += intAttr(testsuite, "failures");
                    totalErrors += intAttr(testsuite, "errors");
                    totalSkipped += intAttr(testsuite, "skipped");

                    // Extract failures
                    extractFailures(testsuite, "failure", includeTestLogs, testOutputLimit, failures);
                    // Extract errors (same structure, different element name)
                    extractFailures(testsuite, "error", includeTestLogs, testOutputLimit, failures);

                } catch (Exception e) {
                    log.warn("Failed to parse Surefire report {}: {}", xmlFile.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize XML parser", e);
            return Optional.empty();
        }

        // Apply total output limit across all failures
        applyTotalOutputLimit(failures);

        var summary = new TestSummary(totalTests, totalFailures, totalSkipped, totalErrors);
        return Optional.of(new SurefireResult(summary, failures));
    }

    private static void extractFailures(Element testsuite, String failureElementName,
                                        boolean includeTestLogs, int testOutputLimit,
                                        List<TestFailure> results) {
        NodeList testcases = testsuite.getElementsByTagName("testcase");
        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            NodeList failureNodes = testcase.getElementsByTagName(failureElementName);
            if (failureNodes.getLength() > 0) {
                Element failure = (Element) failureNodes.item(0);
                String testClass = testcase.getAttribute("classname");
                String testMethod = testcase.getAttribute("name");
                String message = failure.getAttribute("message");
                // Raw stack trace — smart truncation is applied by the caller (StackTraceProcessor)
                String rawTrace = failure.getTextContent();
                String stackTrace = (rawTrace == null || rawTrace.isBlank()) ? null : rawTrace.strip();

                String testOutput = null;
                if (includeTestLogs) {
                    testOutput = extractTestOutput(testcase, testOutputLimit);
                }

                results.add(new TestFailure(testClass, testMethod, message, stackTrace, testOutput));
            }
        }
    }

    /**
     * Extract combined stdout/stderr from a testcase element.
     */
    private static String extractTestOutput(Element testcase, int perTestLimit) {
        String stdout = getChildElementText(testcase, "system-out");
        String stderr = getChildElementText(testcase, "system-err");

        if (stdout == null && stderr == null) {
            return null;
        }

        StringBuilder combined = new StringBuilder();
        if (stdout != null) {
            combined.append(stdout);
        }
        if (stderr != null) {
            if (combined.length() > 0) {
                combined.append("\n[STDERR]\n");
            } else {
                combined.append("[STDERR]\n");
            }
            combined.append(stderr);
        }

        String result = combined.toString();
        return truncateTestOutput(result, perTestLimit);
    }

    /**
     * Get text content of a direct child element by tag name, or null if not present.
     */
    private static String getChildElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.isBlank()) {
                return text.strip();
            }
        }
        return null;
    }

    /**
     * Truncate test output from the beginning, keeping the tail.
     */
    static String truncateTestOutput(String output, int maxChars) {
        if (output == null || output.length() <= maxChars) {
            return output;
        }
        int truncated = output.length() - maxChars;
        return "... (" + truncated + " chars truncated)\n" + output.substring(output.length() - maxChars);
    }

    /**
     * Apply total character limit across all test outputs (default 10000).
     * When the limit is reached, remaining tests have testOutput set to null.
     */
    private static void applyTotalOutputLimit(List<TestFailure> failures) {
        int totalChars = 0;
        for (int i = 0; i < failures.size(); i++) {
            TestFailure f = failures.get(i);
            if (f.testOutput() != null) {
                totalChars += f.testOutput().length();
                if (totalChars > DEFAULT_TOTAL_OUTPUT_LIMIT) {
                    // Null out this and remaining testOutputs
                    for (int j = i; j < failures.size(); j++) {
                        TestFailure orig = failures.get(j);
                        if (orig.testOutput() != null) {
                            failures.set(j, orig.withTestOutput(null));
                        }
                    }
                    break;
                }
            }
        }
    }

    private static int intAttr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Result of parsing Surefire reports.
     */
    public record SurefireResult(TestSummary summary, List<TestFailure> failures) {
    }
}
