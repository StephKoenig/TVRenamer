package org.tvrenamer.view;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Shared helper for common SWT dialog operations.
 *
 * <p>Provides the modal event loop that all custom dialogs use to block
 * until dismissed, eliminating the identical 5-line pattern from each dialog.
 */
public final class DialogHelper {

    private DialogHelper() {
        // utility class
    }

    /**
     * Run a modal event loop that blocks until the given shell is disposed.
     *
     * <p>This replaces the identical {@code while (!shell.isDisposed()) ...}
     * boilerplate found in every custom dialog.
     *
     * @param dialogShell the dialog shell to run the loop for
     */
    public static void runModalLoop(final Shell dialogShell) {
        Display display = dialogShell.getDisplay();
        while (!dialogShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }
}
