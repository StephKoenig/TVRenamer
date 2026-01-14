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

    static String readVersionNumber() {
        byte[] buffer = new byte[10];

        // Runtime: the version file must be present on the classpath.
        try (
            InputStream versionStream = Environment.class.getResourceAsStream(
                "/tvrenamer.version"
            )
        ) {
            if (versionStream == null) {
                throw new RuntimeException(
                    "Version file '/tvrenamer.version' not found on classpath"
                );
            }

            int bytesRead = versionStream.read(buffer);
            if (bytesRead < MIN_BYTES_FOR_VERSION) {
                throw new RuntimeException(
                    "Unable to extract version from version file"
                );
            }
            return StringUtils.makeString(buffer).trim();
        } catch (IOException ioe) {
            logger.log(
                Level.WARNING,
                "Exception when reading version file",
                ioe
            );
            throw new RuntimeException(
                "Exception when reading version file",
                ioe
            );
        } catch (RuntimeException re) {
            // Preserve the original message for easier diagnostics
            throw re;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Exception when reading version file", e);
            throw new RuntimeException(
                "Exception when reading version file",
                e
            );
        }
    }
}
