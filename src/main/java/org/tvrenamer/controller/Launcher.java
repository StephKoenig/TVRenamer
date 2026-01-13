package org.tvrenamer.controller;

import static org.tvrenamer.model.util.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.tvrenamer.model.ShowStore;
import org.tvrenamer.view.UIStarter;

class Launcher {

    private static final Logger logger = Logger.getLogger(
        Launcher.class.getName()
    );
    private static FileHandler startupFileHandler;

    static void initializeFileLogger() {
        // Add a file handler FIRST so we capture all messages
        try {
            String logPath =
                System.getProperty("user.dir") + "/tvrenamer-startup.log";
            startupFileHandler = new FileHandler(logPath, false);
            startupFileHandler.setFormatter(new SimpleFormatter());
            startupFileHandler.setLevel(Level.ALL);
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(startupFileHandler);
            rootLogger.setLevel(Level.ALL);
            logger.info("Startup log initialized: " + logPath);
            logger.info(
                "Root logger handler count after startup init: " +
                    rootLogger.getHandlers().length
            );
        } catch (IOException e) {
            System.err.println(
                "Could not create startup log file: " + e.getMessage()
            );
        }
    }

    static void initializeLoggingConfig() {
        // Find logging.properties file inside jar
        try (
            InputStream loggingConfigStream =
                Launcher.class.getResourceAsStream(LOGGING_PROPERTIES)
        ) {
            if (loggingConfigStream == null) {
                logger.warning("Warning: logging properties not found.");
                return;
            }

            logger.info(
                "Logging configuration stream acquired: " +
                    loggingConfigStream.getClass().getName()
            );

            LogManager logManager = LogManager.getLogManager();
            Logger rootLogger = Logger.getLogger("");

            logger.info(
                "Root logger handler count before reload: " +
                    rootLogger.getHandlers().length
            );
            try {
                logManager.readConfiguration(loggingConfigStream);
            } catch (IOException ioe) {
                logger.log(
                    Level.WARNING,
                    "IOException while loading logging config",
                    ioe
                );
                return;
            }
            logger.info("Logging properties loaded successfully.");
            logger.info(
                "Root logger level after reload: " + rootLogger.getLevel()
            );
            logger.info(
                "Root logger handler count after reload: " +
                    rootLogger.getHandlers().length
            );

            if (startupFileHandler == null) {
                logger.warning(
                    "Startup file handler missing after configuration reload."
                );
                return;
            }

            boolean alreadyAttached = false;
            for (Handler handler : rootLogger.getHandlers()) {
                if (handler == startupFileHandler) {
                    alreadyAttached = true;
                    break;
                }
            }

            if (!alreadyAttached) {
                rootLogger.addHandler(startupFileHandler);
                logger.info(
                    "Reattached startup file handler after configuration reload."
                );
            }

            for (Handler handler : rootLogger.getHandlers()) {
                logger.info(
                    "Root logger handler after reload: " +
                        handler.getClass().getName()
                );
            }
        } catch (Throwable t) {
            logException("initializeLoggingConfig", t);
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

    private static void logException(String context, Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        logger.severe("EXCEPTION in " + context + ":\n" + sw.toString());
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
        // Initialize file logging FIRST to capture everything
        initializeFileLogger();

        // Set up global exception handler to catch any uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logException(
                "Uncaught exception in thread " + thread.getName(),
                throwable
            );
            if (startupFileHandler != null) {
                startupFileHandler.close();
            }
        });

        try {
            logger.info("=== TVRenamer Startup ===");
            logger.info("Version: " + VERSION_NUMBER);
            logger.info("Java Version: " + System.getProperty("java.version"));
            logger.info("Java Home: " + System.getProperty("java.home"));
            logger.info("Working Directory: " + System.getProperty("user.dir"));
            logger.info(
                "OS: " +
                    System.getProperty("os.name") +
                    " " +
                    System.getProperty("os.arch")
            );
            logger.info(
                "Launcher class loader: " + Launcher.class.getClassLoader()
            );
            logger.info(
                "Context class loader: " +
                    Thread.currentThread().getContextClassLoader()
            );
            logger.info(
                "java.library.path: " + System.getProperty("java.library.path")
            );

            logger.info("Loading logging configuration...");
            initializeLoggingConfig();
            logger.info("Logging configuration load complete.");

            logger.info("Creating UIStarter...");
            UIStarter ui = new UIStarter();
            logger.info("UIStarter created successfully.");

            logger.info("Running UI...");
            int status = ui.run();

            logger.info("UI exited with status: " + status);
            tvRenamerThreadShutdown();

            logger.info("=== TVRenamer Exit ===");
            System.exit(status);
        } catch (Throwable t) {
            logException("main()", t);
            if (startupFileHandler != null) {
                startupFileHandler.close();
            }
            System.exit(1);
        }
    }
}
