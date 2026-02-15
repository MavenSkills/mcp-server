package io.github.mavenmcp.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent stack trace processor that preserves root cause and application frames
 * while collapsing framework noise.
 *
 * <p>Parses stack traces into segments (top-level exception + Caused by chains),
 * classifies frames as application or framework by package prefix, collapses
 * consecutive framework frames with summary markers, and always preserves
 * the root cause segment.</p>
 */
public final class StackTraceProcessor {

    private static final int DEFAULT_ROOT_CAUSE_APP_FRAMES = 10;

    private StackTraceProcessor() {
    }

    /**
     * Process a stack trace with intelligent truncation.
     *
     * @param stackTrace     raw stack trace string
     * @param appPackage     application package prefix for frame classification (null/blank = keep all)
     * @param stackTraceLines hard cap on output lines (0 = no cap)
     * @return processed stack trace, or null if input is null/blank
     */
    public static String process(String stackTrace, String appPackage, int stackTraceLines) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return null;
        }

        boolean filterFrames = appPackage != null && !appPackage.isBlank();
        List<Segment> segments = parseSegments(stackTrace.strip());

        if (segments.isEmpty()) {
            return stackTrace.strip();
        }

        List<String> outputLines = new ArrayList<>();

        // Always include top-level exception first line
        Segment topLevel = segments.getFirst();
        outputLines.add(topLevel.header);

        if (segments.size() == 1) {
            // Simple trace — collapse frames from the single segment
            addCollapsedFrames(outputLines, topLevel.frames, appPackage, filterFrames);
        } else {
            // Multi-segment: collapse top-level, show chain headers, preserve root cause
            addCollapsedFrames(outputLines, topLevel.frames, appPackage, filterFrames);

            // Intermediate segments: just header + collapsed frames
            for (int i = 1; i < segments.size() - 1; i++) {
                Segment seg = segments.get(i);
                outputLines.add(seg.header);
                addCollapsedFrames(outputLines, seg.frames, appPackage, filterFrames);
            }

            // Root cause: header + up to N application frames (or all if no filtering)
            Segment rootCause = segments.getLast();
            outputLines.add(rootCause.header);
            addRootCauseFrames(outputLines, rootCause.frames, appPackage, filterFrames);
        }

        // Apply hard cap
        if (stackTraceLines > 0 && outputLines.size() > stackTraceLines) {
            outputLines = applyHardCap(outputLines, segments, stackTraceLines);
        }

        return String.join("\n", outputLines);
    }

    /**
     * Parse a stack trace into segments. Each segment has a header line and frame lines.
     */
    static List<Segment> parseSegments(String stackTrace) {
        String[] lines = stackTrace.split("\n");
        List<Segment> segments = new ArrayList<>();

        String currentHeader = null;
        List<String> currentFrames = new ArrayList<>();

        for (String line : lines) {
            if (currentHeader == null) {
                // First line is always the top-level exception header
                currentHeader = line;
            } else if (line.startsWith("Caused by:")) {
                // Only matches non-indented lines — indented "Caused by:" inside suppressed
                // blocks (e.g. "\tCaused by:") stays as a frame line. isStructuralLine() relies
                // on this invariant to preserve those headers during frame collapsing.
                segments.add(new Segment(currentHeader, List.copyOf(currentFrames)));
                currentFrames.clear();
                currentHeader = line;
            } else {
                currentFrames.add(line);
            }
        }

        // Don't forget the last segment
        if (currentHeader != null) {
            segments.add(new Segment(currentHeader, List.copyOf(currentFrames)));
        }

        return segments;
    }

    /**
     * Add frames to output, collapsing consecutive framework frames.
     */
    private static void addCollapsedFrames(List<String> output, List<String> frames,
                                           String appPackage, boolean filterFrames) {
        if (!filterFrames) {
            output.addAll(frames);
            return;
        }

        int frameworkCount = 0;
        for (String frame : frames) {
            if (isStructuralLine(frame) || isApplicationFrame(frame, appPackage)) {
                if (frameworkCount > 0) {
                    output.add("\t... " + frameworkCount + " framework frames omitted");
                    frameworkCount = 0;
                }
                output.add(frame);
            } else {
                frameworkCount++;
            }
        }
        if (frameworkCount > 0) {
            output.add("\t... " + frameworkCount + " framework frames omitted");
        }
    }

    /**
     * Add root cause frames: preserve up to N application frames, collapse framework.
     */
    private static void addRootCauseFrames(List<String> output, List<String> frames,
                                           String appPackage, boolean filterFrames) {
        if (!filterFrames) {
            output.addAll(frames);
            return;
        }

        int appFrameCount = 0;
        int frameworkCount = 0;
        for (String frame : frames) {
            boolean structural = isStructuralLine(frame);
            if (structural || isApplicationFrame(frame, appPackage)) {
                if (frameworkCount > 0) {
                    output.add("\t... " + frameworkCount + " framework frames omitted");
                    frameworkCount = 0;
                }
                if (structural || appFrameCount < DEFAULT_ROOT_CAUSE_APP_FRAMES) {
                    output.add(frame);
                    if (!structural) {
                        appFrameCount++;
                    }
                }
            } else {
                frameworkCount++;
            }
        }
        if (frameworkCount > 0) {
            output.add("\t... " + frameworkCount + " framework frames omitted");
        }
    }

    /**
     * Check if a stack frame line belongs to the application package.
     */
    static boolean isApplicationFrame(String frameLine, String appPackage) {
        if (appPackage == null || appPackage.isBlank()) {
            return true;
        }
        // Stack frames look like: "\tat com.example.Foo.method(Foo.java:42)"
        String trimmed = frameLine.strip();
        if (trimmed.startsWith("at ")) {
            String className = trimmed.substring(3);
            return className.startsWith(appPackage);
        }
        // Lines like "\t... 42 more" are framework artifacts
        return false;
    }

    /**
     * Check if a line is a structural exception header or JDK-generated marker
     * that must always be preserved. This includes {@code Suppressed:} lines,
     * indented {@code Caused by:} lines (inside suppressed blocks), and
     * {@code ... N more} elision markers generated by the JVM.
     */
    static boolean isStructuralLine(String line) {
        if (line.isEmpty()) {
            return false;
        }
        String stripped = line.strip();
        if (stripped.startsWith("Suppressed:")) {
            return true;
        }
        // JDK-generated elision marker: "... 3 more" (preserves shared-frame info from JVM)
        if (stripped.startsWith("... ") && stripped.endsWith(" more")) {
            return true;
        }
        // Indented "Caused by:" — inside a suppressed block (top-level has no leading whitespace)
        return stripped.startsWith("Caused by:") && Character.isWhitespace(line.charAt(0));
    }

    /**
     * Apply hard cap: truncate to limit while preserving root cause header and at least one frame.
     */
    private static List<String> applyHardCap(List<String> lines, List<Segment> segments,
                                             int maxLines) {
        if (segments.size() <= 1) {
            // Simple trace: just truncate
            return new ArrayList<>(lines.subList(0, maxLines));
        }

        // Find the root cause header in the output
        Segment rootCause = segments.getLast();
        int rootCauseHeaderIdx = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).equals(rootCause.header)) {
                rootCauseHeaderIdx = i;
                break;
            }
        }

        if (rootCauseHeaderIdx < 0 || rootCauseHeaderIdx >= maxLines - 1) {
            // Root cause header already beyond the cap — take what we can from the end
            // Ensure we at least have the top-level header + root cause header + one frame
            List<String> result = new ArrayList<>();
            result.add(lines.getFirst()); // top-level header
            result.add("\t... (intermediate frames truncated)");
            result.add(rootCause.header);
            // Add root cause frames until we hit the cap
            int remaining = maxLines - 3;
            int rootCauseStart = rootCauseHeaderIdx + 1;
            for (int i = rootCauseStart; i < lines.size() && remaining > 0; i++) {
                result.add(lines.get(i));
                remaining--;
            }
            return result;
        }

        // Root cause header is within the cap — just truncate at the limit
        return new ArrayList<>(lines.subList(0, maxLines));
    }

    /**
     * A stack trace segment: one exception with its frames.
     */
    record Segment(String header, List<String> frames) {
    }
}
