# Completed (TVRenamer)

This document is a durable record of completed work: what shipped, why it mattered, and where it lives in the codebase.

It exists to keep `docs/TODO.md` focused on *future work* while preserving the engineering context and implementation details of finished items.

## How to use this file

When you complete an item that was tracked in `docs/TODO.md`:

1. Remove or mark it as completed in `docs/TODO.md` (keep the TODO list clean).
2. Add a new entry here under **Completed Items**, using the template below.
3. If the work changes assumptions (threading, UI thread ownership, encoding rules, persistence keys, etc.), capture that in the entry.

### Entry template

- **Title:** concise name
- **Why:** user impact / risk / motivation
- **Where:** key classes/files (paths helpful)
- **What we did:** bullet list of notable changes / behavior
- **Notes:** (optional) constraints, gotchas, follow-ups, links to specs/release notes

---

## Completed Items

### 1) Make file modification-time behavior configurable
- **Why:** Renaming/moving a file doesn’t change its contents; default behavior should preserve timestamps. Users may prefer setting mtime to “now”.
- **Where:** `org.tvrenamer.controller.FileMover` (`finishMove(...)`) + Preferences UI/prefs model
- **What we did:**
  - Added a preference to preserve original mtime by default.
  - Added an option to set mtime to “now”.

### 2) Fix Preferences dialog token drop insertion position
- **Why:** Drag/drop should insert at caret (or replace selection), not always append.
- **Where:** `org.tvrenamer.view.PreferencesDialog` — `PreferencesDropTargetListener.drop(...)`
- **What we did:**
  - Insert dropped token at current selection/caret and move caret to end of inserted token.

### 3) Thread the preload folder scan
- **Why:** Folder scanning can block UI responsiveness during startup.
- **Where:** `org.tvrenamer.model.EpisodeDb` — `preload()`
- **What we did:**
  - Run preload scanning on a background thread.
- **Notes:**
  - `publish(...)` notifies listeners from that background thread; UI code must marshal to SWT UI thread.

### 4) Harden XPath usage for potential concurrency
- **Why:** Shared `XPath` instances are not guaranteed to be thread-safe.
- **Where:** `org.tvrenamer.controller.util.XPathUtilities`
- **What we did:**
  - Replaced shared static `XPath` with `ThreadLocal<XPath>`.

### 5) Generalize “map to list” helper in MoveRunner
- **Why:** Reduce boilerplate and prefer standard library constructs.
- **Where:** `org.tvrenamer.controller.MoveRunner`
- **What we did:**
  - Replaced custom “get list or create” logic with `Map.computeIfAbsent(...)`.

### 6) Stabilize Windows permission-related tests
- **Why:** Windows “read-only” simulation is unreliable without ACL tooling; tests should not flake.
- **Where:** `org.tvrenamer.controller.TestUtils.setReadOnly(Path)` and move-related tests
- **What we did:**
  - Adopted a pragmatic “verify + skip” strategy when read-only cannot be reliably enforced.
  - Updated move tests to match default mtime preservation.

### 7) Make string handling more explicit (URL vs XML vs display vs filename)
- **Why:** Mixing responsibilities can corrupt provider XML and break URLs/filenames in subtle ways.
- **Where:** `org.tvrenamer.controller.util.StringUtils` and `org.tvrenamer.controller.TheTVDBProvider`
- **What we did:**
  - Use robust URL encode/decode for query parameters.
  - Stop mutating downloaded XML payloads.
  - Treat “special character encoding” as conservative display normalization only.

### 8) Improve move/copy throughput and progress reporting
- **Why:** Copy+delete can be slow; overall progress should be smooth and accurate for multi-file batches.
- **Where:** `org.tvrenamer.controller.util.FileUtilities.copyWithUpdates(...)`, `org.tvrenamer.view.FileMonitor`, `org.tvrenamer.view.ResultsTable`, `org.tvrenamer.view.ProgressBarUpdater`
- **What we did:**
  - Increased copy buffer to 4 MiB and throttled UI progress callbacks to ~4 MiB.
  - Implemented byte-accurate aggregate progress for copy+delete moves only, so the bottom bar advances smoothly across the entire batch and resets after completion.

### 9) Batch “Select Shows” dialog improvements (checkbox selection + streaming ambiguities)
- **Why:** Avoid repeated modal popups; allow partial resolution; make selection UX reliable; reduce blocking.
- **Where:** `org.tvrenamer.view.BatchShowDisambiguationDialog`, `org.tvrenamer.view.ResultsTable`, `org.tvrenamer.model.ShowStore`, `agents.md`
- **What we did:**
  - Reworked candidate selection to use checkboxes (single-choice or none) with row-click toggling.
  - Enabled OK when at least one show is selected (partial resolution).
  - Streamed newly discovered ambiguous shows into an already-open dialog and added a “Downloading …” title indicator.
  - Kept unresolved pending items queued so the dialog can be reopened via the button.
  - Adjusted auto-open behavior to trigger only on an empty→non-empty pending transition (otherwise rely on streaming / explicit button).

### 10) Unified Matching tab (Overrides + Disambiguations) with online validation
- **Why:** Users need one place to view/edit/delete/validate all show-matching rules; reduces confusion and makes troubleshooting easier.
- **Where:** `org.tvrenamer.view.PreferencesDialog` (Matching tab), `org.tvrenamer.model.UserPreferences`, `org.tvrenamer.model.ShowStore`, `docs/Unifying Matches Spec.md`
- **What we did:**
  - Renamed the Preferences “Overrides” tab to “Matching”.
  - Added two editors (Overrides and Disambiguations) using tables.
  - Implemented threaded online validation (TVDB) for new/changed entries with Save gating.
  - Added status icons consistent with the main results table and per-table validation message display.
  - Added Clear All confirmations and adjusted column ordering for cleaner alignment.

### 11) Unified show selection evaluator (runtime + Matching validation)
- **Why:** Avoid drift between runtime selection logic and Preferences validation; reduce duplicated logic; make selection deterministic and explainable.
- **Where:** `org.tvrenamer.model.ShowSelectionEvaluator`, `org.tvrenamer.model.ShowStore`, `org.tvrenamer.view.PreferencesDialog`, `docs/Unified Evaluator Spec.md`
- **What we did:**
  - Introduced a pure evaluator that returns `RESOLVED / AMBIGUOUS / NOT_FOUND` with an explainable reason.
  - Wired runtime selection and Matching-tab override validation to use the evaluator.
  - Implemented deterministic tie-breakers (conservative, spec-driven), including:
    - Prefer base title over parenthetical variants when the base exists.
    - Prefer strict canonical token match over candidates with extra tokens.
    - Prefer `FirstAiredYear ± 1` when extracted includes a year token.
- **Notes:**
  - Further tie-breaker expansion is intentionally deferred; track in `docs/TODO.md`.

### 12) Matching tab: fixed Save gating + persistence
- **Why:** Users must be able to save validated rows; Save should not be blocked incorrectly; edits must persist.
- **Where:** `org.tvrenamer.view.PreferencesDialog`
- **What we did:**
  - Fixed Save gating logic where blank status text (icon-only UI) incorrectly blocked Save even when rows were OK.
  - Fixed persistence logic to save the correct key/value columns (status icon column is not data).

### 13) Overrides now apply to provider lookup consistently (without losing extracted name)
- **Why:** Overrides should affect runtime provider queries the same way they validate in Preferences; preserve visibility of what was parsed from filenames.
- **Where:** `org.tvrenamer.model.FileEpisode`, `org.tvrenamer.controller.FilenameParser`, `org.tvrenamer.view.ResultsTable`
- **What we did:**
  - Split “extracted show name” from “effective lookup show name”.
  - Use overrides for the effective provider lookup name.
  - Preserve extracted show name for UI correlation and disambiguation flows.

### 14) Repo hygiene: removed legacy IDE/build artifacts from `etc/`
- **Why:** Reduce maintenance burden and confusion; avoid keeping obsolete Ant/Ivy-era scripts/configs in a Gradle-based repo.
- **Where:** `etc/`
- **What we did:**
  - Removed legacy IntelliJ template files under `etc/idea`.
  - Removed legacy Ant/Ivy-era run scripts and unused config/profile XMLs under `etc/`.
  - Removed unused constant referencing deleted default overrides file.
- **Notes:**
  - A periodic “legacy reference scan” is tracked in `docs/TODO.md` as a hygiene step.



### 15) CI artifact naming: publish stable + versioned fat JARs
- **Why:** Users and scripts benefit from a stable filename (`tvrenamer.jar`), while Releases and side-by-side installs benefit from a versioned filename. CI artifacts should include both to reduce confusion and simplify testing.
- **Where:** `build.gradle` (Shadow outputs), `.github/workflows/windows-build.yml` (artifact upload paths)
- **What we did:**
  - Produce a stable fat jar named `tvrenamer.jar`.
  - Produce an additional versioned fat jar alongside it (e.g., `tvrenamer-<commitCount>.jar`).
  - Upload both jars in CI artifacts so users can choose either.
- **Notes:**
  - Releases should prefer attaching the versioned jar to make the downloaded filename self-describing.

### 16) Hygiene: scanned for legacy Ant/Ivy/out/lib references
- **Why:** After removing legacy scripts/configs, ensure no stale references remain in docs or code.
- **Where:** Repo-wide scan (docs, scripts, configs, source).
- **What we did:**
  - Scanned for `ant`, `ivy`, `build.xml`, `out/`, `lib/` references.
  - Updated stale comment in `logging.properties` that referenced `build.xml` to reference Gradle instead.
  - Confirmed no legacy directories or build files remain.

### 17) Support multi-episode filename parsing (single file contains multiple episodes)
- **Why:** Many libraries include “double episodes” or “range episodes” in a single file. Without explicit support, parsing/renaming is confusing and users may worry the app will rename/move incorrectly.
- **Where:** `org.tvrenamer.controller.FilenameParser`, `org.tvrenamer.model.FileEpisode`, `org.tvrenamer.controller.FilenameParserTest`
- **What we did:**
  - Added parsing support for case-insensitive multi-episode patterns in a single filename:
    - `S01E04E05` / `S01E04E05E06` (explicit list)
    - `S02E04-E06` and `S02E04-06` (inclusive range)
  - When detected, TVRenamer selects the lowest episode (A) for lookup/matching and stores the `(A..B)` span on the `FileEpisode`.
  - Appends a compact suffix `"(A-B)"` (no leading zeros) to the episode title token used for rename output, e.g. `Silver Linings (1-7)`.

### 18) Add SpotBugs static analysis (local)
- **Why:** Catch likely bugs and suspicious patterns early, without turning style discussions into review churn.
- **Where:** `build.gradle`, `agents.md`
- **What we did:**
  - Added SpotBugs integration and documented how to run it locally (`spotbugsMain`) and where the HTML report is generated.
  - Made the initial configuration conservative and non-blocking while we evaluate signal vs noise.

### 19) Release asset hygiene: avoid stale jars in GitHub Releases
- **Why:** Local builds can accumulate older versioned jars under `build/libs/` (especially when `clean` fails due to Windows file locks). Uploading `build/libs/*.jar` can unintentionally attach stale jars to a release.
- **Where:** Release procedure in `agents.md`
- **What we did:**
  - Documented a safer release upload approach: prefer CI artifacts and/or ensure `build/libs/` is clean, and upload explicit jar filenames to avoid stale versioned jars.

### 20) Code consolidation and modernization (Refactor and Consolidate)
- **Why:** Improve maintainability, reduce code duplication, and modernize patterns for easier future development.
- **Where:** Multiple files across `controller`, `model`, `view`, and `controller/util` packages
- **What we did:**
  - Removed deprecated `loggingOff()`/`loggingOn()` methods from `FileUtilities` (unused legacy code)
  - Consolidated duplicate `safePath(Path)` method: made `FileUtilities.safePath()` public and removed duplicate from `FileMover`
  - Moved magic constants (`NO_FILE_SIZE`, `MAX_TITLE_LENGTH`) from `FileEpisode` to `Constants.java`
  - Removed unused `FileStatus` enum and `fileStatus` field from `FileEpisode` (was set but never read; `currentPathMatchesTemplate` retained as it is used)
  - Improved `UpdateChecker` thread safety: replaced mutable static fields with `AtomicReference<VersionCheckResult>` using a record for immutable result caching; narrowed `catch (Exception)` to `catch (RuntimeException)`
  - Removed dead code from `BatchShowDisambiguationDialog`: unused `logger` field, unused `closedViaWindowX` field (set but never read), unused `allResolved()` method
- **Notes:**
  - `UpdateChecker` now uses lock-free compare-and-set pattern for thread-safe lazy initialization
  - `MoveRunner.existingConflicts()` intentionally retained as infrastructure for future conflict detection (TODO item #2)

### 21) File move enhancements: progress feedback, overwrite option, duplicate cleanup, fuzzy matching
- **Why:** Improve user experience and file management during move/rename operations.
- **Where:** `org.tvrenamer.view.ResultsTable`, `org.tvrenamer.view.ItemState`, `org.tvrenamer.model.UserPreferences`, `org.tvrenamer.view.PreferencesDialog`, `org.tvrenamer.controller.FileMover`, `org.tvrenamer.controller.MoveRunner`, `org.tvrenamer.controller.util.FileUtilities`, `org.tvrenamer.controller.FilenameParser`
- **What we did:**
  1. **Progress tick after completion:** Added `COMPLETED` ItemState with checkmark icon; successful moves now briefly show the checkmark before row is cleared (500ms delay).
  2. **Always overwrite preference:** Added preference to overwrite existing destination files instead of creating versioned suffixes (1), (2). Implemented in FileMover (same-disk rename uses `REPLACE_EXISTING`, cross-disk copy deletes first) and MoveRunner (skips conflict resolution when enabled). **Bug fix:** The early existence check in `tryToMoveFile()` was failing immediately when the destination existed, without checking the overwrite preference. Fixed to allow the move to proceed when overwrite is enabled.
  3. **Duplicate cleanup:** Added preference to delete duplicate video files after successful move. Uses both base-name matching (same name, different extension) and fuzzy episode matching (same season/episode identity). Only video files are deleted (not subtitles like .srt, .sub, .idx). Helper `FileUtilities.deleteDuplicateVideoFiles()` with 15 video extension types.
  4. **Fuzzy episode matching:** Added `FilenameParser.extractSeasonEpisode()` for lightweight season/episode extraction; integrated into both MoveRunner conflict detection and duplicate cleanup to catch files like "S01E02" vs "1x02" that represent the same episode but have different filenames.
- **Notes:**
  - Overwrite and duplicate cleanup preferences default to false (safe behavior).
  - Fuzzy matching supplements base-name matching in both conflict detection and duplicate cleanup.
  - Duplicate cleanup deletes files (relies on OS recycle bin) rather than moving to a subfolder to avoid orphaned folders and media library confusion.

### 22) Tick display fix and duplicate cleanup dialog with user confirmation
- **Why:** Progress tick (checkmark) wasn't visible after copy+delete moves; duplicate cleanup should require user confirmation before deleting files.
- **Where:** `org.tvrenamer.view.FileMonitor`, `org.tvrenamer.view.ResultsTable`, `org.tvrenamer.view.DuplicateCleanupDialog`, `org.tvrenamer.controller.MoveRunner`
- **What we did:**
  1. **Tick display fix:** The progress label (shown during copy operations) used a TableEditor overlay. When the label was disposed, the TableEditor was not disposed, preventing the underlying checkmark icon from showing. Fixed by tracking and disposing both the Label and TableEditor via a new `ProgressLabelResult` record.
  2. **Duplicate cleanup dialog:** Instead of auto-deleting duplicates, now shows a modal dialog after all moves complete:
     - Lists duplicate files in a table with checkboxes (checked = will be deleted)
     - Shows filename and folder columns
     - "Select All" / "Select None" buttons for convenience
     - "Delete Selected" commits deletions; "Cancel" keeps all files
  3. **Aggregate duplicates in MoveRunner:** Added `movers` list to track FileMover instances, `aggregateDuplicates()` method called when all moves complete, and `getFoundDuplicates()` getter for the UI.
  4. **Tick icon consistency:** Removed explicit COMPLETED icon setting after move. The existing SUCCESS icon (ready to rename) now remains visible, ensuring consistent visual feedback.
  5. **Move order:** Fixed moves to execute in table display order (top to bottom). Added `ensureTableSorted()` which applies the current sort column before collecting moves, ensuring items are in the visual order the user sees. MoveRunner now submits moves in original list order instead of arbitrary HashMap iteration order.
- **Notes:**
  - Duplicate cleanup preference must be enabled for duplicates to be detected and shown.
  - Files are only deleted after explicit user confirmation via the dialog.

### 23) JUnit 5 (Jupiter) migration
- **Why:** Modernize test framework to current JUnit 5 (Jupiter) from legacy JUnit 4; benefit from improved annotations, extension model, and better IDE/tooling support.
- **Where:** `build.gradle`, `gradle/libs.versions.toml`, all test files under `src/test/java/org/tvrenamer/`
- **What we did:**
  - Updated `libs.versions.toml`: replaced JUnit 4.13.2 with JUnit 5.11.4 (`junit-jupiter`)
  - Updated `build.gradle`: changed `testImplementation` to use JUnit 5, added `useJUnitPlatform()` to both `test` and `integrationTest` tasks, added `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`
  - Migrated 8 test files with annotation and import changes:
    - `@BeforeClass` → `@BeforeAll`, `@AfterClass` → `@AfterAll`
    - `@Before` → `@BeforeEach`, `@After` → `@AfterEach`
    - `@Ignore` → `@Disabled`
    - `org.junit.*` → `org.junit.jupiter.api.*`
  - Converted `@Rule TemporaryFolder` to `@TempDir Path` annotation (JUnit Jupiter idiom)
  - Reordered assertion parameters (message argument moved from first to last position)
  - Regenerated Gradle dependency lock files with `--write-locks`
- **Notes:**
  - The original TODO.md mentioned "JUnit 6" but that was a mislabel; the described changes (BeforeAll, jupiter imports, etc.) are JUnit 5 features.
  - All tests pass after migration.

### 24) Unit tests for ShowSelectionEvaluator
- **Why:** Prevent regressions in critical show-matching behavior; improve code confidence for future heuristic changes.
- **Where:** `src/test/java/org/tvrenamer/model/ShowSelectionEvaluatorTest.java`
- **What we did:**
  - Created comprehensive test suite with 30+ test cases covering:
    - NOT_FOUND scenarios (null/empty candidates)
    - Pinned ID resolution
    - Exact name matching (case-insensitive)
    - Punctuation-normalized matching
    - Alias matching
    - Parenthetical variant tie-breaker ("Show" vs "Show (IN)")
    - Token set and year tolerance (±1) tie-breakers
    - Single candidate auto-resolution
    - Ambiguous multi-candidate scenarios
    - Edge cases (nulls, blanks, null names in options)
    - Priority ordering (pinned ID > exact name > alias)

### 25) Narrow overly broad `catch (Exception)` blocks
- **Why:** Improve code robustness by catching specific exception types; make error handling more explicit and maintainable.
- **Where:** Multiple files across `controller`, `model`, and `view` packages
- **What we did:**
  - Narrowed `Exception` to `NumberFormatException` in:
    - `UpdateChecker.java:188` (version parsing)
    - `FileEpisode.java:356,361` (season/episode number parsing)
    - `ShowOption.java:91` (show ID parsing)
    - `ShowSelectionEvaluator.java:400` (year parsing)
  - Narrowed `Exception` to `SWTException` in:
    - `ThemeManager.java:166,171` (TabFolder color setters)
  - Used multi-catch for reflection exceptions in:
    - `CocoaUIEnhancer.java:121,287,301` (`NoSuchMethodException | IllegalAccessException | InvocationTargetException`)
  - Added justification comments to intentionally broad catches in `ShowSelectionEvaluator.java` (defensive for external data/utility calls)
- **Notes:**
  - Thread safety nets (top-level UI, background threads) intentionally kept broad
  - Platform compatibility catches (best-effort UI features) documented with comments

### 26) Improve handling of unparsed files (parse failure diagnostics)
- **Why:** Parse failures are a common frustration; users need actionable feedback about WHY parsing failed.
- **Where:** `org.tvrenamer.controller.FilenameParser`, `org.tvrenamer.model.FileEpisode`, `org.tvrenamer.view.ResultsTable`, `org.tvrenamer.model.util.Constants`
- **What we did:**
  - Added `ParseFailureReason` enum to `FilenameParser` with 4 specific failure reasons:
    - `NO_SHOW_NAME` - Could not extract show name from filename
    - `NO_SEASON_EPISODE` - No season/episode pattern found (e.g., S01E02, 1x03)
    - `FILENAME_TOO_SHORT` - Filename too short to parse (< 4 chars)
    - `NO_ALPHANUMERIC` - Filename contains no recognizable text
  - Added `parseFailureReason` field and getter to `FileEpisode`
  - Updated `setFailToParse()` to accept and store a specific reason
  - Added diagnostic logic to `FilenameParser.parseFilename()`:
    - Early validation for too-short/no-alphanumeric filenames
    - `diagnoseFailure()` helper to determine specific failure reason
    - `containsEpisodePattern()` helper to distinguish "no episode pattern" from "no show name"
  - Added summary dialog in `ResultsTable.addEpisodes()`:
    - Tracks parse failures by reason during batch processing
    - Shows non-blocking summary dialog after all files processed (if failures exist)
    - Displays success count, failure count, and breakdown by failure reason
  - Added `PARSE_SUMMARY_TITLE` constant for i18n readiness
- **Notes:**
  - Failure reason is displayed in the "Proposed File Path" column via `getReplacementText()`
  - Summary dialog is non-blocking (shown after parsing completes, not during)
  - All user-facing strings centralized in `Constants.java` for future localization

### 27) MKV metadata tagging via mkvpropedit
- **Why:** Extend metadata tagging to MKV files; MKV is a common container format and users benefit from embedded metadata for media managers.
- **Where:** `org.tvrenamer.controller.metadata.MkvMetadataTagger`, `org.tvrenamer.controller.metadata.MetadataTaggingController`, `org.tvrenamer.model.util.Constants`
- **What we did:**
  - Created `MkvMetadataTagger` implementing `VideoMetadataTagger` interface
  - Uses mkvpropedit CLI from MKVToolNix (external dependency)
  - Detection strategy: checks PATH, Windows Program Files, macOS Homebrew locations
  - Gracefully skips if mkvpropedit not installed (not an error)
  - Writes Matroska tags via XML file at three target levels:
    - Target 70 (Collection): `TITLE`, `COLLECTION`, `CONTENT_TYPE`
    - Target 60 (Season): `PART_NUMBER`, `TITLE`
    - Target 50 (Episode): `TITLE`, `PART_NUMBER`, `DATE_RELEASED`, `DATE_RECORDED`
  - Sets segment `title` (via `--edit info --set title=...`) to filename without extension for display name compatibility
  - Registered in `MetadataTaggingController` alongside `Mp4MetadataTagger`
  - Updated preferences tooltip to mention MKV support and mkvpropedit requirement
- **Notes:**
  - Supported extensions: `.mkv`, `.webm`
  - Requires MKVToolNix installed; detection is cached at startup
  - Proper XML escaping for show/episode titles containing special characters
  - 30-second process timeout with proper cleanup
  - See `docs/Tagging Spec.md` for detailed format documentation

### 28) Embedded help system with static HTML pages
- **Why:** The Help menu existed but was unwired; users need guidance without searching GitHub issues or releases.
- **Where:** `src/main/resources/help/` (HTML files), `org.tvrenamer.controller.HelpLauncher`, `org.tvrenamer.view.UIStarter`
- **What we did:**
  - Created 8 HTML help pages covering all major features:
    - `index.html` - Table of contents and overview
    - `getting-started.html` - First launch and quick start guide
    - `adding-files.html` - Drag-drop, file dialog, preload folder
    - `renaming.html` - Format tokens, customization, conflict handling
    - `preferences.html` - All preferences explained
    - `show-matching.html` - Overrides, disambiguations, troubleshooting matches
    - `metadata-tagging.html` - MP4/MKV tagging, MKVToolNix requirements
    - `troubleshooting.html` - Common issues and debug logging
  - Created `style.css` with light/dark mode support (respects system preference)
  - Created `HelpLauncher` class to extract help from JAR to temp directory and open in browser
  - Wired Help menu item with F1 keyboard shortcut
  - Added documentation maintenance reminder to `agents.md` release process
- **Notes:**
  - Help is embedded in the JAR for offline access
  - Extraction is cached per session for performance
  - Opens in system default browser via `Program.launch()`
  - Temp files marked for deletion on JVM exit

### 29) Extract EpisodeReplacementFormatter from FileEpisode
- **Why:** `FileEpisode` had grown large (~1400 lines); extracting formatting logic improves maintainability and testability.
- **Where:** `org.tvrenamer.model.EpisodeReplacementFormatter` (new), `org.tvrenamer.model.FileEpisode` (refactored)
- **What we did:**
  - Created `EpisodeReplacementFormatter` with extracted static formatting methods:
    - `format()` (was `plugInInformation`) — main token replacement
    - `substituteAirDate()` (was `plugInAirDate`) — date token handling
    - `removeTokens()` — helper for null date cases
  - Modernized the formatting logic:
    - Changed `replaceAll()` to `replace()` — tokens are literals, not regex; eliminates need for `Matcher.quoteReplacement()` and reduces overhead
    - Cached `DateTimeFormatter` instances as static finals (expensive to create)
  - Cleaned up `FileEpisode`:
    - Removed unused `FailureReason` enum (defined but never referenced)
    - Removed unused `setConflict()` method (defined but never called)
    - Simplified `checkFile()` by removing no-op method calls
    - Cleaned up spurious blank lines in multi-line comments
    - Simplified Integer auto-unboxing (`multiEpisodeEnd >= multiEpisodeStart`)
  - Reduced `FileEpisode` from ~1400 to ~1210 lines
- **Notes:**
  - External API unchanged; `setNoFile()`, `setFileVerified()`, `setMoving()` retained as no-ops for API compatibility (called from FileMover)
  - All tests pass after refactoring

### 30) String encoding cleanup and TrickyTitlesTest
- **Why:** Historical code mixed URL encoding, XML post-processing, and filename sanitization in confusing ways. Unused/deprecated methods cluttered `StringUtils`.
- **Where:** `org.tvrenamer.controller.util.StringUtils` (cleaned), `TrickyTitlesTest` (new), `docs/Strings Spec.md` (new)
- **What we did:**
  - Created `docs/Strings Spec.md` documenting encoding responsibilities:
    - URL encoding (query parameters)
    - Filename sanitization (illegal Windows chars)
    - XML handling (never mutate downloaded payloads)
  - Removed unused/deprecated methods from `StringUtils`:
    - `toUpper()` — unused
    - `encodeUrlCharacters()` / `decodeUrlCharacters()` — deprecated wrappers
    - `encodeSpecialCharacters()` / `decodeSpecialCharacters()` — confusing legacy methods
    - Logger field (only used by removed methods)
  - Created `TrickyTitlesTest.java` with 20 tests covering real-world edge cases:
    - Mission: Impossible - Fallout (2018), V/H/S, ? (2021), "What" (2013)
    - S.W.A.T. (2017), *batteries not included, Woodstock '99
    - Unicode characters (em-dash, curly quotes, ellipsis, accented letters)
  - Updated `StringUtilsTest.java` to remove tests for deleted methods
- **Notes:**
  - All encoding responsibilities are now documented in one place
  - Test coverage for tricky titles catches regressions
  - See `docs/Strings Spec.md` for the complete specification

### 31) Preferences dialog drag/drop UX enhancements
- **Why:** Original token list lacked visual affordance; caret didn't track during drag; no click-to-insert option; no preview of format result.
- **Where:** `org.tvrenamer.view.PreferencesDialog` (refactored), `org.tvrenamer.model.util.Constants` (tooltips)
- **What we did:**
  - **Visual feedback during drag:** Added `dragOver()` handler that moves caret to track mouse position, showing the insertion point before drop.
  - **Pill-styled tokens using Canvas:** Replaced plain list items with custom-painted Canvas widgets:
    - Light blue rounded background (RGB 200, 220, 255, 3px corner radius)
    - Darker blue border during drag (RGB 100, 150, 220) for visual feedback
    - Hand cursor indicates interactivity
    - Each token is individually draggable
    - Uses Canvas+PaintListener for reliable background color on Windows (Label.setBackground unreliable)
  - **Vertical layout:** One pill per line using `RowLayout(SWT.VERTICAL)` for narrower dialog.
  - **Click-to-insert:** Added mouse listener so clicking a token inserts it at caret without dragging.
  - **Live preview:** Added preview label below format text field showing real-time result with sample data:
    - Show: "Rover", Season 2, Episode 5, Title: "The Squirrels Episode"
    - Resolution: "720p", Air date: April 26, 2009
    - Updates on every keystroke/drop via ModifyListener
  - **Layout restructure:** Moved "Rename Tokens" title above the token row (was inline) for clearer visual hierarchy.
  - **Tooltip formatting:** Added line breaks to long tooltips for ~70-char max width including bullet points.
  - **Code cleanup:** Removed unused `PreferencesDragSourceListener` class and `addStringsToList()` method; removed unused imports.
- **Notes:**
  - Caret position estimation uses font metrics with GC for reasonably accurate character offset calculation.
  - Sample data uses fictional show to avoid trademark concerns.

---

## Related records

- Per-release notes are stored as versioned Markdown files:
  - `docs/release-notes-v1.0.<commitCount>.md`
- Specs:
  - `docs/Unified Evaluator Spec.md`
  - `docs/Unifying Matches Spec.md`
  - `docs/Strings Spec.md`
