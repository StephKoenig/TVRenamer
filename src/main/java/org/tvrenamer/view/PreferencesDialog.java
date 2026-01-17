package org.tvrenamer.view;

import static org.tvrenamer.model.ReplacementToken.*;
import static org.tvrenamer.model.util.Constants.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.tvrenamer.controller.util.FileUtilities;
import org.tvrenamer.controller.util.StringUtils;
import org.tvrenamer.model.ReplacementToken;
import org.tvrenamer.model.ThemeMode;
import org.tvrenamer.model.UserPreferences;

class PreferencesDialog extends Dialog {

    private static final Logger logger = Logger.getLogger(
        PreferencesDialog.class.getName()
    );
    private static final UserPreferences prefs = UserPreferences.getInstance();

    private static final int DND_OPERATIONS = DND.DROP_MOVE;
    private static final char DOUBLE_QUOTE = '"';

    private static class PreferencesDragSourceListener
        implements DragSourceListener
    {

        private final List sourceList;

        public PreferencesDragSourceListener(List sourceList) {
            this.sourceList = sourceList;
        }

        @Override
        public void dragStart(DragSourceEvent event) {
            if (sourceList.getSelectionIndex() != 0) {
                event.doit = true;
            }
        }

        @Override
        public void dragSetData(DragSourceEvent event) {
            String listEntry = sourceList.getItem(
                sourceList.getSelectionIndex()
            );
            String token;

            Pattern patt = Pattern.compile(
                REPLACEMENT_OPTIONS_LIST_ENTRY_REGEX
            );
            Matcher tokenMatcher = patt.matcher(listEntry);
            if (tokenMatcher.matches()) {
                token = tokenMatcher.group(1);
                event.data = token;
            }
        }

        @Override
        public void dragFinished(DragSourceEvent event) {
            // no-op
        }
    }

    private static class PreferencesDropTargetListener
        implements DropTargetListener
    {

        private final Text targetText;

        public PreferencesDropTargetListener(Text targetText) {
            this.targetText = targetText;
        }

        @Override
        public void drop(DropTargetEvent event) {
            String data = (String) event.data;
            if (data == null) {
                return;
            }

            // Insert at caret/selection position (not just append).
            // This makes drag/drop behave like a normal text editor insertion.
            int selectionStart;
            int selectionEnd;
            try {
                org.eclipse.swt.graphics.Point sel = targetText.getSelection();
                selectionStart = sel.x;
                selectionEnd = sel.y;
            } catch (Exception ignored) {
                // Best-effort fallback: append if we can't read selection.
                targetText.append(data);
                return;
            }

            String current = targetText.getText();
            if (current == null) {
                current = "";
            }

            // Clamp defensively
            if (selectionStart < 0) {
                selectionStart = 0;
            }
            if (selectionEnd < selectionStart) {
                selectionEnd = selectionStart;
            }
            if (selectionStart > current.length()) {
                selectionStart = current.length();
            }
            if (selectionEnd > current.length()) {
                selectionEnd = current.length();
            }

            String before = current.substring(0, selectionStart);
            String after = current.substring(selectionEnd);

            String newValue = before + data + after;
            targetText.setText(newValue);

            // Move caret to just after inserted text
            int newCaret = selectionStart + data.length();
            targetText.setSelection(newCaret);
        }

        @Override
        public void dragEnter(DropTargetEvent event) {
            // no-op
        }

        @Override
        public void dragLeave(DropTargetEvent event) {
            // no-op
        }

        @Override
        public void dragOperationChanged(DropTargetEvent event) {
            // no-op
        }

        @Override
        public void dragOver(DropTargetEvent event) {
            // no-op
        }

        @Override
        public void dropAccept(DropTargetEvent event) {
            // no-op
        }
    }

    // The controls to save
    private Button moveSelectedCheckbox;
    private Button renameSelectedCheckbox;
    private Text destDirText;
    private Button destDirButton;
    private Text seasonPrefixText;
    private Button seasonPrefixLeadingZeroCheckbox;
    private Text replacementStringText;
    private Text ignoreWordsText;
    private Button checkForUpdatesCheckbox;
    private Button recurseFoldersCheckbox;
    private Button rmdirEmptyCheckbox;
    private Button deleteRowsCheckbox;

    // If checked, set moved/renamed files' modification time to "now".
    // If unchecked (default), preserve original modification time.
    private Button setMtimeToNowCheckbox;
    private Combo themeModeCombo;
    private Button preferDvdOrderCheckbox;
    private ThemePalette themePalette;

    // Overrides (Show name -> Show name)
    private Text overridesFromText;
    private Text overridesToText;
    private List overridesList;

    private TabFolder tabFolder;
    private Shell preferencesShell;

    private final Shell parent;
    private final StatusLabel statusLabel;

    private String seasonPrefixString;

    private void createContents() {
        GridLayout shellGridLayout = new GridLayout(4, false);
        preferencesShell.setLayout(shellGridLayout);

        Label helpLabel = new Label(preferencesShell, SWT.NONE);
        helpLabel.setText(HELP_TOOLTIP);
        helpLabel.setLayoutData(
            new GridData(
                SWT.END,
                SWT.CENTER,
                true,
                true,
                shellGridLayout.numColumns,
                1
            )
        );

        tabFolder = new TabFolder(preferencesShell, getStyle());

        tabFolder.setLayoutData(
            new GridData(
                SWT.END,
                SWT.CENTER,
                true,
                true,
                shellGridLayout.numColumns,
                1
            )
        );

        // TabFolder and its TabItems can remain OS/native themed on some platforms,
        // but we should still ensure the contained controls inherit our palette.
        ThemeManager.applyPalette(tabFolder, themePalette);

        createGeneralTab();
        createRenameTab();
        createOverridesTab();

        // Best-effort: re-apply after tab creation so children created inside tab composites
        // are definitely themed (checkbox text, text fields, combos, lists, etc).
        ThemeManager.applyPalette(tabFolder, themePalette);

        statusLabel.open(preferencesShell, shellGridLayout.numColumns);

        createActionButtonGroup();
    }

    /**
     * Toggle whether the or not the listed {@link Control}s are enabled, based off the of
     * the given state value.
     *
     * @param state the boolean to set the other controls to
     * @param controls the list of controls to update
     */
    private void toggleEnableControls(boolean state, Control... controls) {
        for (Control control : controls) {
            control.setEnabled(state);
        }
        preferencesShell.redraw();
    }

    private void createLabel(
        final String label,
        final String tooltip,
        final Composite group
    ) {
        final Label labelObj = new Label(group, SWT.NONE);
        labelObj.setText(label);
        labelObj.setToolTipText(tooltip);

        // we don't need to return the object
    }

    private Text createText(
        final String text,
        final Composite group,
        boolean setSize
    ) {
        final Text textObj = new Text(group, SWT.BORDER);
        textObj.setText(text);
        textObj.setTextLimit(99);
        GridData layout;
        if (setSize) {
            layout = new GridData(
                GridData.FILL,
                GridData.CENTER,
                true,
                true,
                2,
                1
            );
        } else {
            layout = new GridData(GridData.FILL, GridData.CENTER, true, true);
        }
        textObj.setLayoutData(layout);

        return textObj;
    }

    private Button createCheckbox(
        final String text,
        final String tooltip,
        final boolean isChecked,
        final Composite group,
        final int alignment,
        final int span
    ) {
        final Button box = new Button(group, SWT.CHECK);
        box.setText(text);
        box.setSelection(isChecked);
        box.setLayoutData(
            new GridData(alignment, GridData.CENTER, true, true, span, 1)
        );
        box.setToolTipText(tooltip);

        return box;
    }

    private Button createDestDirButton(Composite group) {
        final Button button = new Button(group, SWT.PUSH);
        button.setText(DEST_DIR_BUTTON_TEXT);
        button.addListener(SWT.Selection, event -> {
            DirectoryDialog directoryDialog = new DirectoryDialog(
                preferencesShell
            );

            directoryDialog.setFilterPath(prefs.getDestinationDirectoryName());
            directoryDialog.setText(DIR_DIALOG_TEXT);

            String dir = directoryDialog.open();
            if (dir != null) {
                destDirText.setText(dir);
            }
        });

        return button;
    }

    /*
     * Return true if the parameters indicate a double-quote character
     * is being inserted as the first or last character of the text.
     *
     * The purpose of this method is that the double-quote is an illegal
     * character in file paths, but it is allowed in the text box where
     * the user enters the prefix -- but only as surrounding characters,
     * to show the limits of the text being entered (i.e., to help display
     * whitespace).
     *
     * Note this test is not sufficient on its own.  If the text is quoted
     * already, and then the user tries to add a double-quote in front of
     * the existing quote, that should not be allowed.  It's assumed that
     * situation is caught by other code; this method just detects if it's
     * the first or last character.
     */
    private boolean quoteAtBeginningOrEnd(
        final char c,
        final int pos,
        final int start,
        final int end,
        final int originalLength,
        final int insertLength
    ) {
        // The user has entered a character that is not a double quote.
        if (c != DOUBLE_QUOTE) {
            return false;
        }
        // If start is 0, that means we're inserting at the beginning of the text box;
        // but this may be the result of a paste, so we may be inserting multiple
        // characters.  Checking (pos == 0) makes sure we're looking at the first
        // character of the text that's being inserted.
        if ((start == 0) && (pos == 0)) {
            return true;
        }
        // This is the same idea.  "end == originalLength" means we're inserting at
        // the end of the text box, but we only want to allow the double quote if
        // it's the LAST character of the text being inserted.
        if ((end == originalLength) && (pos == (insertLength - 1))) {
            return true;
        }
        // The user has tried to insert a double quote somewhere other than the first
        // or last character.
        return false;
    }

    /*
     * A sub-method to be called once it's been determined that the user has tried to insert
     * text into the "season prefix" text box, in a legal position (i.e., not before the
     * opening quote, and not after the closing quote.)  Not all edits are insertions; some
     * just delete text.
     *
     * Constructs a string with any illegal characters removed.  If the text is the same
     * length as what we got from the event, then all characters were legal.  If the new text
     * is zero length, then all characters were illegal, and we reject the insertion.  If the
     * length is neither zero, nor the full length of the inserted text, then the user has
     * pasted in some mix of legal and illegal characters.  We strip away the illegal ones,
     * and insert the legal ones, with a warning given to the user.
     */
    private void filterEnteredSeasonPrefixText(
        VerifyEvent e,
        final int previousTextLength
    ) {
        String textToInsert = e.text;
        int insertLength = textToInsert.length();
        StringBuilder acceptedText = new StringBuilder(insertLength);
        for (int i = 0; i < insertLength; i++) {
            char c = textToInsert.charAt(i);
            boolean isLegal =
                StringUtils.isLegalFilenameCharacter(c) ||
                quoteAtBeginningOrEnd(
                    c,
                    i,
                    e.start,
                    e.end,
                    previousTextLength,
                    insertLength
                );
            if (isLegal) {
                acceptedText.append(c);
            }
        }
        if (acceptedText.length() == insertLength) {
            statusLabel.clear(ILLEGAL_CHARACTERS_WARNING);
        } else {
            statusLabel.add(ILLEGAL_CHARACTERS_WARNING);
            if (acceptedText.length() == 0) {
                e.doit = false;
            } else {
                e.text = acceptedText.toString();
            }
        }
    }

    /*
     * The main verifier method for the "season prefix" text box.  The basic idea is
     * that we want to prohibit characters that are illegal in filenames.  But we
     * added a complication by deciding to display (and accept) the text with or without
     * surrounding double quotes.
     *
     * Double quotes are, of course, illegal in filenames (on the filesystems we care
     * about, anyway).  So they are generally prohibited.  And we put the surrounding
     * quotes up by default, so the user never needs to type them.  But it's very possible
     * they might delete the quotes, and then try to re-enter them, and that should be
     * supported.  And we also support them deleting the quotes and NOT reinstating them.
     *
     * A really stringent application might insist the quotes be either absent or balanced.
     * But that makes it impossible to delete a quote, unless you delete the entire text.
     * That's very annoying.  So we allow them to be unbalanced.  The StringUtils method
     * unquoteString will remove the quote from the front and from the back, whether they
     * are balanced or not.
     *
     * In order to avoid having the illegal quote character in the middle of the text, we
     * cannot allow the user to insert any text before the opening quote, or any text after
     * the closing quote.  Doing so would change them from delimiters to part of the text.
     *
     * Edits might not be inserting text at all.  They could be deleting text.  This method
     * checks that the user is trying to insert text, and that it's not before the opening
     * quote or after the closing quote.  If that's the case, it calls the next method,
     * filterEnteredSeasonPrefixText, to ensure no illegal characters are being entered.
     */
    private void verifySeasonPrefixText(VerifyEvent e) {
        if (e.text.length() > 0) {
            String previousText = seasonPrefixText.getText();
            int originalLength = previousText.length();

            if (
                (e.end < (originalLength - 1)) &&
                (previousText.charAt(e.end) == DOUBLE_QUOTE)
            ) {
                statusLabel.add(NO_TEXT_BEFORE_OPENING_QUOTE);
                e.doit = false;
            } else if (
                (e.start > 1) &&
                (previousText.charAt(e.start - 1) == DOUBLE_QUOTE)
            ) {
                statusLabel.add(NO_TEXT_AFTER_CLOSING_QUOTE);
                e.doit = false;
            } else {
                filterEnteredSeasonPrefixText(e, originalLength);
            }
        }
    }

    /*
     * Makes sure the text entered as season prefix is valid in a pathname.
     */
    private void ensureValidPrefixText() {
        String prefixText = seasonPrefixText.getText();

        // Remove the surrounding double quotes, if present;
        // any other double quotes should not be removed.
        String unquoted = StringUtils.unquoteString(prefixText);
        // The verifier should have prevented any illegal characters from
        // being entered.  This is just to check.
        seasonPrefixString = StringUtils.replaceIllegalCharacters(unquoted);

        if (!seasonPrefixString.equals(unquoted)) {
            // Somehow, illegal characters got through.
            logger.severe("Illegal characters recognized in season prefix");
            logger.severe(
                "Instead of \"" +
                    unquoted +
                    "\", will use \"" +
                    seasonPrefixString +
                    "\""
            );
        }
    }

    /*
     * Create the controls that regard the naming of the season prefix folder.
     * The text box gets both a verify listener and a modify listener.
     */
    private void createSeasonPrefixControls(final Composite generalGroup) {
        createLabel(SEASON_PREFIX_TEXT, PREFIX_TOOLTIP, generalGroup);
        seasonPrefixString = prefs.getSeasonPrefix();
        seasonPrefixText = createText(
            StringUtils.makeQuotedString(seasonPrefixString),
            generalGroup,
            true
        );
        seasonPrefixText.addVerifyListener(e -> {
            statusLabel.clear(NO_TEXT_BEFORE_OPENING_QUOTE);
            statusLabel.clear(NO_TEXT_AFTER_CLOSING_QUOTE);
            verifySeasonPrefixText(e);
        });
        seasonPrefixText.addModifyListener(e -> ensureValidPrefixText());
        seasonPrefixLeadingZeroCheckbox = createCheckbox(
            SEASON_PREFIX_ZERO_TEXT,
            SEASON_PREFIX_ZERO_TOOLTIP,
            prefs.isSeasonPrefixLeadingZero(),
            generalGroup,
            GridData.BEGINNING,
            3
        );
    }

    private void populateGeneralTab(final Composite generalGroup) {
        moveSelectedCheckbox = createCheckbox(
            MOVE_SELECTED_TEXT,
            MOVE_SELECTED_TOOLTIP,
            true,
            generalGroup,
            GridData.BEGINNING,
            2
        );
        renameSelectedCheckbox = createCheckbox(
            RENAME_SELECTED_TEXT,
            RENAME_SELECTED_TOOLTIP,
            true,
            generalGroup,
            GridData.END,
            1
        );

        createLabel(DEST_DIR_TEXT, DEST_DIR_TOOLTIP, generalGroup);
        destDirText = createText(
            prefs.getDestinationDirectoryName(),
            generalGroup,
            false
        );
        destDirButton = createDestDirButton(generalGroup);

        createSeasonPrefixControls(generalGroup);

        createLabel(IGNORE_LABEL_TEXT, IGNORE_LABEL_TOOLTIP, generalGroup);
        ignoreWordsText = createText(
            prefs.getIgnoredKeywordsString(),
            generalGroup,
            false
        );

        recurseFoldersCheckbox = createCheckbox(
            RECURSE_FOLDERS_TEXT,
            RECURSE_FOLDERS_TOOLTIP,
            prefs.isRecursivelyAddFolders(),
            generalGroup,
            GridData.BEGINNING,
            3
        );
        rmdirEmptyCheckbox = createCheckbox(
            REMOVE_EMPTIED_TEXT,
            REMOVE_EMPTIED_TOOLTIP,
            prefs.isRemoveEmptiedDirectories(),
            generalGroup,
            GridData.BEGINNING,
            3
        );
        deleteRowsCheckbox = createCheckbox(
            DELETE_ROWS_TEXT,
            DELETE_ROWS_TOOLTIP,
            prefs.isDeleteRowAfterMove(),
            generalGroup,
            GridData.BEGINNING,
            3
        );
        checkForUpdatesCheckbox = createCheckbox(
            CHECK_UPDATES_TEXT,
            CHECK_UPDATES_TOOLTIP,
            prefs.checkForUpdates(),
            generalGroup,
            GridData.BEGINNING,
            3
        );

        setMtimeToNowCheckbox = createCheckbox(
            "Set file modification time to now after move/rename",
            "If checked, TVRenamer will set the destination file's modification time to the current time after moving/renaming.\nIf unchecked (default), TVRenamer will preserve the original modification time.",
            !prefs.isPreserveFileModificationTime(),
            generalGroup,
            GridData.BEGINNING,
            3
        );

        createLabel(THEME_MODE_TEXT, THEME_MODE_TOOLTIP, generalGroup);
        themeModeCombo = new Combo(generalGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        themeModeCombo.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)
        );
        themeModeCombo.setToolTipText(THEME_MODE_TOOLTIP);
        for (ThemeMode mode : ThemeMode.values()) {
            themeModeCombo.add(mode.toString());
        }

        Label themeRestartLabel = new Label(generalGroup, SWT.NONE);
        themeRestartLabel.setText(THEME_MODE_RESTART_NOTE);
        themeRestartLabel.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        preferDvdOrderCheckbox = createCheckbox(
            PREFER_DVD_ORDER_TEXT,
            PREFER_DVD_ORDER_TOOLTIP,
            prefs.isPreferDvdOrderIfPresent(),
            generalGroup,
            GridData.BEGINNING,
            3
        );
    }

    private void initializeGeneralControls() {
        final boolean moveIsSelected = prefs.isMoveSelected();
        moveSelectedCheckbox.setSelection(moveIsSelected);
        toggleEnableControls(
            moveIsSelected,
            destDirText,
            destDirButton,
            seasonPrefixText,
            seasonPrefixLeadingZeroCheckbox
        );
        moveSelectedCheckbox.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    toggleEnableControls(
                        moveSelectedCheckbox.getSelection(),
                        destDirText,
                        destDirButton,
                        seasonPrefixText,
                        seasonPrefixLeadingZeroCheckbox
                    );
                }
            }
        );

        boolean renameIsSelected = prefs.isRenameSelected();
        renameSelectedCheckbox.setSelection(renameIsSelected);

        ThemeMode selectedTheme = prefs.getThemeMode();
        if (selectedTheme == null) {
            selectedTheme = ThemeMode.LIGHT;
        }
        int themeIndex = themeModeCombo.indexOf(selectedTheme.toString());
        if (themeIndex >= 0) {
            themeModeCombo.select(themeIndex);
        } else if (themeModeCombo.getItemCount() > 0) {
            themeModeCombo.select(0);
        }
    }

    private void createGeneralTab() {
        final TabItem item = new TabItem(tabFolder, SWT.NULL);
        item.setText(GENERAL_LABEL);

        final Composite generalGroup = new Composite(tabFolder, SWT.NONE);

        generalGroup.setLayout(new GridLayout(3, false));

        generalGroup.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1)
        );

        generalGroup.setToolTipText(GENERAL_TOOLTIP);
        ThemeManager.applyPalette(generalGroup, themePalette);

        populateGeneralTab(generalGroup);
        initializeGeneralControls();

        item.setControl(generalGroup);
    }

    private void addStringsToList(
        final List guiList,
        final ReplacementToken... tokens
    ) {
        for (ReplacementToken token : tokens) {
            guiList.add(token.toString());
        }
    }

    private void createRenameTab() {
        TabItem item = new TabItem(tabFolder, SWT.NULL);
        item.setText(RENAMING_LABEL);

        Composite replacementGroup = new Composite(tabFolder, SWT.NONE);

        replacementGroup.setLayout(new GridLayout(3, false));

        replacementGroup.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1)
        );
        ThemeManager.applyPalette(replacementGroup, themePalette);

        Label renameTokensLabel = new Label(replacementGroup, SWT.NONE);
        renameTokensLabel.setText(RENAME_TOKEN_TEXT);
        renameTokensLabel.setToolTipText(RENAME_TOKEN_TOOLTIP);

        List renameTokensList = new List(replacementGroup, SWT.SINGLE);
        renameTokensList.setLayoutData(
            new GridData(GridData.BEGINNING, GridData.CENTER, true, true, 2, 1)
        );
        addStringsToList(
            renameTokensList,
            SHOW_NAME,
            SEASON_NUM,
            SEASON_NUM_LEADING_ZERO,
            EPISODE_NUM,
            EPISODE_NUM_LEADING_ZERO,
            EPISODE_TITLE,
            EPISODE_TITLE_NO_SPACES,
            EPISODE_RESOLUTION,
            DATE_DAY_NUM,
            DATE_DAY_NUMLZ,
            DATE_MONTH_NUM,
            DATE_MONTH_NUMLZ,
            DATE_YEAR_MIN,
            DATE_YEAR_FULL
        );

        Label episodeTitleLabel = new Label(replacementGroup, SWT.NONE);
        episodeTitleLabel.setText(RENAME_FORMAT_TEXT);
        episodeTitleLabel.setToolTipText(RENAME_FORMAT_TOOLTIP);

        replacementStringText = createText(
            prefs.getRenameReplacementString(),
            replacementGroup,
            true
        );

        createDragSource(renameTokensList);
        createDropTarget(replacementStringText);

        item.setControl(replacementGroup);
    }

    private void createOverridesTab() {
        TabItem item = new TabItem(tabFolder, SWT.NULL);
        item.setText("Matching");

        Composite overridesGroup = new Composite(tabFolder, SWT.NONE);
        overridesGroup.setLayout(new GridLayout(3, false));
        overridesGroup.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1)
        );
        ThemeManager.applyPalette(overridesGroup, themePalette);

        // --- Overrides section (existing UI for now; will be replaced by table editor) ---
        Label overridesHeader = new Label(overridesGroup, SWT.NONE);
        overridesHeader.setText(
            "Overrides (Extracted show → Replacement text)"
        );
        overridesHeader.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label fromLabel = new Label(overridesGroup, SWT.NONE);
        fromLabel.setText("From (extracted show name)");
        fromLabel.setToolTipText(
            "Exact match (case-insensitive). Example: Stars"
        );

        overridesFromText = createText("", overridesGroup, false);
        new Label(overridesGroup, SWT.NONE); // spacer

        Label toLabel = new Label(overridesGroup, SWT.NONE);
        toLabel.setText("To (correct show name)");
        toLabel.setToolTipText(
            "Replacement name used for TVDB search. Example: Stars (2024)"
        );

        overridesToText = createText("", overridesGroup, false);
        new Label(overridesGroup, SWT.NONE); // spacer

        // Force relayout/redraw when focus moves between fields; some SWT layouts
        // can temporarily miscompute sizes, causing the button row to appear hidden
        // until the next paint event (e.g., mouse hover).
        overridesFromText.addListener(SWT.FocusOut, e ->
            overridesGroup.layout(true, true)
        );
        overridesToText.addListener(SWT.FocusOut, e ->
            overridesGroup.layout(true, true)
        );

        Composite buttons = new Composite(overridesGroup, SWT.NONE);
        buttons.setLayout(new GridLayout(3, true));
        // Explicit sizing hints prevent SWT from "collapsing" this row when focus moves
        // between Text controls (observed as buttons disappearing until hover).
        GridData buttonsGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false,
            3,
            1
        );
        buttonsGridData.minimumHeight = 35;
        buttons.setLayoutData(buttonsGridData);

        Button addButton = new Button(buttons, SWT.PUSH);
        addButton.setText("Add / Update");
        GridData addButtonGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        addButtonGridData.minimumWidth = 110;
        addButtonGridData.heightHint = 30;
        addButton.setLayoutData(addButtonGridData);
        addButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    String from = overridesFromText.getText().trim();
                    String to = overridesToText.getText().trim();
                    if (from.isEmpty() || to.isEmpty()) {
                        return;
                    }
                    upsertOverride(from, to);
                    overridesFromText.setText("");
                    overridesToText.setText("");
                }
            }
        );

        Button removeButton = new Button(buttons, SWT.PUSH);
        removeButton.setText("Remove");
        GridData removeButtonGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        removeButtonGridData.minimumWidth = 90;
        removeButtonGridData.heightHint = 30;
        removeButton.setLayoutData(removeButtonGridData);
        removeButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    int idx = overridesList.getSelectionIndex();
                    if (idx >= 0) {
                        String entry = overridesList.getItem(idx);
                        String from = entry.split("=>")[0].trim();
                        removeOverride(from);
                    }
                }
            }
        );

        Button clearButton = new Button(buttons, SWT.PUSH);
        clearButton.setText("Clear");
        GridData clearButtonGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        clearButtonGridData.minimumWidth = 90;
        clearButtonGridData.heightHint = 30;
        clearButton.setLayoutData(clearButtonGridData);
        clearButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    overridesList.removeAll();
                }
            }
        );

        Label listLabel = new Label(overridesGroup, SWT.NONE);
        listLabel.setText("Overrides list");
        listLabel.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        overridesList = new List(
            overridesGroup,
            SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL
        );
        overridesList.setLayoutData(
            new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1)
        );

        // Populate list from current prefs
        for (Map.Entry<String, String> e : prefs
            .getShowNameOverrides()
            .entrySet()) {
            overridesList.add(e.getKey() + " => " + e.getValue());
        }

        // --- Disambiguations section placeholder (next commits will implement table editor) ---
        Label spacer = new Label(overridesGroup, SWT.NONE);
        spacer.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label disambiguationsHeader = new Label(overridesGroup, SWT.NONE);
        disambiguationsHeader.setText(
            "Disambiguations (Query string → Series ID) [coming next]"
        );
        disambiguationsHeader.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label disambiguationsNote = new Label(overridesGroup, SWT.WRAP);
        disambiguationsNote.setText(
            "This section will allow editing pinned show selections (query string → series ID)."
        );
        disambiguationsNote.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1)
        );

        item.setControl(overridesGroup);
    }

    private void upsertOverride(String from, String to) {
        // Remove any existing entry for this key (case-insensitive)
        int removeIdx = -1;
        for (int i = 0; i < overridesList.getItemCount(); i++) {
            String entry = overridesList.getItem(i);
            String existingFrom = entry.split("=>")[0].trim();
            if (existingFrom.equalsIgnoreCase(from)) {
                removeIdx = i;
                break;
            }
        }
        if (removeIdx >= 0) {
            overridesList.remove(removeIdx);
        }
        overridesList.add(from + " => " + to);
    }

    private void removeOverride(String from) {
        for (int i = 0; i < overridesList.getItemCount(); i++) {
            String entry = overridesList.getItem(i);
            String existingFrom = entry.split("=>")[0].trim();
            if (existingFrom.equalsIgnoreCase(from)) {
                overridesList.remove(i);
                break;
            }
        }
    }

    private void createDragSource(final List sourceList) {
        Transfer[] types = new Transfer[] { TextTransfer.getInstance() };
        DragSource dragSource = new DragSource(sourceList, DND_OPERATIONS);
        dragSource.setTransfer(types);
        dragSource.addDragListener(
            new PreferencesDragSourceListener(sourceList)
        );
    }

    private void createDropTarget(final Text targetText) {
        Transfer[] types = new Transfer[] { TextTransfer.getInstance() };
        DropTarget dropTarget = new DropTarget(targetText, DND_OPERATIONS);
        dropTarget.setTransfer(types);
        dropTarget.addDropListener(
            new PreferencesDropTargetListener(targetText)
        );
    }

    private void createActionButtonGroup() {
        Composite bottomButtonsComposite = new Composite(
            preferencesShell,
            SWT.FILL
        );
        bottomButtonsComposite.setLayout(new GridLayout(2, false));
        ThemeManager.applyPalette(bottomButtonsComposite, themePalette);

        bottomButtonsComposite.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1)
        );

        Button cancelButton = new Button(bottomButtonsComposite, SWT.PUSH);
        GridData cancelButtonGridData = new GridData(
            GridData.BEGINNING,
            GridData.CENTER,
            false,
            false
        );
        cancelButtonGridData.minimumWidth = 150;
        cancelButtonGridData.widthHint = 150;
        cancelButton.setLayoutData(cancelButtonGridData);
        cancelButton.setText(CANCEL_LABEL);
        cancelButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    preferencesShell.close();
                }
            }
        );

        Button saveButton = new Button(bottomButtonsComposite, SWT.PUSH);
        GridData saveButtonGridData = new GridData(
            GridData.END,
            GridData.CENTER,
            true,
            true
        );
        saveButtonGridData.minimumWidth = 150;
        saveButtonGridData.widthHint = 150;
        saveButton.setLayoutData(saveButtonGridData);
        saveButton.setText(SAVE_LABEL);
        saveButton.setFocus();
        saveButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    boolean saved = savePreferences();
                    if (saved) {
                        preferencesShell.close();
                    }
                }
            }
        );

        // Set the OK button as the default, so
        // user can press Enter to save
        preferencesShell.setDefaultButton(saveButton);
    }

    /**
     * Save the preferences to the xml file
     */
    private boolean savePreferences() {
        // Validate the move destination BEFORE committing it into UserPreferences.
        // This prevents the main window (ResultsTable) from reacting to preference changes
        // and showing a second error popup while the Preferences dialog is still open.
        final boolean moveSelected = moveSelectedCheckbox.getSelection();
        final String destDirTextValue = destDirText.getText();

        if (moveSelected) {
            final Path destPath;
            try {
                destPath = Paths.get(destDirTextValue);
            } catch (RuntimeException ex) {
                MessageBox box = new MessageBox(
                    preferencesShell,
                    SWT.ICON_ERROR | SWT.OK
                );
                box.setText(ERROR_LABEL);
                box.setMessage(
                    CANT_CREATE_DEST +
                        ": '" +
                        destDirTextValue +
                        "'. " +
                        MOVE_NOT_POSSIBLE
                );
                box.open();

                if (destDirText != null && !destDirText.isDisposed()) {
                    destDirText.setFocus();
                    destDirText.selectAll();
                }
                return false;
            }

            if (!FileUtilities.checkForCreatableDirectory(destPath)) {
                MessageBox box = new MessageBox(
                    preferencesShell,
                    SWT.ICON_ERROR | SWT.OK
                );
                box.setText(ERROR_LABEL);
                box.setMessage(
                    CANT_CREATE_DEST +
                        ": '" +
                        destDirTextValue +
                        "'. " +
                        MOVE_NOT_POSSIBLE
                );
                box.open();

                if (destDirText != null && !destDirText.isDisposed()) {
                    destDirText.setFocus();
                    destDirText.selectAll();
                }
                return false;
            }
        }

        // Update the preferences object from the UI control values
        prefs.setSeasonPrefix(seasonPrefixString);
        prefs.setSeasonPrefixLeadingZero(
            seasonPrefixLeadingZeroCheckbox.getSelection()
        );
        prefs.setRenameReplacementString(replacementStringText.getText());
        prefs.setIgnoreKeywords(ignoreWordsText.getText());
        prefs.setCheckForUpdates(checkForUpdatesCheckbox.getSelection());
        prefs.setRecursivelyAddFolders(recurseFoldersCheckbox.getSelection());
        prefs.setRemoveEmptiedDirectories(rmdirEmptyCheckbox.getSelection());
        prefs.setDeleteRowAfterMove(deleteRowsCheckbox.getSelection());

        // Default is preserve; checkbox is the inverse ("set to now").
        if (setMtimeToNowCheckbox != null) {
            prefs.setPreserveFileModificationTime(
                !setMtimeToNowCheckbox.getSelection()
            );
        }

        // Commit move settings only after validation succeeded
        prefs.setDestinationDirectory(destDirTextValue);
        prefs.setMoveSelected(moveSelected);

        prefs.setRenameSelected(renameSelectedCheckbox.getSelection());

        prefs.setPreferDvdOrderIfPresent(preferDvdOrderCheckbox.getSelection());

        ThemeMode selectedTheme = ThemeMode.fromString(
            themeModeCombo.getText()
        );
        if (selectedTheme == null) {
            selectedTheme = ThemeMode.LIGHT;
        }
        prefs.setThemeMode(selectedTheme);

        // Show name overrides (exact match, case-insensitive)
        Map<String, String> overrides = new LinkedHashMap<>();
        if (overridesList != null && !overridesList.isDisposed()) {
            for (String entry : overridesList.getItems()) {
                String[] parts = entry.split("=>");
                if (parts.length == 2) {
                    String from = parts[0].trim();
                    String to = parts[1].trim();
                    if (!from.isEmpty() && !to.isEmpty()) {
                        overrides.put(from, to);
                    }
                }
            }
        }
        prefs.setShowNameOverrides(overrides);

        UserPreferences.store(prefs);
        return true;
    }

    /**
     * Creates and opens the preferences dialog, and runs the event loop.
     *
     */
    public void open() {
        // Create the dialog window

        preferencesShell = new Shell(parent, getStyle());

        preferencesShell.setText(PREFERENCES_LABEL);

        themePalette = ThemeManager.createPalette(
            preferencesShell.getDisplay()
        );
        ThemeManager.applyPalette(preferencesShell, themePalette);

        // Add the contents of the dialog window

        createContents();

        preferencesShell.pack();
        preferencesShell.open();
        Display display = parent.getDisplay();
        while (!preferencesShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    /**
     * PreferencesDialog constructor
     *
     * @param parent
     *            the parent {@link Shell}
     */
    public PreferencesDialog(final Shell parent) {
        super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        this.parent = parent;
        statusLabel = new StatusLabel();
    }
}
