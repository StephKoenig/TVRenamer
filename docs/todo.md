# TVRenamer — Consolidated TODO Notes

This document consolidates “future work” notes that are currently embedded throughout the codebase (e.g., `TODO:` comments and other forward-looking commentary such as “in the future…”, “maybe we should…”, “might be better…”, etc.). The intent is to make these ideas easier to discover, discuss, and prioritize—without removing or rewriting the original in-code notes.

Notes are grouped by area and summarized at a high level. Where helpful, the source location is listed so you can jump directly to the original comment.

---

## Top candidates (high impact / low risk)

These are suggested “first picks” from the backlog below—items that are likely to improve user experience, correctness, or maintainability with relatively contained changes.

1. **Make file modification-time behavior configurable**
   - **Why:** Users often care about preserving original timestamps; current behavior forces mtime to “now”.
   - **Where:** `org.tvrenamer.controller.FileMover` — `finishMove(...)`
   - **Effort:** Small-to-medium (preference + UI + apply in move/copy paths)

2. **Fix Preferences dialog token drop insertion position**
   - **Why:** Current drag/drop appends tokens rather than inserting where dropped; easy UX win.
   - **Where:** `org.tvrenamer.view.PreferencesDialog` — `PreferencesDropTargetListener.drop(...)`
   - **Effort:** Small (caret/selection-aware insertion)

3. **Thread the preload folder scan**
   - **Why:** Folder scanning can block responsiveness; background execution improves perceived performance.
   - **Where:** `org.tvrenamer.model.EpisodeDb` — `preload()`
   - **Effort:** Medium (executor + cancellation + UI update batching)

4. **Harden XPath usage for potential concurrency**
   - **Why:** Shared static `XPath` instance may not be thread-safe; safer patterns are straightforward.
   - **Where:** `org.tvrenamer.controller.util.XPathUtilities`
   - **Effort:** Small (per-call creation or `ThreadLocal`)

5. **Improve show selection heuristics when ambiguous**
   - **Why:** Avoid “choose first match” surprises; reduce incorrect auto-matches.
   - **Where:** `org.tvrenamer.model.ShowName` / `ShowStore`
   - **Effort:** Medium (tie-breakers and/or user prompt; can start with better tie-breakers only)

6. **Stabilize Windows permission-related tests**
   - **Why:** Flaky/ineffective Windows “read-only” simulation leads to unreliable tests.
   - **Where:** `org.tvrenamer.controller.TestUtils` — `setReadOnly(Path)`
   - **Effort:** Medium (capability checks/skip strategy, or a dedicated ACL helper)

---

## 1) File moving / filesystem behavior

### Make modification-time behavior configurable
**Context:** After a successful move/rename, the app sets the file’s modification time to “now”.  
**Why it matters:** Some users want to preserve original mtimes; others might want “now”. This is a policy decision best surfaced as a preference.

- Source:
  - `org.tvrenamer.controller.FileMover` — `finishMove(...)`
  - Note: “why do we set the file modification time to ‘now’? Would like to at least make this behavior configurable.”

**Potential follow-ups:**
- Add a preference like “Preserve original modification time” / “Set to now”.
- If preserving, capture original mtime pre-move and apply after move/copy.
- Ensure behavior is consistent for both rename and copy+delete flows.

---

## 2) Move conflict detection & generalization

### Generalize helper used for building map lists
**Context:** `MoveRunner` has a helper for “get list value from map, creating list if absent”.  
**Why it matters:** It’s a generally useful pattern and likely appears in other places or could.

- Source:
  - `org.tvrenamer.controller.MoveRunner` — helper: “TODO: make this a generic facility?”

**Potential follow-ups:**
- Replace with `Map.computeIfAbsent(...)` (if not already feasible given target Java version / style).
- Introduce a small util method (if used across multiple classes), but prefer standard library constructs.

### Expand conflict detection beyond exact filename matches
**Context:** Conflict detection currently assumes “exact filename match” is the primary conflict type, but the code anticipates more nuanced matching.  
**Why it matters:** Users often have conflicts that aren’t identical filenames (different codecs, resolutions, containers, etc.). Better detection could reduce accidental overwrites or confusing “duplicates”.

- Source:
  - `org.tvrenamer.controller.MoveRunner` — comments around `existingConflicts(...)` and conflict resolution notes
  - Notes include:
    - future: find other potentially conflicting files
    - questions like:
      - can we rename files already in destination?
      - is it still a conflict if resolution differs?
      - if file formats differ?
      - what about identical files?

**Potential follow-ups:**
- Define a “conflict policy” preference:
  - overwrite / skip / version suffix / move to duplicates folder
- Detect conflicts by episode identity rather than filename alone (requires metadata)
- Optionally hash/compare files to detect identical content

---

## 3) Copy behavior and file attributes

### Preserve or intentionally manage file attributes on copy
**Context:** Copying a file may not preserve original attributes; sometimes that’s desirable, sometimes not possible.  
**Why it matters:** On some filesystems/shares, preserving ownership/timestamps/ACLs may be important; on others, it may be impossible or undesirable.

- Source:
  - `org.tvrenamer.controller.util.FileUtilities` — `copyWithUpdates(...)`
  - Note: newly created file will not necessarily have same attributes; ownership may be desirable; may be impossible to change.

**Potential follow-ups:**
- Decide which attributes matter:
  - timestamps (ctime/mtime/atime)
  - permissions/ACLs
  - ownership
- If using `Files.copy(...)`, consider `COPY_ATTRIBUTES` where feasible, but be careful across filesystems.
- Expose as preferences, but keep sensible defaults and clear UI wording.

---

## 4) XML / string encoding & parsing

### Expand special-character encoding rules
**Context:** `encodeSpecialCharacters(...)` currently handles a limited set and calls out missing cases.  
**Why it matters:** Metadata from online sources (or file names) can include characters that break XML or cause display issues.

- Source:
  - `org.tvrenamer.controller.util.StringUtils` — `encodeSpecialCharacters(...)`
  - Note: determine other characters that need to be replaced (e.g., `'`, `-`)

**Potential follow-ups:**
- Clearly define where encoding is required (XML vs display vs filesystem).
- Replace ad-hoc encoding with a well-tested XML escaping strategy where applicable.
- Add tests for tricky titles (ampersands, apostrophes, unicode punctuation, etc.).

### Confirm thread-safety of shared XPath instance
**Context:** `XPathUtilities` uses one static `XPath` instance for all requests.  
**Why it matters:** `XPath` implementations may not be thread-safe; sharing could cause concurrency bugs if used across threads.

- Source:
  - `org.tvrenamer.controller.util.XPathUtilities`
  - Note: “We just create this one XPath object and use it for all shows, all episodes. TODO: is there any issue with this?”

**Potential follow-ups:**
- Verify actual usage patterns (single-threaded vs multi-threaded calls).
- If multi-threaded, consider:
  - `ThreadLocal<XPath>`
  - create a new `XPath` per use (likely cheap enough)
  - synchronize access (last resort)

---

## 5) Episode DB / table population & responsiveness

### Improve handling of “unparsed” files
**Context:** Files that fail parsing are still inserted but “not much use”.  
**Why it matters:** Users want actionable feedback: why it failed, how to fix it, and easy filtering/removal.

- Source:
  - `org.tvrenamer.model.EpisodeDb` — add logic
  - Note: “We’re putting the episode in the table anyway, but it’s not much use. TODO: make better use of it.”

**Potential follow-ups:**
- Show parse-failure reason in status/tooltip column
- Provide “copy diagnostic” or “open containing folder” actions
- Add filter “hide parse failures” or a dedicated section/list

### Consider canonicalization when file paths refer to same file
**Context:** EpisodeDb can detect two strings refer to the same file; it currently chooses not to update the stored key/path even if it knows they match.  
**Why it matters:** Normalizing paths can reduce confusion and improve deduplication, but may have pitfalls on Windows/network shares.

- Source:
  - `org.tvrenamer.model.EpisodeDb` — `currentLocationOf(...)`
  - Note: “Though, maybe we should? TODO”

**Potential follow-ups:**
- Decide a consistent canonical form for paths (absolute+normalized vs real path)
- Be careful with UNC/SMB edge cases where “real path” may fail or be slow
- Add tests for path normalization behavior on Windows

### Preload folder in a separate thread
**Context:** Preload is currently not threaded.  
**Why it matters:** Directory scanning can block UI responsiveness if performed on the UI thread.

- Source:
  - `org.tvrenamer.model.EpisodeDb` — `preload()`
  - Note: “TODO: do in separate thread”

**Potential follow-ups:**
- Move preload scanning to a background executor
- Stream results into the UI (batch updates) rather than one huge update
- Ensure cancellation is possible when closing the app / changing preferences

---

## 6) Show matching / selection heuristics

### Improve selection when multiple show matches exist
**Context:** When there are multiple exact hits, the code “chooses first one” and notes potential better criteria. When no exact match, it suggests better selection methods like Levenshtein distance.

- Source:
  - `org.tvrenamer.model.ShowName` — `selectShowOption()`
  - Notes:
    - “could check language? other criteria? case sensitive?”
    - “still might be better ways to choose if we don’t have an exact match. Levenshtein distance?”

**Potential follow-ups:**
- Include language and/or year as tie-breakers
- Provide a “choose show” prompt when ambiguity exists (possibly cached)
- Implement fuzzy matching (Levenshtein/Jaro-Winkler), but avoid surprising auto-selections
- Add tests for common ambiguous show names

---

## 7) Preferences model improvements

### Enforce / normalize destination directory path expectations
**Context:** Destination directory is described as “must be absolute path”, but enforcement is unclear.

- Source:
  - `org.tvrenamer.model.UserPreferences` — `setDestinationDirectory(...)`
  - Notes:
    - enforce absolute?
    - convert to absolute then compare?
    - what happens if destination validation fails?

**Potential follow-ups:**
- Normalize destination paths when saving:
  - store canonical absolute/normalized path string
- Improve validation feedback in Preferences (already improved, but could expand)
- Consider expanding path validation to support environment variables or `~` expansion (if desired)

### Improve ignore-keywords parsing
**Context:** Ignore keywords are stored/parsed with a note about converting commas into pipes for regex and removing periods.

- Source:
  - `org.tvrenamer.model.UserPreferences` — ignore keywords parsing
  - Note: “Convert commas into pipes for proper regex, remove periods”

**Potential follow-ups:**
- Decide whether ignore keywords are:
  - literal tokens, or
  - regex fragments
- If regex-based, ensure proper escaping and UI guidance
- Add UI help text + tests for typical inputs (comma separated, spaces, punctuation)

---

## 8) Preferences dialog drag/drop UX

### Drop target appends instead of inserting at drop location
**Context:** Drag-and-drop of rename tokens into the replacement string currently appends, ignoring the actual drop position.

- Source:
  - `org.tvrenamer.view.PreferencesDialog` — `PreferencesDropTargetListener.drop(...)`
  - Note: “This currently adds the dropped text onto the end, not where we dropped it”

**Potential follow-ups:**
- Use caret position / mouse drop location to insert text
- If dropping into `Text`, use selection/caret APIs to insert at caret
- Provide visual feedback for insertion position

---

## 9) “Soft TODOs” (non-`TODO:` forward-looking commentary)

This section captures additional improvement ideas expressed in comments that are not explicitly tagged with `TODO:`.

### Ambiguity handling in show mapping / query results
**Context:** The show-name mapping pipeline normalizes “query strings” and attempts to map them to series. There are explicit notes that ambiguity exists (and may remain) and that better UI/flows could help users resolve it.

- Source:
  - `org.tvrenamer.model.ShowStore` — class/Javadoc commentary around “query strings”, “actual show name”, and ambiguity notes.
  - Notes include:
    - The “actual show name” is expected to be unique, but true show names may not be.
    - TVDB sometimes appends years to disambiguate (e.g., `Archer (2009)`).
    - In the future it might be better to model mapping as many-to-many and disambiguate later using more information.
    - UI could notify the user of ambiguity, make a best guess, and allow user correction.

**Potential follow-ups:**
- Add explicit “ambiguous match” UI with a selection dialog (and caching).
- Track uncertainty in the model (e.g., multiple candidate series until episode lookup resolves).
- Add better tie-breakers (year, network, language) and expose them in UI.
- Persist user overrides for ambiguous cases (beyond show-name overrides).

### More robust filename → episode inference
**Context:** Current inference is primarily based on placement (season/episode numbering in the chosen ordering). Comments note that richer metadata could improve inference.

- Source:
  - `org.tvrenamer.model.Show` — notes that future enhancements could use other meta-information to determine which episode a filename refers to, beyond placement.

**Potential follow-ups:**
- Use episode titles parsed from filename as a secondary disambiguator.
- When both season/episode and date patterns exist, cross-check them.
- If provider offers absolute ordering or aired date ordering, optionally use it to break ties.

### Parsing fallbacks and “should never happen” paths
**Context:** Parser code contains “this should never happen” style comments indicating areas where behavior could be tightened or more explicitly treated as errors.

- Source:
  - `org.tvrenamer.controller.FilenameParser` — comment noting a mismatch of expected matcher group counts “should never happen”, but currently ignored.

**Potential follow-ups:**
- Add structured logging / telemetry for these “should never happen” cases.
- Add unit tests for unexpected matcher behavior.
- Consider turning the branch into a parse-failure with user-visible diagnostic.

### Clarify future listener semantics for show information
**Context:** There’s commentary that if callbacks need to send additional information later, the listener interface and code paths should change.

- Source:
  - `org.tvrenamer.model.ShowStore` — comment around `mapStringToShow(...)` noting that in the future, if the listener expands to deliver more information later, current immediate-callback clauses would need to be updated.

**Potential follow-ups:**
- Define whether show mapping is strictly one-shot or can be incremental/async.
- If async, design a listener contract that supports partial updates and finalization.

---

## 10) Test-suite notes (future improvements & reliability)

This section captures forward-looking notes found in `src/test/java`. These are often about making tests more reliable across platforms, clarifying historical behaviors, or improving test ergonomics.

### Improve Windows read-only / ACL simulation in tests
**Context:** Some tests need to simulate “not writable” paths/directories. On Windows, POSIX permissions aren’t applicable and `java.io.File#setReadOnly()` isn’t sufficient to enforce non-modifiability for the kinds of behaviors the tests want to assert.

- Source:
  - `org.tvrenamer.controller.TestUtils` — `setReadOnly(Path)`
  - Note: find a library (for testing) that can set Windows ACLs to actually make a directory non-modifiable.

**Potential follow-ups:**
- Introduce a test-only helper/library to manipulate NTFS ACLs (and guard it so it only runs on Windows).
- Alternatively, redesign tests to avoid requiring OS-level ACL mutations (e.g., use a temp directory on a read-only mounted location—often not feasible on Windows CI).
- Add explicit “capability checks” and skip tests when the platform cannot enforce the intended permission behavior reliably.

### Keep DVD-order / conflict-resolution tests resilient to upstream data changes
**Context:** Some TVDB-based tests include commentary acknowledging that upstream show metadata can change over time (e.g., new DVD releases can appear, changing what’s considered a “missing” DVD episode placement).

- Source:
  - `org.tvrenamer.controller.TheTVDBProviderTest` — DVD episode preference tests
  - Commentary notes that future upstream content could change assumptions.

**Potential follow-ups:**
- Prefer fixture-based tests (recorded API responses) for stability, especially for nuanced ordering edge cases.
- If live API tests remain, reduce brittleness by testing invariants rather than exact episode IDs/titles where feasible.
- Document the expectation that some integration tests may need periodic updates when upstream providers change data.

### Document/standardize temp-dir cleanup expectations
**Context:** The test suite places emphasis on cleaning up temp directories and leaving the environment as it was found, with notes about doing a “best-effort rm -rf” style cleanup when teardown fails.

- Source:
  - `org.tvrenamer.model.FileEpisodeTest` — teardown/cleanup commentary.

**Potential follow-ups:**
- Centralize temp-dir creation and teardown helpers so cleanup is consistent across tests.
- Make cleanup more failure-tolerant while still surfacing root-cause failures (e.g., log what couldn’t be deleted and why).
- Ensure cleanup behavior is robust on Windows where file-locking is common.

---


## Backlog suggestions / how to use this file

- Treat sections above as a backlog seed, not a mandate.
- Before implementing a TODO, confirm current behavior, add/expand tests where feasible, and validate on Windows (primary target).
- When completing a TODO, keep original in-code note if it still provides historical context; otherwise consider updating it to reference the implemented solution.

---