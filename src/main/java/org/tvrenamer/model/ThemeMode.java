package org.tvrenamer.model;

import java.util.Locale;

/**
 * Represents the user-selectable theme modes for the TVRenamer UI.
 *
 * <p>
 * LIGHT is the historic/default look and feel. DARK applies a high-contrast
 * palette. AUTO delegates to the operating system preference, falling back
 * to light if the platform does not expose a theme setting.
 * </p>
 */
public enum ThemeMode {
    LIGHT("Light"),
    DARK("Dark"),
    AUTO("Auto");

    private final String label;

    ThemeMode(String label) {
        this.label = label;
    }

    /**
     * @return human-readable label suitable for UI display.
     */
    @Override
    public String toString() {
        return label;
    }

    /**
     * Parse a string value (case-insensitive) and return a matching ThemeMode.
     *
     * @param value textual representation (e.g., "dark")
     * @return matching ThemeMode, or null if no match is found
     */
    public static ThemeMode fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        for (ThemeMode mode : values()) {
            if (mode.name().equals(upper) || mode.label.toUpperCase(Locale.ROOT).equals(upper)) {
                return mode;
            }
        }
        return null;
    }
}
