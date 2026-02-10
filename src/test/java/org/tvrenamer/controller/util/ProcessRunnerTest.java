package org.tvrenamer.controller.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProcessRunner}.
 */
public class ProcessRunnerTest {

    @Test
    public void testSuccessfulCommand() {
        // "java -version" should succeed on any system with JDK installed.
        ProcessRunner.Result result = ProcessRunner.run(
            List.of("java", "-version"), 10
        );
        assertTrue(result.success(), "java -version should succeed");
        assertEquals(0, result.exitCode());
    }

    @Test
    public void testFailedCommand() {
        // A command that doesn't exist should fail gracefully.
        ProcessRunner.Result result = ProcessRunner.run(
            List.of("nonexistent_command_12345"), 5
        );
        assertFalse(result.success(), "nonexistent command should fail");
    }

    @Test
    public void testOutputCapture() {
        // "java -version" outputs version info to stderr (merged via redirectErrorStream).
        ProcessRunner.Result result = ProcessRunner.run(
            List.of("java", "-version"), 10
        );
        assertTrue(result.success());
        assertNotNull(result.output());
        assertFalse(result.output().isBlank(), "should capture version output");
    }

    @Test
    public void testFailureResult() {
        ProcessRunner.Result failure = ProcessRunner.Result.failure();
        assertFalse(failure.success());
        assertEquals(-1, failure.exitCode());
        assertEquals("", failure.output());
    }

    @Test
    public void testNonZeroExitCode() {
        // "java --invalid-flag" should fail with non-zero exit.
        ProcessRunner.Result result = ProcessRunner.run(
            List.of("java", "--invalid-flag-xyz"), 10
        );
        assertFalse(result.success());
        assertNotEquals(0, result.exitCode());
    }
}
