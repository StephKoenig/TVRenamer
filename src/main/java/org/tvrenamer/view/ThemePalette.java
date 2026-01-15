package org.tvrenamer.view;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.tvrenamer.model.ThemeMode;

/**
 * Encapsulates SWT colours that represent the active UI theme. The palette owns the colour
 * resources and must be disposed when the UI shuts down.
 */
final class ThemePalette {

    private final Display display;
    private final ThemeMode mode;
    private final List<Color> ownedColours = new ArrayList<>();
    private boolean disposed;

    private final Color shellBackground;
    private final Color shellForeground;
    private final Color controlBackground;
    private final Color controlForeground;
    private final Color borderColor;
    private final Color accentColor;
    private final Color tableHeaderBackground;
    private final Color tableHeaderForeground;
    private final Color tableRowAlternate;
    private final Color selectionBackground;
    private final Color selectionForeground;

    ThemePalette(Display display, ThemeMode mode) {
        this.display = display;
        this.mode = mode;
        if (mode == ThemeMode.DARK) {
            shellBackground = createColor(new RGB(32, 32, 32));
            shellForeground = createColor(new RGB(235, 235, 235));
            controlBackground = createColor(new RGB(45, 45, 45));
            controlForeground = createColor(new RGB(240, 240, 240));
            borderColor = createColor(new RGB(70, 70, 70));
            accentColor = createColor(new RGB(99, 153, 255));
            tableHeaderBackground = createColor(new RGB(55, 55, 55));
            tableHeaderForeground = controlForeground;

            // Increase zebra striping contrast in dark mode: alternate rows should be
            // noticeably different from the base table background, but still subtle.
            tableRowAlternate = createColor(new RGB(58, 58, 58));

            selectionBackground = createColor(new RGB(70, 110, 180));
            selectionForeground = controlForeground;
        } else {
            shellBackground = createColor(new RGB(245, 245, 245));
            shellForeground = createColor(new RGB(32, 32, 32));
            controlBackground = createColor(new RGB(255, 255, 255));
            controlForeground = createColor(new RGB(24, 24, 24));
            borderColor = createColor(new RGB(200, 200, 200));
            accentColor = createColor(new RGB(0, 120, 215));
            tableHeaderBackground = createColor(new RGB(232, 232, 232));
            tableHeaderForeground = controlForeground;
            tableRowAlternate = createColor(new RGB(248, 248, 248));
            selectionBackground = createColor(new RGB(0, 120, 215));
            selectionForeground = createColor(new RGB(255, 255, 255));
        }

        display.disposeExec(this::dispose);
    }

    ThemeMode getMode() {
        return mode;
    }

    boolean isDark() {
        return mode == ThemeMode.DARK;
    }

    Color getShellBackground() {
        return shellBackground;
    }

    Color getShellForeground() {
        return shellForeground;
    }

    Color getControlBackground() {
        return controlBackground;
    }

    Color getControlForeground() {
        return controlForeground;
    }

    Color getBorderColor() {
        return borderColor;
    }

    Color getAccentColor() {
        return accentColor;
    }

    Color getTableHeaderBackground() {
        return tableHeaderBackground;
    }

    Color getTableHeaderForeground() {
        return tableHeaderForeground;
    }

    Color getTableRowAlternate() {
        return tableRowAlternate;
    }

    Color getSelectionBackground() {
        return selectionBackground;
    }

    Color getSelectionForeground() {
        return selectionForeground;
    }

    /**
     * Apply the default control background/foreground to the supplied control and optionally
     * recurse into its children.
     *
     * @param control root control
     * @param recurse whether to recurse into child controls if composite
     */
    void apply(Control control, boolean recurse) {
        if (control == null || control.isDisposed()) {
            return;
        }
        control.setBackground(getControlBackground());
        control.setForeground(getControlForeground());
        if (recurse && control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                apply(child, true);
            }
        }
    }

    /**
     * Dispose of colours owned by this palette.
     */
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Color color : ownedColours) {
            if (color != null && !color.isDisposed()) {
                color.dispose();
            }
        }
        ownedColours.clear();
    }

    private Color createColor(RGB rgb) {
        Color color = new Color(display, rgb);
        ownedColours.add(color);
        return color;
    }
}
