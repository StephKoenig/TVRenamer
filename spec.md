# TVRenamer — Show Disambiguation: Spec for Fixes & Improvements

## Purpose

This document describes the planned fixes and improvements for the “Resolve ambiguous shows” workflow introduced recently:

1. Prevent the “Resolve ambiguous shows” dialog from popping up when it should not (e.g., the table is empty or only unambiguous files were added).
2. Improve auto-selection heuristics so that certain queries (especially those with disambiguating years in the extracted name) resolve immediately without prompting.
3. Replace retry-timer polling with a callback-driven mechanism so the UI prompts exactly when ambiguous results are enqueued.

This spec is intended to be implementation-oriented and to preserve context if we lose conversational history.

---

## Background / Current Behavior (Summary)

### Current flow (high-level)
- Provider search (TVDB v1 `GetSeries.php`) can return multiple “Series” candidates.
- If multiple candidates exist and there is no persisted disambiguation mapping for the provider query string, the lookup layer queues a pending disambiguation and returns a `FailedShow` indicating “show selection required”.
- The UI suppresses “unable to find show” for that sentinel failure and marks affected rows as `Select Show...` in the Proposed File Path column.
- Clicking a row flagged as pending opens a single “Resolve ambiguous shows” batch dialog.
- After the user chooses candidates, selections are persisted (query string → seriesid) and lookups are re-run for affected rows.

### Known issues to fix
1. **Resolve dialog can appear when it shouldn’t**:
   - Example: you delete all rows (table is empty), then add a single unambiguous file; the resolve dialog still pops due to stale pending state and/or scheduled retries.
2. **Some rows can remain stuck / stale state interactions**:
   - Particularly around clearing the table or adding new files while pending disambiguations exist.
3. **Retry timer is still in use**:
   - We intended to move to a callback model, but currently the UI uses a short retry loop to avoid missing pending disambiguations that arrive after the initial check.
4. **Auto-selection logic is insufficient**:
   - Example: extracted name `Doctor Who (2023)` can be an exact `SeriesName` match, but `FirstAired` year may be 2024 (TVDB metadata quirk). We should prioritize exact `SeriesName`/alias match over `FirstAired` year.

---

## Goals

### G1: No “ghost” Resolve dialog
The Resolve dialog must not appear if:
- the table is empty, or
- there are no pending disambiguations relevant to current rows, or
- the newly added files resolve unambiguously and there are no new pending disambiguations.

### G2: Better auto-selection
Avoid prompting when we can confidently choose:
- Exact `SeriesName` match (case-insensitive) to the extracted show name.
- Exact alias match (case-insensitive) to the extracted show name.
- Year-based heuristics only as a secondary tie-breaker.

### G3: Callback-driven prompt (no polling)
Remove retry-timer polling and instead open the Resolve dialog when the lookup layer enqueues a pending ambiguity, with UI-thread debouncing.

### G4: Maintainability & safety
- Keep behavior deterministic and testable.
- Ensure preference persistence remains robust.
- Avoid introducing new UI dead-ends (user can always reopen Resolve dialog while pending exists).

---

## Non-Goals

- Implement a “More…” expansion beyond the first 5 candidates per ambiguous show (explicitly deferred).
- Add a dedicated “?” icon for the status column (nice-to-have; not required).
- Redesign the full show mapping architecture (e.g., many-to-many inference) beyond these fixes.

---

## Definitions

- **Extracted show name**: `FileEpisode.getFilenameShow()` (human-facing name extracted from filename).
- **Provider query string**: normalized query used for provider search (e.g., via `StringUtils.makeQueryString(...)`).
- **Pending disambiguation**: a queued item requiring the user to choose among multiple candidates for a provider query string.
- **Disambiguation override**: persisted mapping (query string → provider series id) that bypasses future prompts.

---

## Planned Changes

### 1) Clear stale pending state when table becomes empty (Delete key path + Clear List path)

#### Problem
Pending disambiguations can outlive all table rows. Later actions (like adding a new, unambiguous file) can still trigger the Resolve dialog due to stale pending items and/or scheduled checks.

#### Plan
- In the table-row deletion path (Delete/Backspace key handler), after deleting selected rows:
  - If `swtTable.getItemCount() == 0`, clear pending disambiguations:
    - `ShowStore.clearPendingDisambiguations()`
  - Also clear any local per-row flags (table is empty; this is effectively automatic).
- Similarly, in the “Clear List” button action (if distinct), do the same clearing logic.

#### Additional guard
- In `showBatchDisambiguationDialogIfNeeded()`:
  - If `swtTable.getItemCount() == 0`, do **not** show the dialog.
  - Optionally clear pending disambiguations as a safety measure (since there is nothing to resolve).

#### Acceptance
- Delete the last row; then add one unambiguous file → no Resolve dialog appears.

---

### 2) Implement improved auto-selection heuristics (pre-queue)

#### Problem
Some queries that should resolve immediately still prompt (e.g., `Doctor Who (2023)`), often because `FirstAired` year doesn’t match the year in the title.

#### Plan: Selection priority order
Given:
- extracted show name `E` (user-facing extracted name)
- provider candidates `C[]` (each includes `SeriesName`, `seriesid`, optional `aliasNames`, optional `firstAiredYear`)

Select automatically if exactly one match is found in the earliest applicable rule:

1. **Exact SeriesName match (case-insensitive)**:
   - If `candidate.SeriesName.equalsIgnoreCase(E)`, choose it.
2. **Exact alias match (case-insensitive)**:
   - If any alias equals `E`, choose that candidate.
3. **Year heuristics (secondary)**:
   - If `E` contains a `(YYYY)` year token:
     - Prefer a candidate where:
       - `candidate.SeriesName` contains `(YYYY)` OR
       - any alias contains `(YYYY)` OR
       - `candidate.firstAiredYear == YYYY`
   - Choose only if the match is unique.

If no unique selection is found, then queue pending disambiguation and rely on user input.

#### Acceptance
- For `Doctor Who (2023)` where `SeriesName` is exactly `Doctor Who (2023)` but `FirstAired` is 2024:
  - Auto-select the exact `SeriesName` match and do not prompt.

---

### 3) Replace retry-timer polling with callback notifications

#### Problem
We currently use a retry timer loop to check for pending disambiguations because provider lookups complete asynchronously. Polling causes:
- ghost dialogs if stale pending exists,
- extra complexity and delay,
- subtle timing bugs.

#### Plan
Introduce a listener/callback mechanism in the lookup layer:

- Add in `ShowStore`:
  - A listener registry for “pending disambiguations changed”.
  - Public methods:
    - `addPendingDisambiguationListener(Listener l)`
    - `removePendingDisambiguationListener(Listener l)`
- In `queuePendingDisambiguation(...)`:
  - If a new pending entry is actually added (not already present), notify listeners.

In `ResultsTable`:
- Register a listener once (during initialization).
- Listener schedules UI-thread work:
  - `display.asyncExec(() -> showBatchDisambiguationDialogIfNeeded())`
- Add debouncing / “dialog open” guard:
  - If the dialog is already open, do nothing.
  - If no pending exists, do nothing.

Remove:
- Timer retry logic entirely.

#### Acceptance
- No timer retry scheduling remains.
- The batch dialog appears shortly after ambiguities are enqueued (driven by the callback).
- No dialog appears when no ambiguities exist.

---

### 4) “Select Show…” UI state robustness (keep clickable)

#### Current intent
Rows blocked on disambiguation should show `Select Show...` in the Proposed File Path column and be clickable to reopen the batch dialog.

#### Plan
- Continue using a per-row flag stored on the `TableItem` (e.g., `tvrenamer.selectShowPending`).
- Ensure that:
  - recomputation paths do not permanently remove clickability (click checks the flag),
  - `markAllSelectShowPending()` re-applies labels based on pending query strings.

This is already partially implemented but should remain consistent after callback changes.

---

## Testing Plan (Manual)

1. **Unambiguous add after empty table**
   - Add a single unambiguous show → no Resolve dialog.
2. **Ambiguous add, cancel, reopen**
   - Add ambiguous show(s) → Resolve dialog appears.
   - Cancel → rows show `Select Show...`.
   - Click row → Resolve dialog reopens.
3. **Exact SeriesName match with year**
   - Add file with extracted show `Doctor Who (2023)` → should auto-resolve without dialog.
4. **Delete last row clears pending**
   - Add ambiguous show(s) → pending exists.
   - Delete all rows via Delete key → table empty.
   - Add unambiguous file → Resolve dialog does not appear.
5. **Clear List button parity**
   - Repeat the above using Clear List (if present) to ensure same behavior.

---

## Open Questions / Nice-to-haves

- Add a dedicated “?” icon for the status column to accompany `Select Show...`.
- Add an explicit toolbar/menu action “Resolve ambiguous shows…” that opens the batch dialog when pending exists.
- Consider grouping logic for candidates beyond top-5 limitation (explicitly deferred).

---