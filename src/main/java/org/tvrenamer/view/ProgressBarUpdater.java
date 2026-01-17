package org.tvrenamer.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.TaskItem;
import org.tvrenamer.model.ProgressUpdater;

public class ProgressBarUpdater implements ProgressUpdater {

    private final ResultsTable ui;
    private final Display display;
    private final TaskItem taskItem;
    private final ProgressBar progressBar;

    // We use the progress bar as a file-count based progress indicator for move/copy operations
    // only when aggregate byte-based progress is not active.
    //
    // Note: aggregate byte-based progress is driven directly via ResultsTable.updateOverallCopyProgress(...)
    // from MoveObserver callbacks (copy+delete operations only).
    private final int barMax;

    /**
     * Constructs a ProgressBarUpdater for the given ResultsTable.
     *
     * @param ui
     *    the ResultsTable that will use this ProgressBarUpdater
     */
    public ProgressBarUpdater(ResultsTable ui) {
        this.ui = ui;
        this.display = ui.getDisplay();
        this.taskItem = ui.getTaskItem();
        this.progressBar = ui.getProgressBar();

        // progressBar may be null or disposed if UI is shutting down
        if (progressBar != null && !progressBar.isDisposed()) {
            // Use a fixed range for smooth updates.
            this.barMax = Math.max(1, progressBar.getMaximum());
            progressBar.setMaximum(this.barMax);
        } else {
            this.barMax = 0;
        }

        if (taskItem != null && !taskItem.isDisposed()) {
            taskItem.setProgressState(SWT.NORMAL);
            taskItem.setOverlayImage(ItemState.RENAMING.getIcon());
        }
    }

    /**
     * Cleans up the progress bar and the task item.
     * Resets the bottom bar after the batch completes.
     */
    @Override
    public void finish() {
        if (display == null || display.isDisposed()) {
            return;
        }

        display.asyncExec(() -> {
            if (progressBar != null && !progressBar.isDisposed()) {
                progressBar.setSelection(0);
            }
            if (taskItem != null && !taskItem.isDisposed()) {
                taskItem.setOverlayImage(null);
                taskItem.setProgressState(SWT.DEFAULT);
                taskItem.setProgress(0);
            }
            ui.finishAllMoves();
        });
    }

    /**
     * Updates the bottom progress bar and the Windows taskbar progress by file-count.
     *
     * This reflects move/copy batch progress only. Renames are excluded by design.
     *
     * @param totalNumFiles
     *            the total number of files to be moved during the duration
     *            of this progress bar
     * @param nRemaining
     *            the number of files left to be moved
     */
    @Override
    public void setProgress(final int totalNumFiles, final int nRemaining) {
        // If aggregate byte-based progress is active, do not overwrite the progress bar
        // with coarse file-count updates. Aggregate progress will update the bar directly.
        if (ui != null && ui.isAggregateCopyProgressActive()) {
            return;
        }

        if (display == null || display.isDisposed()) {
            return;
        }
        if (totalNumFiles <= 0) {
            return;
        }
        if (barMax <= 0) {
            return;
        }

        final int completed = Math.max(0, totalNumFiles - nRemaining);
        final float progress = (float) completed / (float) totalNumFiles;

        display.asyncExec(() -> {
            if (progressBar == null || progressBar.isDisposed()) {
                return;
            }

            progressBar.setSelection(Math.round(progress * barMax));

            if (taskItem != null && !taskItem.isDisposed()) {
                taskItem.setProgress(Math.round(progress * 100));
            }
        });
    }
}
