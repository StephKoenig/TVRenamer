package org.tvrenamer.view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
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

        // Best-effort: TabFolder/TabItem headers are often OS/native themed and may ignore these,
        // but where supported this makes the tabs match dark mode.
        if (control instanceof TabFolder tabFolder) {
            installTabFolderHeaderTheming(tabFolder, palette);
        }

        // Best-effort border polish for dark mode.
        // SWT native widgets draw their own borders; this only helps where SWT allows custom painting.
        if (palette.isDark()) {
            if (control instanceof Table table) {
                installTableGridlinePainter(table, palette);
                installTableAlternatingRowBackground(table, palette);
            } else if (control instanceof Button button) {
                installButtonBorderPainter(button, palette);
            }
        }

        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                applyPalette(child, palette);
            }
        }
    }

    /**
     * Best-effort menu theming.
     *
     * SWT menus are typically OS-native:
     * - Some SWT versions/platforms don't expose public setters for menu colors.
     * - Even when setters exist, native theming may ignore them.
     *
     * So this is intentionally a no-op for compatibility; theming is handled via the owning
     * {@link Shell} and control palette application elsewhere.
     */
    public static void applyPalette(Object unusedMenu, ThemePalette palette) {
        // Intentionally empty (best-effort only; keep API surface to avoid churn).
        // We accept an Object to avoid coupling to SWT Menu APIs that vary by SWT version.
    }

    private static void installTabFolderHeaderTheming(
        final TabFolder tabFolder,
        final ThemePalette palette
    ) {
        if (tabFolder == null || palette == null || tabFolder.isDisposed()) {
            return;
        }

        // Avoid installing multiple times (applyPalette is recursive and may be called more than once).
        if (
            Boolean.TRUE.equals(
                tabFolder.getData("tvrenamer.tabfolder.theming")
            )
        ) {
            return;
        }
        tabFolder.setData("tvrenamer.tabfolder.theming", Boolean.TRUE);

        // Best-effort: on some SWT/platforms these setters are honored; on others they're ignored.
        try {
            tabFolder.setBackground(palette.getControlBackground());
        } catch (Exception ex) {
            logger.log(Level.FINEST, "TabFolder background not supported", ex);
        }
        try {
            tabFolder.setForeground(palette.getControlForeground());
        } catch (Exception ex) {
            logger.log(Level.FINEST, "TabFolder foreground not supported", ex);
        }

        // Best-effort note:
        // This SWT version does not expose TabItem color setters (no setForeground/setBackground),
        // so we can only theme the TabFolder itself and the composites within each tab.
    }

    private static void installTableGridlinePainter(
        final Table table,
        final ThemePalette palette
    ) {
        if (table == null || table.isDisposed()) {
            return;
        }

        // Avoid installing multiple times (applyPalette is recursive and may be called more than once).
        if (Boolean.TRUE.equals(table.getData("tvrenamer.dark.gridlines"))) {
            return;
        }
        table.setData("tvrenamer.dark.gridlines", Boolean.TRUE);

        // We draw subtle grid lines ourselves so they aren't bright white in dark mode.
        // This is best-effort: some platforms/themes may still draw native lines on top.
        table.addPaintListener(
            new PaintListener() {
                @Override
                public void paintControl(PaintEvent e) {
                    if (table.isDisposed()) {
                        return;
                    }
                    e.gc.setForeground(palette.getBorderColor());

                    Rectangle client = table.getClientArea();

                    // Horizontal lines at the bottom of each item
                    int itemCount = table.getItemCount();
                    for (int i = 0; i < itemCount; i++) {
                        TableItem item = table.getItem(i);
                        if (item == null) {
                            continue;
                        }
                        Rectangle bounds = item.getBounds(0);
                        int y = bounds.y + bounds.height - 1;
                        if (y >= client.y && y <= client.y + client.height) {
                            e.gc.drawLine(
                                client.x,
                                y,
                                client.x + client.width,
                                y
                            );
                        }
                    }

                    // Vertical column separators
                    int x = client.x;
                    int colCount = table.getColumnCount();
                    for (int c = 0; c < colCount; c++) {
                        x += table.getColumn(c).getWidth();
                        e.gc.drawLine(
                            x - 1,
                            client.y,
                            x - 1,
                            client.y + client.height
                        );
                    }
                }
            }
        );
    }

    private static void installTableAlternatingRowBackground(
        final Table table,
        final ThemePalette palette
    ) {
        if (table == null || table.isDisposed()) {
            return;
        }
        if (Boolean.TRUE.equals(table.getData("tvrenamer.dark.altrows"))) {
            return;
        }
        table.setData("tvrenamer.dark.altrows", Boolean.TRUE);

        Listener refresher = new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (table.isDisposed()) {
                    return;
                }
                int count = table.getItemCount();
                for (int i = 0; i < count; i++) {
                    TableItem item = table.getItem(i);
                    if (item == null) {
                        continue;
                    }
                    if ((i % 2) == 1) {
                        item.setBackground(palette.getTableRowAlternate());
                    } else {
                        item.setBackground(palette.getControlBackground());
                    }
                    item.setForeground(palette.getControlForeground());
                }
            }
        };

        table.addListener(SWT.Paint, refresher);
        table.addListener(SWT.Resize, refresher);
        table.addListener(SWT.Selection, refresher);
    }

    private static void installButtonBorderPainter(
        final Button button,
        final ThemePalette palette
    ) {
        if (button == null || button.isDisposed()) {
            return;
        }

        // Don't double-install.
        if (
            Boolean.TRUE.equals(button.getData("tvrenamer.dark.buttonborder"))
        ) {
            return;
        }
        button.setData("tvrenamer.dark.buttonborder", Boolean.TRUE);

        // Native SWT buttons are OS rendered; border drawing isn't always honored.
        // We do a minimal overlay border as best-effort to avoid bright white edges.
        button.addPaintListener(
            new PaintListener() {
                @Override
                public void paintControl(PaintEvent e) {
                    if (button.isDisposed()) {
                        return;
                    }
                    Rectangle r = button.getBounds();
                    if (r.width <= 1 || r.height <= 1) {
                        return;
                    }
                    e.gc.setForeground(palette.getBorderColor());
                    e.gc.drawRectangle(0, 0, r.width - 1, r.height - 1);
                }
            }
        );
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
