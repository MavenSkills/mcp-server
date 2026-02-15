package io.github.mavenmcp.tool;

import java.nio.file.Path;
import java.util.List;

import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;

/**
 * Shared MavenRunner test doubles for tool tests.
 */
final class TestRunners {

    private TestRunners() {
    }

    /** Returns a fixed result for any execution. */
    static class StubRunner extends MavenRunner {
        private final MavenExecutionResult result;

        StubRunner(MavenExecutionResult result) {
            this.result = result;
        }

        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            return result;
        }
    }

    static class CapturingRunner extends MavenRunner {
        String capturedGoal;
        List<String> capturedArgs;
        final java.util.ArrayList<String> allGoals = new java.util.ArrayList<>();
        private final java.util.Set<String> failingGoals = new java.util.HashSet<>();

        void failOnGoal(String goal) {
            failingGoals.add(goal);
        }

        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            capturedGoal = goal;
            capturedArgs = extraArgs;
            allGoals.add(goal);
            int exitCode = failingGoals.contains(goal) ? 1 : 0;
            return new MavenExecutionResult(exitCode, "", "", 100);
        }
    }

    /** Always throws MavenExecutionException. */
    static class ThrowingRunner extends MavenRunner {
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            throw new MavenExecutionException("Simulated failure", new RuntimeException(), 0);
        }
    }
}
