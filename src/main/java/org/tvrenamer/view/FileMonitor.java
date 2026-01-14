package org.tvrenamer.view;

import java.text.NumberFormat;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.MoveObserver;

public class FileMonitor implements MoveObserver {

    private final NumberFormat format = NumberFormat.getPercentInstance();

    private final ResultsTable ui;
    private final TableItem item;
    private final Display display;
    private Label label = null;
    private long maximum = 0;
    private int loopCount = 0;

    /**
     * Creates the monitor, with the label and the display.
     *
     * @param ui - the ResultsTable instance
     * @param item - the TableItem to monitor
     */
    public FileMonitor(ResultsTable ui, TableItem item) {
        this.ui = ui;
        this.item = item;
        this.display = ui.getDisplay();
        format.setMaximumFractionDigits(1);
    }

    /**
     * Set the maximum value.
     *
     * @param max the new maximum value
     */
    @Override
    public void initializeProgress(final long max) {
        if (display == null || display.isDisposed()) {
            return;
        }
        if (item == null || item.isDisposed()) {
            return;
        }
        display.syncExec(() -> {
            if (item.isDisposed()) {
                return;
            }
            label = ui.getProgressLabel(item);
        });
        maximum = Math.max(0, max);
        setProgressValue(0);
    }

    /**
     * Update the progress value.
     *
     * @param value the new value
     */
    @Override
    public void setProgressValue(final long value) {
        if (display == null || display.isDisposed()) {
            return;
        }
        if (label == null || label.isDisposed()) {
            return;
        }
        if (maximum <= 0) {
            return;
        }
        if (loopCount++ % 500 == 0) {
            display.asyncExec(() -> {
                if (display.isDisposed()) {
                    return;
                }
                if (label == null || label.isDisposed()) {
                    return;
                }
                label.setText(format.format((double) value / maximum));
            });
        }
    }

    /**
     * Update the status label.
     *
     * @param status the new status label
     */
    @Override
    public void setProgressStatus(final String status) {
        if (display == null || display.isDisposed()) {
            return;
        }
        if (label == null || label.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (display.isDisposed()) {
                return;
            }
            if (label == null || label.isDisposed()) {
                return;
            }
            label.setToolTipText(status);
        });
    }

    /**
     * Dispose of the label.  We need to do this whether the label was used or not.
     */
    @Override
    public void finishProgress(final FileEpisode episode) {
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (display.isDisposed()) {
                return;
            }
            if (label != null && !label.isDisposed()) {
                label.dispose();
            }
            if (item == null || item.isDisposed()) {
                return;
            }
            ui.finishMove(item, episode);
        });
    }
}
