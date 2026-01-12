package org.tvrenamer.controller;

import static org.tvrenamer.model.util.Constants.*;

import org.tvrenamer.model.ShowStore;
import org.tvrenamer.view.UIStarter;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class Launcher {
    private static final Logger logger = Logger.getLogger(Launcher.class.getName());
    private static FileHandler startupFileHandler;

    static void initializeLogger() {
        // Find logging.properties file inside jar
        try (InputStream loggingConfigStream = Launcher.class.getResourceAsStream(LOGGING_PROPERTIES)) {
            if (loggingConfigStream == null) {
                System.err.println("Warning: logging properties not found.");
            } else {
                LogManager.getLogManager().readConfiguration(loggingConfigStream);
            }
        } catch (IOException e) {
            System.err.println("Exception thrown while loading logging config");
            e.printStackTrace();
        }

        // Add a file handler to write startup log to current directory
        try {
            String logPath = System.getProperty("user.dir") + "/tvrenamer-startup.log";
            startupFileHandler = new FileHandler(logPath, false);
            startupFileHandler.setFormatter(new SimpleFormatter());
            startupFileHandler.setLevel(Level.ALL);
            Logger.getLogger("").addHandler(startupFileHandler);
            logger.info("Startup log initialized: " + logPath);
        } catch (IOException e) {
            System.err.println("Could not create startup log file: " + e.getMessage());
        }
    }

    /**
     * Shut down any threads that we know might be running. Sadly hard-coded.
     */
    private static void tvRenamerThreadShutdown() {
        logger.fine("Shutting down MoveRunner...");
        MoveRunner.shutDown();
        logger.fine("Cleaning up ShowStore...");
        ShowStore.cleanUp();
        logger.fine("Cleaning up ListingsLookup...");
        ListingsLookup.cleanUp();
        logger.fine("Shutdown complete.");

        // Close the startup file handler
        if (startupFileHandler != null) {
            startupFileHandler.close();
        }
    }

    /**
     * All this application does is run the UI, with no arguments. Configuration
     * comes from the PREFERENCES_FILE (see Constants.java). But in the future,
     * it might be able to do different things depending on command-line arguments.
     *
     * @param args
     *             not actually processed, at this time
     */
    public static void main(String[] args) {
        logger.info("=== TVRenamer Startup ===");
        logger.info("Version: " + VERSION_NUMBER);
        logger.info("Java Version: " + System.getProperty("java.version"));
        logger.info("Working Directory: " + System.getProperty("user.dir"));

        logger.fine("Initializing logger...");
        initializeLogger();

        logger.fine("Creating UIStarter...");
        UIStarter ui = new UIStarter();

        logger.fine("Running UI...");
        int status = ui.run();

        logger.fine("UI exited with status: " + status);
        tvRenamerThreadShutdown();

        logger.info("=== TVRenamer Exit ===");
        System.exit(status);
    }
}
