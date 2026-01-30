package org.tvrenamer.view;

import static org.tvrenamer.model.util.Constants.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * Modal dialog to confirm deletion of duplicate video files found after moves.
 *
 * <p>Displays a list of duplicate files with checkboxes. Files are checked by default
 * (meaning they will be deleted). Users can uncheck files they want to keep.
 *
 * <p>Returns the list of files to delete when OK is clicked, or null if cancelled.
 */
public final class DuplicateCleanupDialog extends Dialog {

    private static final String TITLE = "Duplicate Files Found";
    private static final int MIN_WIDTH = 600;
    private static final int MIN_HEIGHT = 300;

    private final Shell parent;
    private final List<Path> duplicates;

    private Shell dialogShell;
    private Table table;
    private List<Path> result = null;

    /**
     * Create a new duplicate cleanup dialog.
     *
     * @param parent parent shell
     * @param duplicates list of duplicate file paths to display
     */
    public DuplicateCleanupDialog(final Shell parent, final List<Path> duplicates) {
        super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        this.parent = parent;
        this.duplicates = (duplicates == null) ? new ArrayList<>() : new ArrayList<>(duplicates);
    }

    /**
     * Open the dialog and block until user closes.
     *
     * @return list of files to delete, or null if cancelled
     */
    public List<Path> open() {
        if (duplicates.isEmpty()) {
            return new ArrayList<>();
        }

        dialogShell = new Shell(parent, getStyle());
        dialogShell.setText(TITLE);
        dialogShell.setMinimumSize(MIN_WIDTH, MIN_HEIGHT);

        // Match main window icon.
        dialogShell.setImage(UIStarter.readImageFromPath(APPLICATION_ICON_PATH));

        // Apply theme palette for dark mode support.
        ThemePalette themePalette = ThemeManager.createPalette(dialogShell.getDisplay());
        ThemeManager.applyPalette(dialogShell, themePalette);

        createContents(themePalette);

        dialogShell.pack();
        dialogShell.setSize(
            Math.max(dialogShell.getSize().x, MIN_WIDTH),
            Math.max(dialogShell.getSize().y, MIN_HEIGHT)
        );

        // Center on parent.
        int parentX = parent.getBounds().x;
        int parentY = parent.getBounds().y;
        int parentWidth = parent.getBounds().width;
        int parentHeight = parent.getBounds().height;
        int dialogWidth = dialogShell.getSize().x;
        int dialogHeight = dialogShell.getSize().y;
        dialogShell.setLocation(
            parentX + (parentWidth - dialogWidth) / 2,
            parentY + (parentHeight - dialogHeight) / 2
        );

        dialogShell.open();

        // Event loop.
        while (!dialogShell.isDisposed()) {
            if (!dialogShell.getDisplay().readAndDispatch()) {
                dialogShell.getDisplay().sleep();
            }
        }

        return result;
    }

    private void createContents(ThemePalette themePalette) {
        dialogShell.setLayout(new GridLayout(1, false));

        // Instruction label.
        Label instructionLabel = new Label(dialogShell, SWT.WRAP);
        instructionLabel.setText(
            "The following duplicate video files were found in the destination folder(s). " +
            "Checked files will be deleted. Uncheck any files you want to keep."
        );
        GridData instructionData = new GridData(SWT.FILL, SWT.TOP, true, false);
        instructionData.widthHint = MIN_WIDTH - 40;
        instructionLabel.setLayoutData(instructionData);

        // Table with checkboxes.
        table = new Table(dialogShell, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 200;
        table.setLayoutData(tableData);

        // Apply theme to table.
        table.setBackground(themePalette.getControlBackground());
        table.setForeground(themePalette.getControlForeground());
        table.setHeaderBackground(themePalette.getTableHeaderBackground());
        table.setHeaderForeground(themePalette.getTableHeaderForeground());

        // Columns: checkbox (implicit), filename, folder.
        TableColumn filenameColumn = new TableColumn(table, SWT.NONE);
        filenameColumn.setText("Filename");
        filenameColumn.setWidth(250);

        TableColumn folderColumn = new TableColumn(table, SWT.NONE);
        folderColumn.setText("Folder");
        folderColumn.setWidth(300);

        // Populate table.
        for (Path path : duplicates) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setChecked(true); // Default to delete.
            item.setData(path);

            String filename = (path.getFileName() != null)
                ? path.getFileName().toString()
                : path.toString();
            String folder = (path.getParent() != null)
                ? path.getParent().toString()
                : "";

            item.setText(0, filename);
            item.setText(1, folder);
        }

        // Summary label.
        Label summaryLabel = new Label(dialogShell, SWT.NONE);
        summaryLabel.setText(duplicates.size() + " duplicate file(s) found");
        summaryLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Button composite.
        Composite buttonComposite = new Composite(dialogShell, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(4, false));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ThemeManager.applyPalette(buttonComposite, themePalette);

        // Select All button.
        Button selectAllButton = new Button(buttonComposite, SWT.PUSH);
        selectAllButton.setText("Select All");
        selectAllButton.addListener(SWT.Selection, e -> {
            for (TableItem item : table.getItems()) {
                item.setChecked(true);
            }
        });

        // Select None button.
        Button selectNoneButton = new Button(buttonComposite, SWT.PUSH);
        selectNoneButton.setText("Select None");
        selectNoneButton.addListener(SWT.Selection, e -> {
            for (TableItem item : table.getItems()) {
                item.setChecked(false);
            }
        });

        // Spacer.
        Label spacer = new Label(buttonComposite, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Delete button.
        Button deleteButton = new Button(buttonComposite, SWT.PUSH);
        deleteButton.setText("Delete Selected");
        deleteButton.addListener(SWT.Selection, e -> {
            result = collectCheckedPaths();
            dialogShell.close();
        });

        // Cancel button.
        Button cancelButton = new Button(buttonComposite, SWT.PUSH);
        cancelButton.setText(CANCEL_LABEL);
        cancelButton.addListener(SWT.Selection, e -> {
            result = null;
            dialogShell.close();
        });

        // Default button.
        dialogShell.setDefaultButton(deleteButton);
    }

    private List<Path> collectCheckedPaths() {
        List<Path> checked = new ArrayList<>();
        for (TableItem item : table.getItems()) {
            if (item.getChecked()) {
                Object data = item.getData();
                if (data instanceof Path path) {
                    checked.add(path);
                }
            }
        }
        return checked;
    }
}
