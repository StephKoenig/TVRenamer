package org.tvrenamer.view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.tvrenamer.model.ThemeMode;
import org.tvrenamer.model.UserPreferences;
import org.tvrenamer.model.util.Environment;

/**
 * Central place for resolving the preferred UI theme and provisioning the SWT colours that
 * implement that theme.
 */
public final class ThemeManager {

    private static final Logger logger = Logger.getLogger(
        ThemeManager.class.getName()
    );

    private static final Duration DETECTION_TIMEOUT = Duration.ofSeconds(1);

    private ThemeManager() {
        // Utility class
    }

    /**
     * Resolve the effective theme for the given preference, applying OS detection when AUTO
     * is selected.
     *
     * @param preference requested theme mode
     * @return resolved theme mode (never null)
     */
    public static ThemeMode resolveTheme(ThemeMode preference) {
        ThemeMode requested = preference != null ? preference : ThemeMode.LIGHT;
        if (requested != ThemeMode.AUTO) {
            return requested;
        }

        ThemeMode detected = detectSystemTheme();
        logger.fine("Auto theme resolved to " + detected);
        return detected;
    }

    /**
     * Create a new {@link ThemePalette} for the supplied display, using the user's stored
     * preference (falling back to LIGHT if unset).
     *
     * @param display SWT display (must not be null)
     * @return palette instance that must be {@link ThemePalette#dispose() disposed} when no longer needed
     */
    public static ThemePalette createPalette(Display display) {
        UserPreferences prefs = UserPreferences.getInstance();
        ThemeMode preference = prefs.getThemeMode();
        ThemeMode resolved = resolveTheme(preference);
        return createPalette(display, resolved);
    }

    /**
     * Create a palette for the given display and mode.
     *
     * @param display SWT display (must not be null)
     * @param mode    theme mode to realise
     * @return palette instance
     */
    public static ThemePalette createPalette(Display display, ThemeMode mode) {
        if (display == null) {
            throw new IllegalArgumentException("Display must not be null");
        }
        ThemeMode resolved = resolveTheme(mode);
        return new ThemePalette(display, resolved);
    }

    /**
     * Convenience helper that applies colours to a control tree.
     *
     * @param control root control
     * @param palette palette to use
     */
    public static void applyPalette(Control control, ThemePalette palette) {
        if (control == null || palette == null) {
            return;
        }
        control.setBackground(palette.getControlBackground());
        control.setForeground(palette.getControlForeground());

        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                applyPalette(child, palette);
            }
        }
    }

    private static ThemeMode detectSystemTheme() {
        try {
            if (Environment.IS_WINDOWS) {
                Boolean usesLight = readWindowsAppsUseLightTheme();
                if (usesLight != null) {
                    return usesLight ? ThemeMode.LIGHT : ThemeMode.DARK;
                }
            } else if (Environment.IS_MAC_OSX) {
                Boolean dark = readMacOsInterfaceStyleDark();
                if (dark != null) {
                    return dark ? ThemeMode.DARK : ThemeMode.LIGHT;
                }
            } else {
                Boolean dark = readFreedesktopDarkPreference();
                if (dark != null) {
                    return dark ? ThemeMode.DARK : ThemeMode.LIGHT;
                }
            }
        } catch (Exception ex) {
            logger.log(
                Level.FINE,
                "Theme detection failed; defaulting to LIGHT",
                ex
            );
        }
        return ThemeMode.LIGHT;
    }

    private static Boolean readWindowsAppsUseLightTheme() {
        ProcessBuilder builder = new ProcessBuilder(
            "reg",
            "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v",
            "AppsUseLightTheme"
        );
        String output = runCommand(builder);
        if (output == null) {
            return null;
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("AppsUseLightTheme")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length > 2) {
                    String rawValue = parts[parts.length - 1];
                    try {
                        int value = Integer.decode(rawValue);
                        return value != 0;
                    } catch (NumberFormatException ex) {
                        logger.log(
                            Level.FINE,
                            "Unable to parse AppsUseLightTheme value: " +
                                rawValue,
                            ex
                        );
                    }
                }
            }
        }
        return null;
    }

    private static Boolean readMacOsInterfaceStyleDark() {
        ProcessBuilder builder = new ProcessBuilder(
            "defaults",
            "read",
            "-g",
            "AppleInterfaceStyle"
        );
        String output = runCommand(builder);
        if (output == null) {
            return null;
        }
        return output.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static Boolean readFreedesktopDarkPreference() {
        String preferDark = System.getenv("GTK_THEME");
        if (
            preferDark != null &&
            preferDark.toLowerCase(Locale.ROOT).contains("dark")
        ) {
            return Boolean.TRUE;
        }
        String kdeLookAndFeel = System.getenv("KDE_FULL_SESSION");
        if (kdeLookAndFeel != null) {
            String plasmaTheme = System.getenv("PLASMA_USE_QT_SCALING");
            if (
                plasmaTheme != null &&
                plasmaTheme.toLowerCase(Locale.ROOT).contains("dark")
            ) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    private static String runCommand(ProcessBuilder builder) {
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(
                DETECTION_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                logger.fine(
                    "Theme detection command timed out: " + builder.command()
                );
                return null;
            }
            if (process.exitValue() != 0) {
                logger.fine(
                    "Theme detection command exited with " + process.exitValue()
                );
                return null;
            }
            try (
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                )
            ) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
                return sb.toString().trim();
            }
        } catch (IOException | InterruptedException ex) {
            logger.log(
                Level.FINE,
                "Theme detection command failed: " + builder.command(),
                ex
            );
            Thread.currentThread().interrupt();
            return null;
        } catch (SecurityException ex) {
            logger.log(
                Level.FINE,
                "Insufficient rights to run theme detection command",
                ex
            );
            return null;
        }
    }
}
