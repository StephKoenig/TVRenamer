package org.tvrenamer.model.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tvrenamer.controller.util.StringUtils;

public class Environment {

    private static final Logger logger = Logger.getLogger(
        Environment.class.getName()
    );

    public static final String USER_HOME = System.getProperty("user.home");
    public static final String TMP_DIR_NAME = System.getProperty(
        "java.io.tmpdir"
    );
    private static final String OS_NAME = System.getProperty("os.name");

    private enum OSType {
        WINDOWS,
        LINUX,
        MAC,
    }

    private static OSType chooseOSType() {
        if (OS_NAME.contains("Mac")) {
            return OSType.MAC;
        }
        if (OS_NAME.contains("Windows")) {
            return OSType.WINDOWS;
        }
        return OSType.LINUX;
    }

    private static final OSType JVM_OS_TYPE = chooseOSType();
    public static final boolean IS_MAC_OSX = (JVM_OS_TYPE == OSType.MAC);
    public static final boolean IS_WINDOWS = (JVM_OS_TYPE == OSType.WINDOWS);

    @SuppressWarnings("unused")
    public static final boolean IS_UN_X = (JVM_OS_TYPE == OSType.LINUX);

    // If InputStream.read() fails, it returns -1.  So, anything less than zero is
    // clearly a failure.  But we assume a version must at least be "x.y", so let's
    // call anything less than three bytes a fail.
    private static final int MIN_BYTES_FOR_VERSION = 3;

    private static String readResourceTrimmed(
        final String resourcePath,
        final int maxBytes,
        final int minBytes,
        final String notFoundMessage,
        final String tooShortMessage
    ) {
        byte[] buffer = new byte[maxBytes];

        try (
            InputStream stream = Environment.class.getResourceAsStream(
                resourcePath
            )
        ) {
            if (stream == null) {
                throw new RuntimeException(notFoundMessage);
            }

            int bytesRead = stream.read(buffer);
            if (bytesRead < minBytes) {
                throw new RuntimeException(tooShortMessage);
            }

            return StringUtils.makeString(buffer).trim();
        } catch (IOException ioe) {
            logger.log(
                Level.WARNING,
                "Exception when reading resource " + resourcePath,
                ioe
            );
            throw new RuntimeException(
                "Exception when reading resource " + resourcePath,
                ioe
            );
        } catch (RuntimeException re) {
            // Preserve the original message for easier diagnostics
            throw re;
        } catch (Exception e) {
            logger.log(
                Level.WARNING,
                "Exception when reading resource " + resourcePath,
                e
            );
            throw new RuntimeException(
                "Exception when reading resource " + resourcePath,
                e
            );
        }
    }

    static String readVersionNumber() {
        return readResourceTrimmed(
            "/tvrenamer.version",
            32,
            MIN_BYTES_FOR_VERSION,
            "Version file '/tvrenamer.version' not found on classpath",
            "Unable to extract version from version file"
        );
    }

    /**
     * Read the build date (YYMMDD, UTC) from the generated build metadata resource.
     *
     * @return build date in YYMMDD format, or empty string if unavailable
     */
    public static String readBuildDateYYMMDD() {
        try {
            return readResourceTrimmed(
                "/tvrenamer.builddate",
                32,
                1,
                "Build date file '/tvrenamer.builddate' not found on classpath",
                "Unable to extract build date from build date file"
            );
        } catch (RuntimeException ignored) {
            // Best-effort: show nothing rather than crashing the UI.
            return "";
        }
    }

    /**
     * Read the git commit SHA from the generated build metadata resource.
     *
     * @return full commit SHA, or empty string if unavailable
     */
    public static String readCommitSha() {
        try {
            return readResourceTrimmed(
                "/tvrenamer.commit",
                128,
                1,
                "Commit file '/tvrenamer.commit' not found on classpath",
                "Unable to extract commit from commit file"
            );
        } catch (RuntimeException ignored) {
            // Best-effort: show nothing rather than crashing the UI.
            return "";
        }
    }
}
