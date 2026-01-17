package org.tvrenamer.view;

import static org.tvrenamer.model.ReplacementToken.*;
import static org.tvrenamer.model.util.Constants.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.tvrenamer.controller.TheTVDBProvider;
import org.tvrenamer.controller.util.FileUtilities;
import org.tvrenamer.controller.util.StringUtils;
import org.tvrenamer.model.ReplacementToken;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.ShowOption;
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

    // Matching (Overrides + Disambiguations)
    // Overrides (Extracted show -> replacement show text)
    private Text overridesFromText;
    private Text overridesToText;
    private Table overridesTable;

    // Disambiguations (query string -> series id)
    private Text disambiguationsQueryText;
    private Text disambiguationsIdText;
    private Table disambiguationsTable;

    // Matching hover "tooltip" label (SWT TableItem has no setToolTipText()).
    private Label matchingHoverTipLabel;

    // Matching validation / dirty tracking
    private static final String MATCHING_STATUS_INCOMPLETE = "Incomplete";
    private static final String MATCHING_DIRTY_KEY = "tvrenamer.matching.dirty";

    // Status icons (reuse the same assets as the main results table).
    private static final org.eclipse.swt.graphics.Image MATCHING_ICON_OK =
        ItemState.SUCCESS.getIcon();
    private static final org.eclipse.swt.graphics.Image MATCHING_ICON_ERROR =
        ItemState.FAIL.getIcon();
    private static final org.eclipse.swt.graphics.Image MATCHING_ICON_VALIDATING =
        ItemState.DOWNLOADING.getIcon();

    // Save gating: disable Save when any dirty row is invalid/incomplete/validating
    private Button saveButton;

    // Matching async validation
    private static final String MATCHING_VALIDATE_TOKEN_KEY =
        "tvrenamer.matching.validateToken";
    private volatile long matchingValidationSeq = 0L;

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

        // Shared "tooltip" label for validation messages (SWT TableItem has no setToolTipText()).
        matchingHoverTipLabel = new Label(overridesGroup, SWT.WRAP);
        matchingHoverTipLabel.setText("");
        matchingHoverTipLabel.setLayoutData(
            new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1)
        );

        // --- Overrides section (Extracted show -> Replacement text) ---
        Label overridesHeader = new Label(overridesGroup, SWT.NONE);
        overridesHeader.setText(
            "Overrides (Extracted show \u2192 Replacement text)"
        );
        overridesHeader.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label fromLabel = new Label(overridesGroup, SWT.NONE);
        fromLabel.setText("Extracted show");
        fromLabel.setToolTipText(
            "Exact match (case-insensitive). Example: Stars"
        );

        overridesFromText = createText("", overridesGroup, false);
        new Label(overridesGroup, SWT.NONE); // spacer

        Label toLabel = new Label(overridesGroup, SWT.NONE);
        toLabel.setText("Replace with");
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

        Composite overrideButtons = new Composite(overridesGroup, SWT.NONE);
        overrideButtons.setLayout(new GridLayout(3, true));
        GridData overrideButtonsGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false,
            3,
            1
        );
        overrideButtonsGridData.minimumHeight = 35;
        overrideButtons.setLayoutData(overrideButtonsGridData);

        Button overrideAddButton = new Button(overrideButtons, SWT.PUSH);
        overrideAddButton.setText("Add / Update");
        GridData overrideAddGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        overrideAddGridData.minimumWidth = 110;
        overrideAddGridData.heightHint = 30;
        overrideAddButton.setLayoutData(overrideAddGridData);
        overrideAddButton.addSelectionListener(
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

                    // Validate the selected row (or the upserted row) asynchronously.
                    int idx = overridesTable.getSelectionIndex();
                    if (idx < 0) {
                        idx = overridesTable.getItemCount() - 1;
                    }
                    validateMatchingRowOnline(
                        overridesTable,
                        idx,
                        MatchingRowType.OVERRIDE
                    );
                }
            }
        );

        Button overrideRemoveButton = new Button(overrideButtons, SWT.PUSH);
        overrideRemoveButton.setText("Remove");
        GridData overrideRemoveGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        overrideRemoveGridData.minimumWidth = 90;
        overrideRemoveGridData.heightHint = 30;
        overrideRemoveButton.setLayoutData(overrideRemoveGridData);
        overrideRemoveButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    int idx = overridesTable.getSelectionIndex();
                    if (idx >= 0) {
                        overridesTable.remove(idx);
                    }
                }
            }
        );

        Button overrideClearButton = new Button(overrideButtons, SWT.PUSH);
        overrideClearButton.setText("Clear All");
        GridData overrideClearGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        overrideClearGridData.minimumWidth = 90;
        overrideClearGridData.heightHint = 30;
        overrideClearButton.setLayoutData(overrideClearGridData);
        overrideClearButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    MessageBox box = new MessageBox(
                        preferencesShell,
                        SWT.ICON_WARNING | SWT.OK | SWT.CANCEL
                    );
                    box.setText("Clear All");
                    box.setMessage(
                        "Are you sure?\r\nThis will remove all entries."
                    );
                    if (box.open() == SWT.OK) {
                        overridesTable.removeAll();
                        updateSaveEnabledFromMatchingValidation();
                    }
                }
            }
        );

        overridesTable = new Table(
            overridesGroup,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE
        );
        overridesTable.setHeaderVisible(true);
        overridesTable.setLinesVisible(true);
        GridData overridesTableData = new GridData(
            SWT.FILL,
            SWT.FILL,
            true,
            true,
            3,
            1
        );
        overridesTableData.minimumHeight = 110;
        overridesTable.setLayoutData(overridesTableData);

        TableColumn oColFrom = new TableColumn(overridesTable, SWT.LEFT);
        oColFrom.setText("Extracted show");
        TableColumn oColTo = new TableColumn(overridesTable, SWT.LEFT);
        oColTo.setText("Replace with");
        TableColumn oColStatus = new TableColumn(overridesTable, SWT.LEFT);
        oColStatus.setText("Status");

        // Populate table from prefs (not dirty; treat as OK by default).
        for (Map.Entry<String, String> e : prefs
            .getShowNameOverrides()
            .entrySet()) {
            TableItem ti = new TableItem(overridesTable, SWT.NONE);
            ti.setText(new String[] { e.getKey(), e.getValue(), "" });
            ti.setImage(2, MATCHING_ICON_OK);
            ti.setData(MATCHING_DIRTY_KEY, Boolean.FALSE);
        }
        for (TableColumn c : overridesTable.getColumns()) {
            c.pack();
        }

        // Clicking a row loads it into the edit fields for easy update
        overridesTable.addListener(SWT.Selection, e -> {
            int idx = overridesTable.getSelectionIndex();
            if (idx >= 0) {
                TableItem ti = overridesTable.getItem(idx);
                overridesFromText.setText(ti.getText(0));
                overridesToText.setText(ti.getText(1));
            }
        });

        // Hover: show stored validation message in the shared label (best-effort).
        overridesTable.addListener(SWT.MouseMove, e -> {
            if (
                matchingHoverTipLabel == null ||
                matchingHoverTipLabel.isDisposed()
            ) {
                return;
            }

            // Hit-test the hovered row and show its validation message (best-effort).
            TableItem ti = overridesTable.getItem(
                new org.eclipse.swt.graphics.Point(e.x, e.y)
            );
            String next = (ti == null)
                ? ""
                : String.valueOf(
                      ti.getData("tvrenamer.matching.validationMessage")
                  );

            // Avoid forcing layouts on every mouse move; only update when text actually changes.
            String current = matchingHoverTipLabel.getText();
            if (current == null) {
                current = "";
            }
            if (!current.equals(next)) {
                matchingHoverTipLabel.setText(next);
            }
        });

        // --- Disambiguations section (Query string -> Series ID) ---
        Label spacer = new Label(overridesGroup, SWT.NONE);
        spacer.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label disambiguationsHeader = new Label(overridesGroup, SWT.NONE);
        disambiguationsHeader.setText(
            "Disambiguations (Query string \u2192 Series ID)"
        );
        disambiguationsHeader.setLayoutData(
            new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1)
        );

        Label queryLabel = new Label(overridesGroup, SWT.NONE);
        queryLabel.setText("Query string");
        queryLabel.setToolTipText(
            "Provider query string (normalized). Example: the rookie"
        );

        disambiguationsQueryText = createText("", overridesGroup, false);
        new Label(overridesGroup, SWT.NONE); // spacer

        Label idLabel = new Label(overridesGroup, SWT.NONE);
        idLabel.setText("Series ID");
        idLabel.setToolTipText(
            "Provider series id (e.g., TVDB seriesid). Example: 361753"
        );

        disambiguationsIdText = createText("", overridesGroup, false);
        new Label(overridesGroup, SWT.NONE); // spacer

        disambiguationsQueryText.addListener(SWT.FocusOut, e ->
            overridesGroup.layout(true, true)
        );
        disambiguationsIdText.addListener(SWT.FocusOut, e ->
            overridesGroup.layout(true, true)
        );

        Composite disambiguationButtons = new Composite(
            overridesGroup,
            SWT.NONE
        );
        disambiguationButtons.setLayout(new GridLayout(3, true));
        GridData disambButtonsGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false,
            3,
            1
        );
        disambButtonsGridData.minimumHeight = 35;
        disambiguationButtons.setLayoutData(disambButtonsGridData);

        Button disambAddButton = new Button(disambiguationButtons, SWT.PUSH);
        disambAddButton.setText("Add / Update");
        GridData disambAddGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        disambAddGridData.minimumWidth = 110;
        disambAddGridData.heightHint = 30;
        disambAddButton.setLayoutData(disambAddGridData);
        disambAddButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    String q = disambiguationsQueryText.getText().trim();
                    String id = disambiguationsIdText.getText().trim();
                    if (q.isEmpty() || id.isEmpty()) {
                        return;
                    }
                    upsertDisambiguation(q, id);
                    disambiguationsQueryText.setText("");
                    disambiguationsIdText.setText("");

                    // Validate the selected row (or the upserted row) asynchronously.
                    int idx = disambiguationsTable.getSelectionIndex();
                    if (idx < 0) {
                        idx = disambiguationsTable.getItemCount() - 1;
                    }
                    validateMatchingRowOnline(
                        disambiguationsTable,
                        idx,
                        MatchingRowType.DISAMBIGUATION
                    );
                }
            }
        );

        Button disambRemoveButton = new Button(disambiguationButtons, SWT.PUSH);
        disambRemoveButton.setText("Remove");
        GridData disambRemoveGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        disambRemoveGridData.minimumWidth = 90;
        disambRemoveGridData.heightHint = 30;
        disambRemoveButton.setLayoutData(disambRemoveGridData);
        disambRemoveButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    int idx = disambiguationsTable.getSelectionIndex();
                    if (idx >= 0) {
                        disambiguationsTable.remove(idx);
                    }
                }
            }
        );

        Button disambClearButton = new Button(disambiguationButtons, SWT.PUSH);
        disambClearButton.setText("Clear All");
        GridData disambClearGridData = new GridData(
            SWT.FILL,
            SWT.CENTER,
            true,
            false
        );
        disambClearGridData.minimumWidth = 90;
        disambClearGridData.heightHint = 30;
        disambClearButton.setLayoutData(disambClearGridData);
        disambClearButton.addSelectionListener(
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    MessageBox box = new MessageBox(
                        preferencesShell,
                        SWT.ICON_WARNING | SWT.OK | SWT.CANCEL
                    );
                    box.setText("Clear All");
                    box.setMessage(
                        "Are you sure?\r\nThis will remove all entries."
                    );
                    if (box.open() == SWT.OK) {
                        disambiguationsTable.removeAll();
                        updateSaveEnabledFromMatchingValidation();
                    }
                }
            }
        );

        disambiguationsTable = new Table(
            overridesGroup,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE
        );
        disambiguationsTable.setHeaderVisible(true);
        disambiguationsTable.setLinesVisible(true);
        GridData disambiguationsTableData = new GridData(
            SWT.FILL,
            SWT.FILL,
            true,
            true,
            3,
            1
        );
        disambiguationsTableData.minimumHeight = 110;
        disambiguationsTable.setLayoutData(disambiguationsTableData);

        TableColumn dColQuery = new TableColumn(disambiguationsTable, SWT.LEFT);
        dColQuery.setText("Query string");
        TableColumn dColId = new TableColumn(disambiguationsTable, SWT.LEFT);
        dColId.setText("Series ID");
        TableColumn dColStatus = new TableColumn(
            disambiguationsTable,
            SWT.LEFT
        );
        dColStatus.setText("Status");

        for (Map.Entry<String, String> e : prefs
            .getShowDisambiguationOverrides()
            .entrySet()) {
            TableItem ti = new TableItem(disambiguationsTable, SWT.NONE);
            ti.setText(new String[] { e.getKey(), e.getValue(), "" });
            ti.setImage(2, MATCHING_ICON_OK);
            ti.setData(MATCHING_DIRTY_KEY, Boolean.FALSE);
        }
        for (TableColumn c : disambiguationsTable.getColumns()) {
            c.pack();
        }

        disambiguationsTable.addListener(SWT.Selection, e -> {
            int idx = disambiguationsTable.getSelectionIndex();
            if (idx >= 0) {
                TableItem ti = disambiguationsTable.getItem(idx);
                disambiguationsQueryText.setText(ti.getText(0));
                disambiguationsIdText.setText(ti.getText(1));
            }
        });

        // Hover: show stored validation message in the shared label (best-effort).
        disambiguationsTable.addListener(SWT.MouseMove, e -> {
            if (
                matchingHoverTipLabel == null ||
                matchingHoverTipLabel.isDisposed()
            ) {
                return;
            }

            // Hit-test the hovered row and show its validation message (best-effort).
            TableItem ti = disambiguationsTable.getItem(
                new org.eclipse.swt.graphics.Point(e.x, e.y)
            );
            String next = (ti == null)
                ? ""
                : String.valueOf(
                      ti.getData("tvrenamer.matching.validationMessage")
                  );

            // Avoid forcing layouts on every mouse move; only update when text actually changes.
            String current = matchingHoverTipLabel.getText();
            if (current == null) {
                current = "";
            }
            if (!current.equals(next)) {
                matchingHoverTipLabel.setText(next);
            }
        });

        item.setControl(overridesGroup);
    }

    private void upsertOverride(String from, String to) {
        // Update selected row if present; otherwise upsert by key (case-insensitive).
        int selected = (overridesTable == null)
            ? -1
            : overridesTable.getSelectionIndex();
        if (selected >= 0) {
            TableItem ti = overridesTable.getItem(selected);
            ti.setText(new String[] { from, to });
            return;
        }

        int updateIdx = -1;
        if (overridesTable != null) {
            for (int i = 0; i < overridesTable.getItemCount(); i++) {
                TableItem ti = overridesTable.getItem(i);
                if (ti.getText(0).trim().equalsIgnoreCase(from)) {
                    updateIdx = i;
                    break;
                }
            }
            if (updateIdx >= 0) {
                overridesTable
                    .getItem(updateIdx)
                    .setText(new String[] { from, to });
                return;
            }

            TableItem ti = new TableItem(overridesTable, SWT.NONE);
            ti.setText(new String[] { from, to });
        }
    }

    private void removeOverride(String from) {
        if (overridesTable == null) {
            return;
        }
        for (int i = 0; i < overridesTable.getItemCount(); i++) {
            TableItem ti = overridesTable.getItem(i);
            if (ti.getText(0).trim().equalsIgnoreCase(from)) {
                overridesTable.remove(i);
                break;
            }
        }
    }

    private void upsertDisambiguation(String queryString, String seriesId) {
        int selected = (disambiguationsTable == null)
            ? -1
            : disambiguationsTable.getSelectionIndex();
        if (selected >= 0) {
            TableItem ti = disambiguationsTable.getItem(selected);
            ti.setText(new String[] { queryString, seriesId });
            return;
        }

        int updateIdx = -1;
        if (disambiguationsTable != null) {
            for (int i = 0; i < disambiguationsTable.getItemCount(); i++) {
                TableItem ti = disambiguationsTable.getItem(i);
                if (ti.getText(0).trim().equalsIgnoreCase(queryString)) {
                    updateIdx = i;
                    break;
                }
            }
            if (updateIdx >= 0) {
                disambiguationsTable
                    .getItem(updateIdx)
                    .setText(new String[] { queryString, seriesId });
                return;
            }

            TableItem ti = new TableItem(disambiguationsTable, SWT.NONE);
            ti.setText(new String[] { queryString, seriesId });
        }
    }

    private void removeDisambiguation(String queryString) {
        if (disambiguationsTable == null) {
            return;
        }
        for (int i = 0; i < disambiguationsTable.getItemCount(); i++) {
            TableItem ti = disambiguationsTable.getItem(i);
            if (ti.getText(0).trim().equalsIgnoreCase(queryString)) {
                disambiguationsTable.remove(i);
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

        saveButton = new Button(bottomButtonsComposite, SWT.PUSH);
        GridData saveButtonGridData = new GridData(
            GridData.END,
            GridData.CENTER,
            false,
            false
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
        // Block save if there are invalid/incomplete dirty matching rows.
        if (!matchingIsSaveable()) {
            return false;
        }
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
        if (overridesTable != null && !overridesTable.isDisposed()) {
            for (TableItem ti : overridesTable.getItems()) {
                String from = ti.getText(0).trim();
                String to = ti.getText(1).trim();
                if (!from.isEmpty() && !to.isEmpty()) {
                    overrides.put(from, to);
                }
            }
        }
        prefs.setShowNameOverrides(overrides);

        // Show disambiguations (query string -> series id)
        Map<String, String> disambiguations = new LinkedHashMap<>();
        if (
            disambiguationsTable != null && !disambiguationsTable.isDisposed()
        ) {
            for (TableItem ti : disambiguationsTable.getItems()) {
                String from = ti.getText(0).trim();
                String to = ti.getText(1).trim();
                if (!from.isEmpty() && !to.isEmpty()) {
                    disambiguations.put(from, to);
                }
            }
        }
        prefs.setShowDisambiguationOverrides(disambiguations);

        UserPreferences.store(prefs);
        return true;
    }

    private boolean matchingIsSaveable() {
        return !hasInvalidDirtyMatchingRows();
    }

    private void updateSaveEnabledFromMatchingValidation() {
        if (saveButton == null || saveButton.isDisposed()) {
            return;
        }
        saveButton.setEnabled(matchingIsSaveable());
    }

    private boolean hasInvalidDirtyMatchingRows() {
        return (
            hasInvalidDirtyMatchingRows(overridesTable) ||
            hasInvalidDirtyMatchingRows(disambiguationsTable)
        );
    }

    private boolean hasInvalidDirtyMatchingRows(final Table table) {
        if (table == null || table.isDisposed()) {
            return false;
        }
        for (TableItem ti : table.getItems()) {
            boolean dirty = Boolean.TRUE.equals(ti.getData(MATCHING_DIRTY_KEY));
            if (!dirty) {
                continue;
            }
            String status = safeCell(ti, 2).trim();

            // Dirty rows must be validated; block save for blank/incomplete or if still validating/error.
            if (status.isEmpty()) {
                return true;
            }
            if (MATCHING_STATUS_INCOMPLETE.equals(status)) {
                return true;
            }

            org.eclipse.swt.graphics.Image img = ti.getImage(2);
            if (img == MATCHING_ICON_VALIDATING) {
                return true;
            }
            if (img == MATCHING_ICON_ERROR) {
                return true;
            }
        }
        return false;
    }

    private enum MatchingRowType {
        OVERRIDE,
        DISAMBIGUATION,
    }

    private void validateMatchingRowOnline(
        final Table table,
        final int idx,
        final MatchingRowType type
    ) {
        if (table == null || table.isDisposed()) {
            return;
        }
        if (idx < 0 || idx >= table.getItemCount()) {
            return;
        }
        final TableItem ti = table.getItem(idx);

        // Mark dirty + assign token to ignore stale validation results.
        ti.setData(MATCHING_DIRTY_KEY, Boolean.TRUE);
        final long token = ++matchingValidationSeq;
        ti.setData(MATCHING_VALIDATE_TOKEN_KEY, Long.valueOf(token));

        final String key = safeCell(ti, 0).trim();
        final String val = safeCell(ti, 1).trim();

        if (key.isEmpty() || val.isEmpty()) {
            ti.setText(2, MATCHING_STATUS_INCOMPLETE);
            updateSaveEnabledFromMatchingValidation();
            return;
        }

        // Show validating state immediately and start animation.
        setRowValidating(table, ti, token);
        updateSaveEnabledFromMatchingValidation();

        // Run provider checks off the UI thread.
        new Thread(
            () -> {
                ValidationResult result;
                try {
                    result = validateViaProvider(type, key, val);
                } catch (Exception ex) {
                    logger.log(
                        Level.INFO,
                        "Matching validation failed (exception): type=" +
                            type +
                            ", key=" +
                            key,
                        ex
                    );
                    result = ValidationResult.invalid(
                        "Cannot validate (provider error)"
                    );
                }

                final ValidationResult finalResult = result;

                Display display = (preferencesShell != null)
                    ? preferencesShell.getDisplay()
                    : Display.getDefault();

                if (display == null) {
                    return;
                }

                display.asyncExec(() -> {
                    if (table.isDisposed() || ti.isDisposed()) {
                        return;
                    }
                    Object tokObj = ti.getData(MATCHING_VALIDATE_TOKEN_KEY);
                    if (!(tokObj instanceof Long)) {
                        return;
                    }
                    long currentToken = ((Long) tokObj).longValue();
                    if (currentToken != token) {
                        // stale result; ignore
                        return;
                    }

                    if (finalResult.valid) {
                        ti.setText(2, "");
                        ti.setImage(2, MATCHING_ICON_OK);
                    } else {
                        ti.setText(2, "");
                        ti.setImage(2, MATCHING_ICON_ERROR);
                    }

                    // Store the validation message for tooltip rendering.
                    String msg = finalResult.message;
                    if (msg == null) {
                        msg = "";
                    }
                    ti.setData("tvrenamer.matching.validationMessage", msg);

                    // TableItem does not support tooltips; PreferencesDialog uses a shared tooltip label
                    // that is updated on hover (see Matching tab hover listener).
                    updateSaveEnabledFromMatchingValidation();
                });
            },
            "tvrenamer-matching-validate"
        )
            .start();
    }

    private static final class ValidationResult {

        final boolean valid;
        final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = (message == null) ? "" : message;
        }

        static ValidationResult ok(String msg) {
            return new ValidationResult(true, msg);
        }

        static ValidationResult invalid(String msg) {
            return new ValidationResult(false, msg);
        }
    }

    private ValidationResult validateViaProvider(
        final MatchingRowType type,
        final String key,
        final String val
    ) {
        if (type == MatchingRowType.DISAMBIGUATION) {
            // key=query string, val=series id
            String queryString = key;
            String seriesId = val;

            ShowName sn = ShowName.mapShowName(queryString);
            try {
                TheTVDBProvider.getShowOptions(sn);
            } catch (Exception e) {
                return ValidationResult.invalid(
                    "Cannot validate (provider unavailable)"
                );
            }

            java.util.List<ShowOption> options = sn.getShowOptions();
            if (options == null || options.isEmpty()) {
                return ValidationResult.invalid("No matches");
            }

            for (ShowOption opt : options) {
                if (opt != null && seriesId.equals(opt.getIdString())) {
                    return ValidationResult.ok("Pinned match is valid");
                }
            }
            return ValidationResult.invalid("ID not found in results");
        }

        // OVERRIDE: key=extracted show, val=replacement text
        String replacementText = val;

        // Simulate pipeline: override -> query string -> provider options -> disambiguation check
        String queryString = StringUtils.makeQueryString(replacementText);

        ShowName sn = ShowName.mapShowName(replacementText);
        try {
            TheTVDBProvider.getShowOptions(sn);
        } catch (Exception e) {
            return ValidationResult.invalid(
                "Cannot validate (provider unavailable)"
            );
        }

        java.util.List<ShowOption> options = sn.getShowOptions();
        if (options == null || options.isEmpty()) {
            return ValidationResult.invalid("No matches");
        }
        if (options.size() == 1) {
            return ValidationResult.ok("Resolves uniquely");
        }

        // Ambiguous: if a disambiguation exists and it matches a candidate, treat as valid.
        String pinnedId = prefs.resolveDisambiguatedSeriesId(queryString);
        if (pinnedId != null) {
            for (ShowOption opt : options) {
                if (opt != null && pinnedId.equals(opt.getIdString())) {
                    return ValidationResult.ok("Resolved via pinned ID");
                }
            }
        }
        return ValidationResult.invalid("Still ambiguous (would prompt)");
    }

    private void setRowValidating(
        final Table table,
        final TableItem ti,
        final long token
    ) {
        if (table.isDisposed() || ti.isDisposed()) {
            return;
        }
        // initial label
        ti.setText(2, "");
        ti.setImage(2, MATCHING_ICON_VALIDATING);

        Display display = table.getDisplay();
        display.timerExec(
            200,
            new Runnable() {
                @Override
                public void run() {
                    if (table.isDisposed() || ti.isDisposed()) {
                        return;
                    }
                    Object tokObj = ti.getData(MATCHING_VALIDATE_TOKEN_KEY);
                    if (!(tokObj instanceof Long)) {
                        return;
                    }
                    long currentToken = ((Long) tokObj).longValue();
                    if (currentToken != token) {
                        return; // replaced/stale
                    }

                    // Stop animating once validation completes (icon no longer "clock").
                    org.eclipse.swt.graphics.Image img = ti.getImage(2);
                    if (img != MATCHING_ICON_VALIDATING) {
                        return;
                    }

                    // Keep clock icon; re-schedule to continue while validating.
                    display.timerExec(200, this);
                }
            }
        );
    }

    private static String safeCell(final TableItem ti, final int col) {
        if (ti == null) {
            return "";
        }
        try {
            String s = ti.getText(col);
            return (s == null) ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
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
