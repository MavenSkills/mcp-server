package io.github.mavenmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.parser.StackTraceProcessor;
import io.github.mavenmcp.parser.SurefireReportParser;
import io.github.mavenmcp.parser.SurefireReportParser.SurefireResult;
import io.github.mavenmcp.parser.TestFailureDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test simulating a real-case scenario: 205 errored tests caused by a Redis
 * container port conflict. The original MCP server response was 5.4 MB. This test verifies
 * the full processing pipeline (SurefireReportParser -> StackTraceProcessor ->
 * TestFailureDeduplicator) handles massive Spring context failures efficiently.
 *
 * <p>Real-case characteristics:
 * <ul>
 *   <li>22 unique test classes, all failing due to the same Redis port conflict</li>
 *   <li>1 failure with a full 6-level Caused by chain</li>
 *   <li>21 failures with "ApplicationContext failure threshold exceeded" (no Caused by)</li>
 *   <li>All messages ~3800 chars (Spring WebMergedContextConfiguration.toString())</li>
 * </ul>
 */
class RealCaseRedisPortConflictTest {

    /** MAX_HEADER_LENGTH (200) + "..." (3) = 203. Matches StackTraceProcessor.MAX_HEADER_LENGTH. */
    private static final int MAX_HEADER_WITH_ELLIPSIS = 203;

    /** 22 realistic test class names from a Spring Boot auth module. */
    private static final List<String> TEST_CLASSES = List.of(
            "com.example.auth.company.CompanySetupServiceTest",
            "com.example.auth.AuthenticationTest",
            "com.example.auth.session.SessionManagementTest",
            "com.example.auth.token.RefreshTokenServiceTest",
            "com.example.auth.user.UserRegistrationTest",
            "com.example.auth.user.UserProfileServiceTest",
            "com.example.auth.role.RoleAssignmentTest",
            "com.example.auth.permission.PermissionCheckTest",
            "com.example.auth.oauth2.OAuth2LoginFlowTest",
            "com.example.auth.oauth2.OAuth2TokenExchangeTest",
            "com.example.auth.mfa.TwoFactorAuthTest",
            "com.example.auth.mfa.TotpVerificationTest",
            "com.example.auth.audit.AuditLogServiceTest",
            "com.example.auth.audit.LoginAttemptTrackerTest",
            "com.example.auth.password.PasswordResetFlowTest",
            "com.example.auth.password.PasswordPolicyTest",
            "com.example.auth.cache.RedisCacheIntegrationTest",
            "com.example.auth.cache.SessionCacheTest",
            "com.example.auth.config.SecurityConfigTest",
            "com.example.auth.config.CorsConfigurationTest",
            "com.example.auth.api.AuthControllerTest",
            "com.example.auth.api.UserControllerTest"
    );

    @TempDir
    Path tempDir;
    Path reportsDir;

    @BeforeEach
    void setUp() throws IOException {
        reportsDir = tempDir.resolve("target/surefire-reports");
        Files.createDirectories(reportsDir);
    }

    @Test
    void fullPipelineShouldHandleMassiveSpringContextFailuresEfficiently() throws Exception {
        // --- Arrange: generate 22 Surefire XML reports ---
        // First test class gets the full Caused by chain (first to hit the Redis error)
        writeXml(TEST_CLASSES.get(0), buildContextMessage(TEST_CLASSES.get(0)),
                buildFullStackTrace(TEST_CLASSES.get(0)));

        // Remaining 21 get the "threshold exceeded" variant (Spring skips context reload)
        for (int i = 1; i < TEST_CLASSES.size(); i++) {
            String testClass = TEST_CLASSES.get(i);
            writeXml(testClass, buildThresholdMessage(testClass),
                    buildThresholdTrace(testClass));
        }

        // --- Act: Step 1 - Parse ---
        var parseResult = SurefireReportParser.parse(tempDir, true, 2000);
        assertThat(parseResult).isPresent();
        SurefireResult surefireResult = parseResult.get();
        List<TestFailure> parsedFailures = surefireResult.failures();

        assertThat(parsedFailures).hasSize(22);

        // --- Act: Step 2 - Process stack traces ---
        List<TestFailure> processedFailures = new ArrayList<>();
        for (TestFailure f : parsedFailures) {
            String processedTrace = StackTraceProcessor.process(f.stackTrace(), null, 50);
            processedFailures.add(f.withStackTrace(processedTrace));
        }

        // --- Act: Step 3 - Deduplicate ---
        List<TestFailure> deduplicated = TestFailureDeduplicator.deduplicate(processedFailures);

        // --- Assert: 1. All parsed messages are truncated ---
        for (TestFailure f : parsedFailures) {
            assertThat(f.message())
                    .as("message for %s should be truncated", f.testClass())
                    .hasSizeLessThanOrEqualTo(MAX_HEADER_WITH_ELLIPSIS);
        }

        // --- Assert: 2. All exception headers in processed stack traces are truncated ---
        for (TestFailure f : processedFailures) {
            if (f.stackTrace() == null) continue;
            for (String line : f.stackTrace().split("\n")) {
                String stripped = line.strip();
                if (stripped.isEmpty()) continue;
                if (stripped.startsWith("Caused by:")
                        || (!stripped.startsWith("\t") && !stripped.startsWith("..."))) {
                    assertThat(stripped)
                            .as("header line should be truncated: %.60s...", stripped)
                            .hasSizeLessThanOrEqualTo(MAX_HEADER_WITH_ELLIPSIS);
                }
            }
        }

        // --- Assert: 3. Deduplication reduces count ---
        // The 1 failure with Caused by chain has a different root cause key than the 21 threshold ones.
        // Threshold failures have no Caused by -> fall back to message first line -> all identical
        // -> they collapse into 1. The full-chain failure has a deepest Caused by -> separate group.
        // Result: 2 groups.
        assertThat(deduplicated).hasSizeLessThan(22);
        assertThat(deduplicated).hasSize(2);

        // --- Assert: 4. Size reduction ---
        ObjectMapper mapper = new ObjectMapper();
        int rawSize = computeTotalChars(parsedFailures, mapper);
        int processedSize = computeTotalChars(processedFailures, mapper);
        int deduplicatedSize = computeTotalChars(deduplicated, mapper);

        assertThat(deduplicatedSize)
                .as("deduplicated output should be dramatically smaller than raw input")
                .isLessThan(rawSize / 2);

        // --- Diagnostic output (intentional for this integration test) ---
        System.out.println();
        System.out.println("=== Redis Port Conflict Real-Case Simulation ===");
        System.out.println();
        System.out.printf("  Test classes:           %d%n", TEST_CLASSES.size());
        System.out.printf("  Parsed failures:        %d%n", parsedFailures.size());
        System.out.printf("  After deduplication:    %d%n", deduplicated.size());
        System.out.println();
        System.out.printf("  Raw JSON size:          %,d chars%n", rawSize);
        System.out.printf("  After stack processing: %,d chars (%.0f%% reduction)%n",
                processedSize, reduction(rawSize, processedSize));
        System.out.printf("  After deduplication:    %,d chars (%.0f%% reduction from raw)%n",
                deduplicatedSize, reduction(rawSize, deduplicatedSize));
        System.out.println();
        System.out.println("=================================================");
        System.out.println();
    }

    // ---- XML generation ----

    private void writeXml(String testClass, String message, String stackTrace) throws IOException {
        String escapedMessage = escapeXmlAttribute(message);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="%s" time="0.5" tests="1" errors="1" skipped="0" failures="0">
                  <testcase name="testMethod" classname="%s" time="0.5">
                    <error message="%s" type="java.lang.IllegalStateException">%s</error>
                  </testcase>
                </testsuite>
                """.formatted(testClass, testClass, escapedMessage, stackTrace);
        Files.writeString(reportsDir.resolve("TEST-" + testClass + ".xml"), xml);
    }

    private static String escapeXmlAttribute(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ---- Test data builders ----

    /**
     * Common filler used in both context messages and threshold messages.
     * Repeated annotation entries that pad the configuration string to ~3800 chars.
     */
    private static final String ANNOTATION_FILLER =
            "@org.springframework.security.config.annotation.web.configuration"
                    + ".EnableWebSecurity(debug=false), "
                    + "@org.springframework.context.annotation.Import("
                    + "value={com.example.auth.config.SecurityConfig.class, "
                    + "com.example.auth.config.RedisConfig.class}), ";

    /**
     * Build a ~3800-char Spring ApplicationContext failure message.
     * Mirrors the real WebMergedContextConfiguration.toString() output.
     *
     * <p>The message prefix (before the testClass-specific part) is deliberately longer than
     * 200 chars so that after truncation all messages share the same dedup key. This matches
     * the real-world scenario where the Spring context annotation noise occupies the first
     * several hundred characters.
     */
    private static String buildContextMessage(String testClass) {
        // Common prefix: ~80 chars of "Failed to load..." + hash + config preamble
        // Then a block of annotation noise that pushes the testClass field past the 200-char mark.
        String prefix = "Failed to load ApplicationContext for [WebMergedContextConfiguration@4d45b457 "
                + "contextInitializerClasses = [], activeProfiles = [test], "
                + "propertySourceDescriptors = [], propertySourceProperties = ["
                + "\"org.springframework.boot.test.context.SpringBootTestContextBootstrapper=true\", "
                + "\"server.port=0\"], "
                + "testClass = " + testClass + ", locations = [], "
                + "classes = [com.example.auth.AuthApplication], "
                + "contextCustomizers = [";
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < 3760) {
            sb.append(ANNOTATION_FILLER);
        }
        sb.append("contextLoader = org.springframework.boot.test.context.SpringBootContextLoader]");
        return sb.toString();
    }

    /**
     * Build a ~3800-char "threshold exceeded" message (Spring skips reloading the context).
     * The testClass field is placed after 200+ chars of common prefix, ensuring all threshold
     * messages share the same truncated dedup key.
     */
    private static String buildThresholdMessage(String testClass) {
        String prefix = "ApplicationContext failure threshold (1) exceeded: "
                + "skipping repeated attempt to load context for [WebMergedContextConfiguration@4d45b457 "
                + "contextInitializerClasses = [], activeProfiles = [test], "
                + "propertySourceDescriptors = [], propertySourceProperties = ["
                + "\"org.springframework.boot.test.context.SpringBootTestContextBootstrapper=true\"], "
                + "testClass = " + testClass + ", locations = [], "
                + "classes = [com.example.auth.AuthApplication], "
                + "contextCustomizers = [";
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < 3760) {
            sb.append(ANNOTATION_FILLER);
        }
        sb.append("contextLoader = org.springframework.boot.test.context.SpringBootContextLoader]");
        return sb.toString();
    }

    /**
     * Build a full stack trace with 6-level Caused by chain.
     * Simulates the first test that actually triggered the Redis port conflict.
     */
    private static String buildFullStackTrace(String testClass) {
        String contextMessage = buildContextMessage(testClass);
        return "java.lang.IllegalStateException: " + contextMessage + "\n"
                + "\tat org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:180)\n"
                + "\tat org.springframework.test.context.support.DefaultTestContext.getApplicationContext(DefaultTestContext.java:130)\n"
                + "\tat org.springframework.test.context.support.DependencyInjectionTestExecutionListener.injectDependencies(DependencyInjectionTestExecutionListener.java:142)\n"
                + "\tat org.springframework.test.context.support.DependencyInjectionTestExecutionListener.prepareTestInstance(DependencyInjectionTestExecutionListener.java:98)\n"
                + "\tat org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener.prepareTestInstance(SpringBootDependencyInjectionTestExecutionListener.java:50)\n"
                + "\tat org.springframework.test.context.TestContextManager.prepareTestInstance(TestContextManager.java:260)\n"
                + "\tat org.springframework.test.context.junit.jupiter.SpringExtension.postProcessTestInstance(SpringExtension.java:163)\n"
                + "\tat org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor.invokeTestInstancePostProcessors(ClassBasedTestDescriptor.java:378)\n"
                + "\tat org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor.instantiateAndPostProcessTestInstance(ClassBasedTestDescriptor.java:290)\n"
                + "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.prepare(TestMethodTestDescriptor.java:103)\n"
                + "\tat org.junit.jupiter.engine.execution.HierarchicalTestEngine$HierarchicalTestExecutor.lambda$execute$0(HierarchicalTestEngine.java:60)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)\n"
                + "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:248)\n"
                + "Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'embeddedRedis' defined in class path resource [com/example/auth/config/RedisConfig.class]: Failed to instantiate [com.example.auth.config.EmbeddedRedisServer]: Factory method 'embeddedRedis' threw exception with message: Container startup failed for image redis:7.2-alpine\n"
                + "\tat org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod(AbstractAutowireCapableBeanFactory.java:1354)\n"
                + "\tat org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:1191)\n"
                + "\tat org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:563)\n"
                + "\tat org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:523)\n"
                + "\tat org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:250)\n"
                + "\tat org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:340)\n"
                + "\tat org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:950)\n"
                + "\tat org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:616)\n"
                + "\tat org.springframework.boot.SpringApplication.refresh(SpringApplication.java:754)\n"
                + "\tat org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:456)\n"
                + "\t... 13 more\n"
                + "Caused by: org.springframework.beans.BeanInstantiationException: Failed to instantiate [com.example.auth.config.EmbeddedRedisServer]: Factory method 'embeddedRedis' threw exception with message: Container startup failed\n"
                + "\tat org.springframework.beans.factory.support.SimpleInstantiationStrategy.instantiate(SimpleInstantiationStrategy.java:177)\n"
                + "\tat org.springframework.beans.factory.support.ConstructorResolver.instantiate(ConstructorResolver.java:651)\n"
                + "\tat org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod(AbstractAutowireCapableBeanFactory.java:1350)\n"
                + "\t... 10 more\n"
                + "Caused by: org.testcontainers.containers.ContainerLaunchException: Container startup failed for image redis:7.2-alpine\n"
                + "\tat org.testcontainers.containers.GenericContainer.doStart(GenericContainer.java:350)\n"
                + "\tat org.testcontainers.containers.GenericContainer.start(GenericContainer.java:330)\n"
                + "\tat com.example.auth.config.RedisConfig.embeddedRedis(RedisConfig.java:45)\n"
                + "\t... 12 more\n"
                + "Caused by: org.testcontainers.containers.ContainerLaunchException$RetryCountExceededException: Retry limit hit with exception\n"
                + "\tat org.testcontainers.containers.GenericContainer.tryStart(GenericContainer.java:505)\n"
                + "\tat org.testcontainers.containers.GenericContainer.doStart(GenericContainer.java:345)\n"
                + "\t... 14 more\n"
                + "Caused by: org.testcontainers.containers.ContainerLaunchException: Could not create/start container for image redis:7.2-alpine\n"
                + "\tat org.testcontainers.containers.GenericContainer.tryStart(GenericContainer.java:495)\n"
                + "\t... 15 more\n"
                + "Caused by: com.github.dockerjava.api.exception.InternalServerErrorException: Status 500: {\"message\":\"failed to set up container networking: driver failed programming external connectivity on endpoint redis-test-abc123 (deadbeef): Error starting userland proxy: listen tcp4 0.0.0.0:6379: bind: address already in use\"}\n"
                + "\tat com.github.dockerjava.core.DefaultInvocationBuilder.execute(DefaultInvocationBuilder.java:247)\n"
                + "\tat com.github.dockerjava.core.exec.StartContainerCmdExec.exec(StartContainerCmdExec.java:27)\n"
                + "\tat org.testcontainers.dockerclient.transport.okhttp.OkDockerHttpClient.execute(OkDockerHttpClient.java:170)\n";
    }

    /**
     * Build a threshold-exceeded stack trace (no Caused by chain, ~20 framework frames).
     * This is what Spring generates when it skips reloading a previously-failed context.
     */
    private static String buildThresholdTrace(String testClass) {
        String thresholdMessage = buildThresholdMessage(testClass);
        return "java.lang.IllegalStateException: " + thresholdMessage + "\n"
                + "\tat org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:152)\n"
                + "\tat org.springframework.test.context.support.DefaultTestContext.getApplicationContext(DefaultTestContext.java:130)\n"
                + "\tat org.springframework.test.context.support.DependencyInjectionTestExecutionListener.injectDependencies(DependencyInjectionTestExecutionListener.java:142)\n"
                + "\tat org.springframework.test.context.support.DependencyInjectionTestExecutionListener.prepareTestInstance(DependencyInjectionTestExecutionListener.java:98)\n"
                + "\tat org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener.prepareTestInstance(SpringBootDependencyInjectionTestExecutionListener.java:50)\n"
                + "\tat org.springframework.test.context.TestContextManager.prepareTestInstance(TestContextManager.java:260)\n"
                + "\tat org.springframework.test.context.junit.jupiter.SpringExtension.postProcessTestInstance(SpringExtension.java:163)\n"
                + "\tat org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor.invokeTestInstancePostProcessors(ClassBasedTestDescriptor.java:378)\n"
                + "\tat org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor.instantiateAndPostProcessTestInstance(ClassBasedTestDescriptor.java:290)\n"
                + "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.prepare(TestMethodTestDescriptor.java:103)\n"
                + "\tat org.junit.jupiter.engine.execution.HierarchicalTestEngine$HierarchicalTestExecutor.lambda$execute$0(HierarchicalTestEngine.java:60)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$prepare$2(NodeTestTask.java:128)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:35)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)\n"
                + "\tat org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:54)\n"
                + "\tat org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:198)\n"
                + "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:248)\n"
                + "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:191)\n";
    }

    // ---- Utility methods ----

    private static int computeTotalChars(List<TestFailure> failures, ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(failures).length();
    }

    private static double reduction(int before, int after) {
        return 100.0 * (before - after) / before;
    }
}
