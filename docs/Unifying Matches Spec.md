# Unifying Matches Spec (Overrides + Disambiguations)

## Goal

Unify the two existing “show matching” mechanisms into one coherent user-facing editing experience under the Preferences **Matching** tab (implemented):

1. **Overrides**: `extracted show name -> replacement show text`
2. **Disambiguations**: `provider query string -> provider series id` (TVDB today)

Users should be able to **view, add, edit, delete** these rules in one place, and **validate** new/changed entries so the future filename processing pipeline becomes smoother (i.e., does not require the Select Shows dialog for those names).

This spec intentionally describes *behavior + UX + persistence* without prescribing a particular UI widget toolkit beyond SWT.

---

## Non-goals (for this iteration)

- Do **not** introduce a new rule type for `extracted show -> series id` (“pin-by-name”). This is a future enhancement.
- Do **not** add support for additional providers (TMDB) yet. Provider is implicit (TVDB).
- Do **not** redesign the Select Shows dialog; we only ensure the rules editor complements it.

---

## Current state (baseline)

### Existing rule stores

- **Overrides** exist as a user preference mapping show name strings to show name strings and are applied during parsing / pre-provider steps.
- **Disambiguations** exist as a user preference mapping `query string -> series id` and are used during provider selection to avoid prompting.

### Existing pipeline (conceptual)

1. Parse file → extract show name text.
2. Apply Overrides (if any).
3. Compute provider query string (normalization).
4. Provider search returns candidates.
5. If disambiguation exists for query string and id is present in candidates → select it.
6. Else if ambiguous → prompt via Select Shows dialog.

---

## UX Changes

### Preferences tab naming

- Rename existing Preferences tab **Overrides** to **Matching**.
- Keep the tab location/order unchanged to reduce user confusion.

### Layout: Option B (two tables)

The Matching tab contains **two tables**:

1. **Overrides table**
2. **Disambiguations table**

The dialog/window may need to be slightly taller/wider to fit both tables comfortably.

#### Overrides table

Columns:

- **Status** (icon; first column)
- **Extracted show**
- **Replace with**

Editing model:

- Entry text boxes above the table.
- Clicking a row copies its values into the entry boxes.
- **Add / Update** commits changes:
  - if a row is selected, it updates that row
  - otherwise it upserts by key (case-insensitive) or inserts a new row

Row controls:

- Add / Update
- Remove
- Clear All (with confirmation)

#### Disambiguations table

Columns:

- **Status** (icon; first column)
- **Query string**
- **Series ID**

Editing model:

- Entry text boxes above the table.
- Clicking a row copies its values into the entry boxes.
- **Add / Update** commits changes:
  - if a row is selected, it updates that row
  - otherwise it upserts by key (case-insensitive) or inserts a new row

Row controls:

- Add / Update
- Remove
- Clear All (with confirmation)

#### Save / Apply behavior

- The UI edits an in-memory copy of the stored rules.
- Rows are tracked as **dirty** (new or changed).
- Dirty rows must be validated before Save is allowed.
- **Save** is disabled if **any dirty row** is invalid (❌).
- **Existing non-dirty rows** do not block saving even if they would no longer validate (we don’t revalidate everything on load).

---

## Validation Requirements

### Definition of “Valid”

A rule (or set of rules) is **Valid** if, when applied through the normal TVRenamer show-resolution pipeline, the result would **not require the Select Shows disambiguation dialog** for that extracted show (i.e., the pipeline resolves without user selection).

This is a **pipeline-level validation**, not a simple syntax check.

### What to validate

- Validate **new rows** and **changed rows** only.
- Validation occurs when the user **commits** a change via **Add / Update**.
- Existing rows loaded from preferences are treated as “OK” by default and are not revalidated automatically.

### Validation is online (connected)

- Validation assumes online connectivity and provider availability.
- If provider is unavailable (timeout / API discontinued), validation should mark the row as ❌ with a clear message such as:
  - `Cannot validate (provider unavailable)`
- Save remains disabled for dirty rows that are invalid due to inability to validate.

### Validation threading + UI feedback

- Validation runs off the SWT UI thread (background thread) and posts results back to the UI thread.
- Each row uses icons for state (matching the main results table icons):
  - **Valid (OK):** check icon
  - **Invalid:** cross icon
  - **Validating:** clock icon
- While validating, Save is disabled for dirty rows.
- Validation requests are versioned with a per-row token so stale async results are ignored if the row is edited again.

---

## Validation logic per table

### A) Overrides row validation (full pipeline)

Given:

- `extractedShow` (left column)
- `replacementText` (right column)

Simulate:

1. Start with the extracted show.
2. Apply override (substitute with `replacementText`).
3. Compute query string the same way the app does for provider lookup.
4. Run provider search for that query string.
5. Determine whether show resolution would require Select Shows:
   - If **0 candidates** → ❌ `No matches`
   - If **1 candidate** → ✅ `Resolves uniquely`
   - If **>1 candidates**, check whether a disambiguation exists *for this query string* and would pick a candidate:
     - If yes and the id exists in candidates → ✅ `Resolved via pinned ID`
     - If not → ❌ `Still ambiguous (would prompt)`

Note: This “full pipeline” behavior is the key requirement: an override can validate because a disambiguation exists.

### B) Disambiguations row validation

Given:

- `queryString`
- `seriesId`

Simulate:

1. Run provider search for `queryString`.
2. If **0 candidates** → ❌ `No matches`
3. If `seriesId` is present among candidates → ✅ `Pinned match is valid`
4. Else → ❌ `ID not found in results (stale or wrong)`

We intentionally do not require that the query string is ambiguous; a pinned id can still be valid even if only 1 candidate exists.

---

## Handling blanks / partial edits

- If required fields are blank when committing via Add / Update, the row is marked:
  - **Incomplete** (and treated as invalid for Save gating)
- Because the commit model is explicit (Add / Update), transient in-field edits do not immediately invalidate until committed.

---

## Persistence / Data model

### Storage keys (unchanged)

- Overrides remain `String -> String` mapping.
- Disambiguations remain `query string -> series id` mapping.

### Editing behavior

- The UI edits an in-memory copy.
- On Save:
  - persist overrides map
  - persist disambiguations map
- Any deletions remove entries from the maps.

### Normalization

- Disambiguation keys are query strings; user may edit them directly.
- Do not auto-normalize the user’s typed query string beyond trimming; validation will fail if they make it unusable.
- Override keys/values should be trimmed.

---

## Interaction with Select Shows dialog

- Select Shows dialog continues to create/modify disambiguations (`query string -> id`) through normal use.
- Matching tab provides a manual editor over the same data.
- If a user “breaks” a query string in Matching tab, they may re-enter Select Shows flow later; this is acceptable.

### Validation message visibility (tooltip-like behavior)
- SWT TableItem does not support true per-cell tooltips, so the UI shows a small message label near each table that updates as the user hovers rows.

---

## Success criteria

- Users can resolve ambiguous shows without repeated modal churn by:
  - using Overrides to steer names
  - using Disambiguations to pin ambiguous query strings
- Matching tab shows clear validation ticks/crosses for new/changed entries.
- Save is blocked only by invalid dirty rows.
- No UI freezes; validation work is threaded and responsive.

---

## Future enhancement (explicit TODO)

### Pin by extracted show name (not implemented here)

Add a third rule type:

- `extracted show (or post-override show text) -> series id`

This would let users pin a show without crafting/maintaining a query string. This is intentionally deferred.
