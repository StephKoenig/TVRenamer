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
    private final int barSize;

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
            this.barSize = progressBar.getMaximum();
        } else {
            this.barSize = 0;
        }

        if (taskItem != null && !taskItem.isDisposed()) {
            taskItem.setProgressState(SWT.NORMAL);
            taskItem.setOverlayImage(ItemState.RENAMING.getIcon());
        }
    }

    /**
     * Cleans up the progress bar and the task item
     *
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
            }
            ui.finishAllMoves();
        });
    }

    /**
     * Updates the progress bar and the task item
     *
     * @param totalNumFiles
     *            the total number of files to be moved during the duration
     *            of this progress bar
     * @param nRemaining
     *            the number of files left to be moved
     */
    @Override
    public void setProgress(final int totalNumFiles, final int nRemaining) {
        if (display == null || display.isDisposed()) {
            return;
        }
        if (totalNumFiles <= 0) {
            return;
        }
        if (barSize <= 0) {
            return;
        }

        final float progress =
            (float) (totalNumFiles - nRemaining) / totalNumFiles;
        display.asyncExec(() -> {
            if (progressBar == null || progressBar.isDisposed()) {
                return;
            }
            progressBar.setSelection(Math.round(progress * barSize));

            if (taskItem != null) {
                if (taskItem.isDisposed()) {
                    return;
                }
                taskItem.setProgress(Math.round(progress * 100));
            }
        });
    }
}
