package org.tvrenamer.controller.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExternalToolDetector}.
 */
public class ExternalToolDetectorTest {

    @Test
    public void testDetectJavaInPath() {
        // "java" should be on PATH in any JDK test environment.
        assertTrue(
            ExternalToolDetector.isExecutableInPath("java"),
            "java should be found in PATH"
        );
    }

    @Test
    public void testDetectNonexistentExecutable() {
        assertFalse(
            ExternalToolDetector.isExecutableInPath("nonexistent_tool_xyz_12345"),
            "nonexistent tool should not be found"
        );
    }

    @Test
    public void testDetectReturnsPathName() {
        // When found in PATH, detect should return the simple name.
        String result = ExternalToolDetector.detect(
            new String[] { "java" },
            new String[] {},
            new String[] {}
        );
        assertEquals("java", result);
    }

    @Test
    public void testDetectReturnsEmptyWhenNotFound() {
        String result = ExternalToolDetector.detect(
            new String[] { "nonexistent_tool_xyz_12345" },
            new String[] {},
            new String[] {}
        );
        assertEquals("", result);
    }

    @Test
    public void testDetectTriesMultipleNames() {
        // First name doesn't exist, second is "java" which should work.
        String result = ExternalToolDetector.detect(
            new String[] { "nonexistent_tool_xyz_12345", "java" },
            new String[] {},
            new String[] {}
        );
        assertEquals("java", result);
    }
}
