package io.github.mavenmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Details of a single test failure or error.
 *
 * @param testClass  fully qualified test class name
 * @param testMethod test method name
 * @param message    failure/error message
 * @param stackTrace truncated stack trace (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestFailure(String testClass, String testMethod, String message, String stackTrace) {
}
