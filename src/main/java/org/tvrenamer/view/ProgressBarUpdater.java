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

    /**
     * Constructs a ProgressBarUpdater for the given ResultsTable.
     *
     * Note: the bottom progress bar is driven by aggregate byte-based copy progress
     * (copy+delete operations only). This updater should NOT write file-count based
     * progress into the bottom bar, because it can cause "snap back" behavior.
     *
     * @param ui
     *    the ResultsTable that will use this ProgressBarUpdater
     */
    public ProgressBarUpdater(ResultsTable ui) {
        this.ui = ui;
        this.display = ui.getDisplay();
        this.taskItem = ui.getTaskItem();
        this.progressBar = ui.getProgressBar();

        if (taskItem != null && !taskItem.isDisposed()) {
            taskItem.setProgressState(SWT.NORMAL);
            taskItem.setOverlayImage(ItemState.RENAMING.getIcon());
        }
    }

    /**
     * Cleans up the task item and resets the bottom progress bar after the batch completes.
     */
    @Override
    public void finish() {
        if (display == null || display.isDisposed()) {
            return;
        }

        display.asyncExec(() -> {
            // Reset the bottom progress bar; aggregate progress will repopulate it for the next batch.
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
     * Update the Windows taskbar progress only.
     *
     * We intentionally do NOT update the bottom progress bar here, because it is controlled
     * by aggregate byte-based progress from per-file copy callbacks.
     */
    @Override
    public void setProgress(final int totalNumFiles, final int nRemaining) {
        if (display == null || display.isDisposed()) {
            return;
        }
        if (totalNumFiles <= 0) {
            return;
        }

        final int completed = Math.max(0, totalNumFiles - nRemaining);
        final float progress = (float) completed / (float) totalNumFiles;

        display.asyncExec(() -> {
            if (taskItem != null && !taskItem.isDisposed()) {
                taskItem.setProgress(Math.round(progress * 100));
            }
        });
    }
}
