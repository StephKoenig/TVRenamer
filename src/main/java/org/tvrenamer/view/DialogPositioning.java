package org.tvrenamer.view;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

/**
 * Shared helper for positioning dialog shells relative to their parent shell.
 *
 * <p>Goal: avoid OS-random placement for app-owned SWT Shell dialogs by placing them
 * centered over the parent (main window), with a small offset so the user can still
 * see the parent window edges.</p>
 *
 * <p>Notes:
 * <ul>
 *   <li>This is intended for custom dialogs built with {@link Shell} (e.g. Preferences/About/Select Shows).</li>
 *   <li>Native dialogs like {@code MessageBox}, {@code FileDialog}, {@code DirectoryDialog} are OS-managed and are
 *       not reliably positionable.</li>
 * </ul>
 * </p>
 */
public final class DialogPositioning {

    private DialogPositioning() {
        // utility
    }

    /**
     * Position {@code dialogShell} centered over {@code parentShell} with a small default offset.
     *
     * <p>Call this after the dialog has been laid out (e.g. after {@code pack()}) and before {@code open()}.</p>
     */
    public static void positionDialog(final Shell dialogShell, final Shell parentShell) {
        positionDialog(dialogShell, parentShell, 24, 24);
    }

    /**
     * Position {@code dialogShell} centered over {@code parentShell} with an explicit offset.
     *
     * <p>Call this after the dialog has been laid out (e.g. after {@code pack()}) and before {@code open()}.</p>
     *
     * @param dialogShell the dialog shell to position
     * @param parentShell the parent shell (typically the main application window); may be null
     * @param offsetX x offset applied after centering (positive moves right)
     * @param offsetY y offset applied after centering (positive moves down)
     */
    public static void positionDialog(
        final Shell dialogShell,
        final Shell parentShell,
        final int offsetX,
        final int offsetY
    ) {
        if (dialogShell == null || dialogShell.isDisposed()) {
            return;
        }

        Rectangle dialogBounds = safeBounds(dialogShell);
        if (dialogBounds == null) {
            return;
        }

        Rectangle anchorBounds = null;
        if (parentShell != null && !parentShell.isDisposed()) {
            anchorBounds = safeBounds(parentShell);
        }

        // Prefer the parent's monitor for clamping so the dialog stays on the
        // same screen as the main window (the dialog shell hasn't been positioned
        // yet, so its default monitor may be the primary display).
        Monitor mon = null;
        if (parentShell != null && !parentShell.isDisposed()) {
            try {
                mon = parentShell.getMonitor();
            } catch (Throwable ignored) {
                mon = null;
            }
        }
        if (mon == null) {
            try {
                mon = dialogShell.getMonitor();
            } catch (Throwable ignored) {
                mon = null;
            }
        }
        Rectangle workArea = (mon != null) ? safeWorkArea(mon) : null;

        // Fallback to "center on monitor" if parent bounds unavailable.
        int x;
        int y;

        if (anchorBounds != null) {
            x = anchorBounds.x + ((anchorBounds.width - dialogBounds.width) / 2) + offsetX;
            y = anchorBounds.y + ((anchorBounds.height - dialogBounds.height) / 2) + offsetY;
        } else if (workArea != null) {
            x = workArea.x + ((workArea.width - dialogBounds.width) / 2) + offsetX;
            y = workArea.y + ((workArea.height - dialogBounds.height) / 2) + offsetY;
        } else {
            // Last resort: leave as-is.
            return;
        }

        // Clamp to monitor work area so we don't place the dialog off-screen.
        if (workArea != null) {
            x = clamp(x, workArea.x, workArea.x + Math.max(0, workArea.width - dialogBounds.width));
            y = clamp(y, workArea.y, workArea.y + Math.max(0, workArea.height - dialogBounds.height));
        }

        dialogShell.setLocation(new Point(x, y));
    }

    private static Rectangle safeBounds(final Shell s) {
        try {
            Rectangle b = s.getBounds();
            if (b == null) {
                return null;
            }
            // Ensure non-negative sizes for clamping math.
            if (b.width < 0) {
                b.width = 0;
            }
            if (b.height < 0) {
                b.height = 0;
            }
            return b;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rectangle safeWorkArea(final Monitor m) {
        try {
            Rectangle b = m.getClientArea();
            if (b == null) {
                return null;
            }
            if (b.width < 0) {
                b.width = 0;
            }
            if (b.height < 0) {
                b.height = 0;
            }
            return b;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int clamp(final int v, final int min, final int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
