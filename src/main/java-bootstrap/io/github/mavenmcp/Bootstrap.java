package io.github.mavenmcp;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bootstrap entry point compiled at Java 11 bytecode level.
 * Validates JVM version before loading the main application
 * to provide a clear error message instead of UnsupportedClassVersionError.
 */
public class Bootstrap {

    static final int REQUIRED_JAVA_VERSION = 21;
    static final String SERVER_CLASS = "io.github.mavenmcp.MavenMcpServer";

    public static void main(String[] args) {
        int currentVersion = Runtime.version().feature();
        if (!checkVersion(currentVersion, System.err)) {
            System.exit(1);
        }

        try {
            delegateToServer(SERVER_CLASS, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null
                    ? e.getCause()
                    : e;
            System.err.println("Failed to start maven-mcp: " + cause.getMessage());
            System.exit(1);
        }
    }

    /**
     * Loads the server class by name and invokes its main method via reflection.
     *
     * @param className fully qualified server class name
     * @param args CLI arguments to pass through
     * @throws ReflectiveOperationException if the class cannot be loaded or invoked
     */
    static void delegateToServer(String className, String[] args) throws ReflectiveOperationException {
        Class<?> serverClass = Class.forName(className);
        Method mainMethod = serverClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }

    /**
     * Checks if the given Java version meets the minimum requirement.
     * On failure, prints a human-readable error to the provided stream.
     *
     * @param detectedVersion the JVM feature version
     * @param err stream for error output
     * @return true if version is sufficient, false otherwise
     */
    static boolean checkVersion(int detectedVersion, PrintStream err) {
        if (detectedVersion < REQUIRED_JAVA_VERSION) {
            err.println("maven-mcp requires Java " + REQUIRED_JAVA_VERSION + "+");
            err.println("Detected: Java " + detectedVersion);
            err.println("Install Java " + REQUIRED_JAVA_VERSION + "+: https://adoptium.net/");
            return false;
        }
        return true;
    }
}
