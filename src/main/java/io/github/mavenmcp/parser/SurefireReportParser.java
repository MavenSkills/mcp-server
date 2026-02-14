package io.github.mavenmcp.parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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

    private SurefireReportParser() {
    }

    /**
     * Parse Surefire XML reports for the given project.
     *
     * @param projectDir      project root directory
     * @param stackTraceLines max lines per stack trace (default 50)
     * @return parsed test results, or empty if no reports found
     */
    public static Optional<SurefireResult> parse(Path projectDir, int stackTraceLines) {
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            for (File xmlFile : xmlFiles) {
                try {
                    Document doc = builder.parse(xmlFile);
                    Element testsuite = doc.getDocumentElement();

                    totalTests += intAttr(testsuite, "tests");
                    totalFailures += intAttr(testsuite, "failures");
                    totalErrors += intAttr(testsuite, "errors");
                    totalSkipped += intAttr(testsuite, "skipped");

                    // Extract failures
                    extractFailures(testsuite, "failure", stackTraceLines, failures);
                    // Extract errors (same structure, different element name)
                    extractFailures(testsuite, "error", stackTraceLines, failures);

                } catch (Exception e) {
                    log.warn("Failed to parse Surefire report {}: {}", xmlFile.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize XML parser", e);
            return Optional.empty();
        }

        var summary = new TestSummary(totalTests, totalFailures, totalSkipped, totalErrors);
        return Optional.of(new SurefireResult(summary, failures));
    }

    private static void extractFailures(Element testsuite, String failureElementName,
                                        int stackTraceLines, List<TestFailure> results) {
        NodeList testcases = testsuite.getElementsByTagName("testcase");
        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            NodeList failureNodes = testcase.getElementsByTagName(failureElementName);
            if (failureNodes.getLength() > 0) {
                Element failure = (Element) failureNodes.item(0);
                String testClass = testcase.getAttribute("classname");
                String testMethod = testcase.getAttribute("name");
                String message = failure.getAttribute("message");
                String stackTrace = truncateStackTrace(failure.getTextContent(), stackTraceLines);

                results.add(new TestFailure(testClass, testMethod, message, stackTrace));
            }
        }
    }

    private static String truncateStackTrace(String stackTrace, int maxLines) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return null;
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= maxLines) {
            return stackTrace.strip();
        }
        return String.join("\n", Arrays.copyOf(lines, maxLines)).strip();
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
