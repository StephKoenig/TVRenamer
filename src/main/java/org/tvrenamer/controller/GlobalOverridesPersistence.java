package org.tvrenamer.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tvrenamer.model.GlobalOverrides;

public class GlobalOverridesPersistence {

    private static final Logger logger = Logger.getLogger(
        GlobalOverridesPersistence.class.getName()
    );

    // Use reflection provider so the default constructor is called, thus calling the superclass constructor
    private static final XStream xstream = new XStream(
        new PureJavaReflectionProvider()
    );

    static {
        // XStream requires explicit security permissions.
        // This ensures reading overrides.xml works under newer XStream defaults.
        xstream.allowTypes(new Class[] { GlobalOverrides.class });
        xstream.allowTypesByWildcard(new String[] { "org.tvrenamer.model.**" });

        xstream.alias("overrides", GlobalOverrides.class);
    }

    /**
     * Save the overrides object to the file.
     *
     * @param overrides the overrides object to save
     * @param path the path to save it to
     */
    @SuppressWarnings("SameParameterValue")
    public static void persist(GlobalOverrides overrides, Path path) {
        String xml = xstream.toXML(overrides);

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Overwrite any existing file
            Files.writeString(path, xml);
        } catch (
            IOException
            | UnsupportedOperationException
            | SecurityException e
        ) {
            logger.log(
                Level.SEVERE,
                "Exception occurred when writing overrides file '" +
                    path.toAbsolutePath() +
                    "'",
                e
            );
        }
    }

    /**
     * Load the overrides from path.
     *
     * @param path the path to read
     * @return the populated overrides object
     */
    @SuppressWarnings("SameParameterValue")
    public static GlobalOverrides retrieve(Path path) {
        if (Files.notExists(path)) {
            // If file doesn't exist, assume defaults
            logger.fine(
                "Overrides file '" +
                    path.toAbsolutePath() +
                    "' does not exist - assuming no overrides"
            );
            return null;
        }

        try (InputStream in = Files.newInputStream(path)) {
            return (GlobalOverrides) xstream.fromXML(in);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            logger.log(
                Level.SEVERE,
                "Exception reading overrides file '" +
                    path.toAbsolutePath() +
                    "'",
                e
            );
            logger.info("assuming no overrides");
            return null;
        }
    }
}
