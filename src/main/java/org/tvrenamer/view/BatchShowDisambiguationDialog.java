package org.tvrenamer.view;

import static org.tvrenamer.model.util.Constants.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.tvrenamer.model.ShowOption;
import org.tvrenamer.model.ShowStore;

/**
 * Modal dialog used to resolve all ambiguous show lookups in a single batch.
 *
 * <p>Left side: list of ambiguous extracted show names (one row per provider query string).
 * Right side: show an example filename (bleeds off; tooltip contains the full name)
 * and up to 5 provider candidates with metadata (name, year, id, aliases).
 *
 * <p>Returns a map of queryString -> chosen provider series id for any rows the user resolved.
 * If the user cancels, returns null.
 */
public final class BatchShowDisambiguationDialog extends Dialog {

    private static final Logger logger = Logger.getLogger(
        BatchShowDisambiguationDialog.class.getName()
    );

    private static final int MAX_OPTIONS_PER_SHOW = 5;

    private final Shell parent;
    private final Map<String, ShowStore.PendingDisambiguation> pending;

    private Shell dialogShell;
    private ThemePalette themePalette;

    private Table leftTable;
    private Table rightCandidatesTable;

    private Label exampleFileValueLabel;
    private Label showNameValueLabel;

    // queryString -> chosenId
    private final Map<String, String> selections = new LinkedHashMap<>();

    private Button okButton;

    /**
     * Create a new batch dialog.
     *
     * @param parent parent shell
     * @param pendingDisambiguations map of queryString -> pending disambiguation
     */
    public BatchShowDisambiguationDialog(
        final Shell parent,
        final Map<
            String,
            ShowStore.PendingDisambiguation
        > pendingDisambiguations
    ) {
        super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        this.parent = parent;
        this.pending = (pendingDisambiguations == null)
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(pendingDisambiguations);
    }

    /**
     * Open the dialog and block until user closes.
     *
     * @return map of queryString -> chosen provider series id, or null if cancelled
     */
    public Map<String, String> open() {
        if (pending.isEmpty()) {
            return new LinkedHashMap<>();
        }

        dialogShell = new Shell(parent, getStyle());
        dialogShell.setText("Select Shows");

        // Match main window icon (best-effort).
        // We intentionally load a fresh Image here; SWT Shell takes ownership for display purposes.
        dialogShell.setImage(
            UIStarter.readImageFromPath(APPLICATION_ICON_PATH)
        );

        // Apply theme palette so controls/tables inherit dark mode styling.
        themePalette = ThemeManager.createPalette(dialogShell.getDisplay());
        ThemeManager.applyPalette(dialogShell, themePalette);

        // Treat closing the window via the title-bar X as Cancel.
        dialogShell.addListener(SWT.Close, e -> {
            // Mark as cancelled so caller leaves pending queued, and mark that the close button was used
            // so the caller can avoid immediate re-open loops.
            cancelled = true;
            closedViaWindowX = true;
            selections.clear();
        });

        createContents(dialogShell);

        dialogShell.setMinimumSize(900, 370);
        dialogShell.pack();
        dialogShell.open();

        Display display = parent.getDisplay();
        while (!dialogShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        // If dialogShell closed by cancel path or by window X, we return null.
        if (cancelled) {
            return null;
        }

        return new LinkedHashMap<>(selections);
    }

    private boolean cancelled = false;

    // When the user closes the dialog via the window close button (X), treat it as Cancel.
    // This prevents the dialog from immediately reopening due to pending disambiguation callbacks.
    private boolean closedViaWindowX = false;

    private void createContents(final Shell shell) {
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 12;
        layout.marginHeight = 12;
        layout.verticalSpacing = 10;
        shell.setLayout(layout);

        Label header = new Label(shell, SWT.WRAP);
        header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        header.setText(
            "Some shows matched multiple results. Showing first " +
                MAX_OPTIONS_PER_SHOW +
                " results per show. Please choose the correct show for each."
        );

        SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite left = new Composite(sash, SWT.NONE);
        left.setLayout(new GridLayout(1, false));

        Composite right = new Composite(sash, SWT.NONE);
        right.setLayout(new GridLayout(1, false));

        sash.setWeights(new int[] { 35, 65 });

        createLeftPane(left);
        createRightPane(right);

        createButtons(shell);

        // Ensure children created inside panes inherit palette (tables, labels, buttons).
        ThemeManager.applyPalette(shell, themePalette);
        ThemeManager.applyPalette(left, themePalette);
        ThemeManager.applyPalette(right, themePalette);

        populateLeftTable();

        // Select first unresolved row by default
        if (leftTable.getItemCount() > 0) {
            leftTable.setSelection(0);
            onLeftSelectionChanged();
        }

        updateOkEnabled();
    }

    private void createLeftPane(final Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText("Ambiguous Shows");

        leftTable = new Table(
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE
        );
        leftTable.setHeaderVisible(true);
        leftTable.setLinesVisible(true);

        // Theme table + header for dark mode (match main table behavior).
        leftTable.setBackground(themePalette.getControlBackground());
        leftTable.setForeground(themePalette.getControlForeground());
        leftTable.setHeaderBackground(themePalette.getTableHeaderBackground());
        leftTable.setHeaderForeground(themePalette.getTableHeaderForeground());

        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.minimumHeight = 100;
        leftTable.setLayoutData(gd);

        TableColumn colShow = new TableColumn(leftTable, SWT.LEFT);
        colShow.setText("Extracted show");

        TableColumn colFiles = new TableColumn(leftTable, SWT.LEFT);
        colFiles.setText("Files");

        TableColumn colStatus = new TableColumn(leftTable, SWT.LEFT);
        colStatus.setText("Status");

        leftTable.addListener(SWT.Selection, e -> onLeftSelectionChanged());
    }

    private void createRightPane(final Composite parent) {
        Composite meta = new Composite(parent, SWT.NONE);
        meta.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout metaLayout = new GridLayout(2, false);
        metaLayout.marginWidth = 0;
        metaLayout.marginHeight = 0;
        metaLayout.horizontalSpacing = 8;
        meta.setLayout(metaLayout);

        Label showNameLabel = new Label(meta, SWT.NONE);
        showNameLabel.setText("Extracted show:");

        showNameValueLabel = new Label(meta, SWT.NONE);
        showNameValueLabel.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false)
        );
        showNameValueLabel.setText("");

        Label exampleFileLabel = new Label(meta, SWT.NONE);
        exampleFileLabel.setText("Example file:");

        exampleFileValueLabel = new Label(meta, SWT.NONE);
        exampleFileValueLabel.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false)
        );
        exampleFileValueLabel.setText("");
        exampleFileValueLabel.setToolTipText("");

        Label candidatesLabel = new Label(parent, SWT.NONE);
        candidatesLabel.setText("Choose a match:");

        rightCandidatesTable = new Table(
            parent,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE
        );
        rightCandidatesTable.setHeaderVisible(true);
        rightCandidatesTable.setLinesVisible(true);

        // Theme table + header for dark mode (match main table behavior).
        rightCandidatesTable.setBackground(themePalette.getControlBackground());
        rightCandidatesTable.setForeground(themePalette.getControlForeground());
        rightCandidatesTable.setHeaderBackground(
            themePalette.getTableHeaderBackground()
        );
        rightCandidatesTable.setHeaderForeground(
            themePalette.getTableHeaderForeground()
        );

        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.minimumHeight = 100;
        rightCandidatesTable.setLayoutData(tableData);

        TableColumn colName = new TableColumn(rightCandidatesTable, SWT.LEFT);
        colName.setText("Name");

        TableColumn colYear = new TableColumn(rightCandidatesTable, SWT.LEFT);
        colYear.setText("Year");

        TableColumn colId = new TableColumn(rightCandidatesTable, SWT.LEFT);
        colId.setText("ID");

        TableColumn colAliases = new TableColumn(
            rightCandidatesTable,
            SWT.LEFT
        );
        colAliases.setText("Aliases");

        // Cap the aliases column width: aliases can be extremely long.
        // Prefer horizontal scrolling over an overly wide table.
        colAliases.setWidth(260);

        // Persist selection on single-click as well (so users can click a candidate, then click OK).
        rightCandidatesTable.addListener(SWT.Selection, e ->
            applyCurrentSelectionOnly()
        );

        // Double-click (default selection) applies selection and advances.
        rightCandidatesTable.addListener(SWT.DefaultSelection, e ->
            applyCurrentSelectionAndAdvance()
        );
    }

    private void createButtons(final Composite parent) {
        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        GridLayout gl = new GridLayout(2, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 8;
        buttons.setLayout(gl);

        Button cancelButton = new Button(buttons, SWT.PUSH);
        cancelButton.setText(CANCEL_LABEL);
        cancelButton.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false)
        );
        cancelButton.addListener(SWT.Selection, e -> {
            cancelled = true;
            closedViaWindowX = false;
            selections.clear();
            close();
        });

        okButton = new Button(buttons, SWT.PUSH);
        okButton.setText("OK");
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        okButton.addListener(SWT.Selection, e -> {
            // Only allow OK when everything is resolved
            if (!allResolved()) {
                return;
            }
            cancelled = false;
            close();
        });

        dialogShell.setDefaultButton(okButton);
    }

    private void close() {
        if (dialogShell != null && !dialogShell.isDisposed()) {
            dialogShell.close();
        }
    }

    private void populateLeftTable() {
        leftTable.removeAll();

        for (Map.Entry<
            String,
            ShowStore.PendingDisambiguation
        > entry : pending.entrySet()) {
            String queryString = entry.getKey();
            ShowStore.PendingDisambiguation pd = entry.getValue();

            TableItem item = new TableItem(leftTable, SWT.NONE);
            item.setData(queryString);

            String extracted = (pd == null) ? "" : safe(pd.extractedShowName);
            String exampleFile = (pd == null) ? "" : safe(pd.exampleFileName);

            String filesCol = exampleFile.isEmpty() ? "" : "1 file";
            String statusCol = selections.containsKey(queryString)
                ? "Selected"
                : "Not selected";

            item.setText(new String[] { extracted, filesCol, statusCol });
            // TableItem does not support tooltips consistently across SWT versions;
            // keep the display concise and rely on selection context on the right pane.
        }

        for (TableColumn c : leftTable.getColumns()) {
            c.pack();
        }
    }

    private void onLeftSelectionChanged() {
        int idx = leftTable.getSelectionIndex();
        if (idx < 0) {
            clearRightPane();
            return;
        }

        TableItem selectedItem = leftTable.getItem(idx);
        Object data = selectedItem.getData();
        if (!(data instanceof String)) {
            clearRightPane();
            return;
        }

        String queryString = (String) data;
        ShowStore.PendingDisambiguation pd = pending.get(queryString);
        if (pd == null) {
            clearRightPane();
            return;
        }

        showNameValueLabel.setText(safe(pd.extractedShowName));

        String exampleFile = safe(pd.exampleFileName);
        exampleFileValueLabel.setText(exampleFile);
        exampleFileValueLabel.setToolTipText(exampleFile);

        populateCandidates(pd.options);

        // If already selected for this query, preselect it in candidate table
        String chosenId = selections.get(queryString);
        if (chosenId != null && !chosenId.isBlank()) {
            selectCandidateById(chosenId);
        } else if (rightCandidatesTable.getItemCount() > 0) {
            rightCandidatesTable.setSelection(0);
        }

        rightCandidatesTable.getParent().layout(true, true);
    }

    private void clearRightPane() {
        showNameValueLabel.setText("");
        exampleFileValueLabel.setText("");
        exampleFileValueLabel.setToolTipText("");
        rightCandidatesTable.removeAll();
    }

    private void populateCandidates(final List<ShowOption> options) {
        rightCandidatesTable.removeAll();

        List<ShowOption> safeOptions = (options == null)
            ? new ArrayList<>()
            : new ArrayList<>(options);
        if (safeOptions.size() > MAX_OPTIONS_PER_SHOW) {
            safeOptions = safeOptions.subList(0, MAX_OPTIONS_PER_SHOW);
        }

        for (ShowOption opt : safeOptions) {
            if (opt == null) {
                continue;
            }

            TableItem item = new TableItem(rightCandidatesTable, SWT.NONE);
            item.setData(opt);

            String name = safe(opt.getName());
            String year = "";
            try {
                Integer y = opt.getFirstAiredYear();
                if (y != null) {
                    year = y.toString();
                }
            } catch (Exception ignored) {
                // best-effort
            }

            String id = safe(opt.getIdString());

            String aliases = "";
            try {
                List<String> aliasNames = opt.getAliasNames();
                if (aliasNames != null && !aliasNames.isEmpty()) {
                    aliases = String.join(", ", aliasNames);
                }
            } catch (Exception ignored) {
                // best-effort
            }

            item.setText(new String[] { name, year, id, aliases });
        }

        for (TableColumn c : rightCandidatesTable.getColumns()) {
            c.pack();
        }

        // Re-apply a max width for Aliases after pack(), since aliases can be very long.
        // Column order: Name, Year, ID, Aliases
        if (rightCandidatesTable.getColumnCount() >= 4) {
            TableColumn aliasesCol = rightCandidatesTable.getColumn(3);
            int maxAliasesWidth = 260;
            if (aliasesCol.getWidth() > maxAliasesWidth) {
                aliasesCol.setWidth(maxAliasesWidth);
            }
        }
    }

    private void selectCandidateById(final String id) {
        if (id == null) {
            return;
        }
        for (int i = 0; i < rightCandidatesTable.getItemCount(); i++) {
            TableItem ti = rightCandidatesTable.getItem(i);
            Object data = ti.getData();
            if (data instanceof ShowOption) {
                ShowOption opt = (ShowOption) data;
                if (id.equals(opt.getIdString())) {
                    rightCandidatesTable.setSelection(i);
                    return;
                }
            }
        }
    }

    private void applyCurrentSelectionAndAdvance() {
        if (!applyCurrentSelectionOnly()) {
            return;
        }

        int leftIdx = leftTable.getSelectionIndex();
        if (leftIdx < 0) {
            return;
        }

        // Move to next unresolved row if any
        int next = findNextUnresolvedIndex(leftIdx + 1);
        if (next < 0) {
            next = findNextUnresolvedIndex(0);
        }
        if (next >= 0) {
            leftTable.setSelection(next);
            onLeftSelectionChanged();
        }

        updateOkEnabled();
    }

    private boolean applyCurrentSelectionOnly() {
        int leftIdx = leftTable.getSelectionIndex();
        if (leftIdx < 0) {
            return false;
        }
        TableItem leftItem = leftTable.getItem(leftIdx);
        Object data = leftItem.getData();
        if (!(data instanceof String)) {
            return false;
        }
        String queryString = (String) data;

        int candIdx = rightCandidatesTable.getSelectionIndex();
        if (candIdx < 0) {
            return false;
        }
        TableItem candItem = rightCandidatesTable.getItem(candIdx);
        Object candData = candItem.getData();
        if (!(candData instanceof ShowOption)) {
            return false;
        }
        ShowOption chosen = (ShowOption) candData;

        selections.put(queryString, chosen.getIdString());

        // Update left status cell
        leftItem.setText(2, "Selected");

        updateOkEnabled();
        return true;
    }

    private int findNextUnresolvedIndex(int start) {
        for (int i = start; i < leftTable.getItemCount(); i++) {
            TableItem item = leftTable.getItem(i);
            Object data = item.getData();
            if (data instanceof String) {
                String queryString = (String) data;
                if (!selections.containsKey(queryString)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean allResolved() {
        for (Map.Entry<
            String,
            ShowStore.PendingDisambiguation
        > entry : pending.entrySet()) {
            String queryString = entry.getKey();
            if (queryString == null || queryString.isBlank()) {
                continue;
            }
            if (!selections.containsKey(queryString)) {
                return false;
            }
        }
        return true;
    }

    private void updateOkEnabled() {
        if (okButton == null || okButton.isDisposed()) {
            return;
        }
        okButton.setEnabled(allResolved());
    }

    private static String safe(final String s) {
        return (s == null) ? "" : s;
    }
}
