package org.tvrenamer.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.AbstractReflectionConverter.UnknownFieldException;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tvrenamer.model.ThemeMode;
import org.tvrenamer.model.UserPreferences;

public class UserPreferencesPersistence {

    private static final Logger logger = Logger.getLogger(
        UserPreferencesPersistence.class.getName()
    );

    // Use reflection provider so the default constructor is called, thus calling
    // the superclass constructor. Instantiate the object so the Observable superclass
    // is called correctly.
    private static final XStream xstream = new XStream(
        new PureJavaReflectionProvider()
    );

    static {
        // XStream requires explicit security permissions
        xstream.allowTypes(
            new Class[] { UserPreferences.class, ThemeMode.class }
        );
        xstream.allowTypesByWildcard(new String[] { "org.tvrenamer.model.**" });

        xstream.alias("preferences", UserPreferences.class);

        xstream.omitField(UserPreferences.class, "pcs");
        xstream.aliasField(
            "moveEnabled",
            UserPreferences.class,
            "moveSelected"
        );

        xstream.aliasField(
            "renameEnabled",
            UserPreferences.class,
            "renameSelected"
        );
        xstream.aliasField("theme", UserPreferences.class, "themeMode");

        // Make the fields of PropertyChangeSupport transient
        xstream.omitField(java.beans.PropertyChangeSupport.class, "listeners");
        xstream.omitField(java.beans.PropertyChangeSupport.class, "children");
        xstream.omitField(java.beans.PropertyChangeSupport.class, "source");
        xstream.omitField(
            java.beans.PropertyChangeSupport.class,
            "propertyChangeSupportSerializedDataVersion"
        );
    }

    /**
     * Save the preferences object to the path.
     *
     * @param prefs the preferences object to save
     * @param path  the path to save it to
     */
    @SuppressWarnings("SameParameterValue")
    public static void persist(UserPreferences prefs, Path path) {
        String xml = xstream.toXML(prefs);

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
                "Exception occurred when writing preferences file '" +
                    path.toAbsolutePath() +
                    "'",
                e
            );
        }
    }

    /**
     * Load the preferences from path.
     *
     * @param path the path to read
     * @return the populated preferences object
     */
    @SuppressWarnings("SameParameterValue")
    public static UserPreferences retrieve(Path path) {
        if (Files.notExists(path)) {
            // If file doesn't exist, assume defaults
            logger.fine(
                "Preferences file '" +
                    path.toAbsolutePath() +
                    "' does not exist - assuming defaults"
            );
            return null;
        }

        try (InputStream in = Files.newInputStream(path)) {
            try {
                return (UserPreferences) xstream.fromXML(in);
            } catch (UnknownFieldException ufe) {
                // Forward/backward compatibility: tolerate unknown fields in prefs.xml.
                // Example: older/newer versions may have fields that don't exist in this build.
                logger.log(
                    Level.INFO,
                    "Ignoring unknown field(s) while reading preferences file '" +
                        path.toAbsolutePath() +
                        "': " +
                        ufe.getMessage(),
                    ufe
                );

                // Best-effort: ignore unknown fields and retry.
                xstream.ignoreUnknownElements();
                try (InputStream inRetry = Files.newInputStream(path)) {
                    return (UserPreferences) xstream.fromXML(inRetry);
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            logger.log(
                Level.SEVERE,
                "Exception reading preferences file '" +
                    path.toAbsolutePath() +
                    "'",
                e
            );
            logger.info("assuming default preferences");
            return null;
        }
    }
}
