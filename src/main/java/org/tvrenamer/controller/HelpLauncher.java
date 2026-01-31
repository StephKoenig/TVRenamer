package org.tvrenamer.controller;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.program.Program;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launches the embedded help documentation in the user's default browser.
 *
 * Help files are stored as resources in the JAR and extracted to a temporary
 * directory when needed. The extraction is cached for the duration of the
 * application session.
 */
public class HelpLauncher extends SelectionAdapter {

    private static final Logger logger = Logger.getLogger(HelpLauncher.class.getName());

    private static final String HELP_RESOURCE_PATH = "/help/";
    private static final String[] HELP_FILES = {
        "index.html",
        "getting-started.html",
        "adding-files.html",
        "renaming.html",
        "preferences.html",
        "show-matching.html",
        "metadata-tagging.html",
        "troubleshooting.html",
        "style.css"
    };

    // Cached help directory (extracted once per session)
    private static Path helpDirectory = null;
    private static final Object EXTRACTION_LOCK = new Object();

    private final String helpPage;

    /**
     * Create a HelpLauncher that opens the help index.
     */
    public HelpLauncher() {
        this("index.html");
    }

    /**
     * Create a HelpLauncher that opens a specific help page.
     *
     * @param helpPage the HTML file to open (e.g., "preferences.html")
     */
    public HelpLauncher(String helpPage) {
        this.helpPage = helpPage;
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
        openHelp();
    }

    /**
     * Open the help page in the default browser.
     */
    public void openHelp() {
        try {
            Path helpDir = getOrExtractHelpDirectory();
            if (helpDir == null) {
                logger.warning("Could not extract help files");
                return;
            }

            Path helpFile = helpDir.resolve(helpPage);
            if (!Files.exists(helpFile)) {
                logger.warning("Help file not found: " + helpFile);
                // Fall back to index
                helpFile = helpDir.resolve("index.html");
            }

            String fileUrl = helpFile.toUri().toString();
            logger.fine("Opening help: " + fileUrl);
            Program.launch(fileUrl);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to open help", e);
        }
    }

    /**
     * Get the help directory, extracting from JAR if necessary.
     *
     * @return path to the help directory, or null if extraction failed
     */
    private static Path getOrExtractHelpDirectory() {
        if (helpDirectory != null && Files.isDirectory(helpDirectory)) {
            return helpDirectory;
        }

        synchronized (EXTRACTION_LOCK) {
            // Double-check after acquiring lock
            if (helpDirectory != null && Files.isDirectory(helpDirectory)) {
                return helpDirectory;
            }

            try {
                helpDirectory = extractHelpFiles();
                return helpDirectory;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to extract help files", e);
                return null;
            }
        }
    }

    /**
     * Extract help files from the JAR to a temporary directory.
     *
     * @return path to the extracted help directory
     * @throws IOException if extraction fails
     */
    private static Path extractHelpFiles() throws IOException {
        // Create temp directory for help files
        Path tempDir = Files.createTempDirectory("tvrenamer-help-");

        // Mark for deletion on JVM exit (best effort)
        tempDir.toFile().deleteOnExit();

        logger.fine("Extracting help files to: " + tempDir);

        for (String filename : HELP_FILES) {
            String resourcePath = HELP_RESOURCE_PATH + filename;
            try (InputStream is = HelpLauncher.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.warning("Help resource not found: " + resourcePath);
                    continue;
                }

                Path targetFile = tempDir.resolve(filename);
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                targetFile.toFile().deleteOnExit();

                logger.fine("Extracted: " + filename);
            }
        }

        return tempDir;
    }

    /**
     * Open the help index directly (convenience method for non-SWT contexts).
     */
    public static void openHelpIndex() {
        new HelpLauncher().openHelp();
    }
}
