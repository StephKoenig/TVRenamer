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

---

## Related records

- Per-release notes are stored as versioned Markdown files:
  - `docs/release-notes-v1.0.<commitCount>.md`
- Specs:
  - `docs/Unified Evaluator Spec.md`
  - `docs/Unifying Matches Spec.md`
