# TVRenamer — Consolidated TODO Notes


Note: Completed work is tracked in `docs/Completed.md`. Keep this file focused on future work only.


This document consolidates “future work” notes that are currently embedded throughout the codebase (e.g., `TODO:` comments and other forward-looking commentary such as “in the future…”, “maybe we should…”, “might be better…”, etc.). The intent is to make these ideas easier to discover, discuss, and prioritize—without removing or rewriting the original in-code notes.

Notes are grouped by area and summarized at a high level. Where helpful, the source location is listed so you can jump directly to the original comment.

---

## Prioritized backlog (user impact / robustness / effort)

This section is the “living” priority order for what’s left, based on:
- **User impact** (how often it helps / how confusing it is today)
- **Security/robustness** (reduces risk of data loss or hard-to-debug failures)
- **Effort** (how contained the change is)

### P2 — Medium impact / longer horizon
1. **Headless CLI mode (automation/pipelines)**
   - **Why:** Enables scripted usage without SWT/GUI.
   - **Where:** new entry point (e.g., `org.tvrenamer.controller.CliMain`) + separation of UI vs core logic.
   - **Effort:** Medium/Large

2. **Show selection heuristics: verify coverage and expand carefully**
   - **Why:** Reduce unnecessary prompts while staying deterministic/spec-driven.
   - **Where:** `org.tvrenamer.model.ShowSelectionEvaluator`
   - **Effort:** Medium (incremental)

3. **SWT upgrade guardrail: document and investigate SWT 3.130+ native-load incompatibility**
   - **Why:** Enables future SWT upgrades without breaking Windows startup.
   - **Where:** dependency management + docs
   - **Effort:** Small/Medium

---

## Dependency Updates (January 2026)

Evaluated available updates for all dependencies and plugins.

### Current Versions (after updates)
| Dependency | Version | Latest Available | Status |
|------------|---------|------------------|--------|
| SWT | 3.129.0 | 3.131.0 | ❌ Blocked (see below) |
| XStream | 1.4.21 | 1.4.21 | ✅ Latest |
| Commons Codec | 1.21.0 | 1.21.0 | ✅ Updated |
| OkHttp | 5.3.2 | 5.3.2 | ✅ Updated |
| mp4parser | 1.9.56 | 1.9.56 | ✅ New (metadata tagging) |
| JUnit | 5.11.4 | 5.11.4 | ✅ Updated (JUnit 5/Jupiter) |
| Gradle | 9.3.1 | 9.3.1 | ✅ Updated |
| Shadow Plugin | 9.3.1 | 9.3.1 | ✅ Updated |
| Launch4j Plugin | 4.0.0 | 4.0.0 | ✅ Latest |
| SpotBugs Plugin | 6.4.8 | 6.4.8 | ✅ Updated |

### SWT 3.130+ Investigation Results
Tested multiple SWT versions with Gradle 9.3.1 and Shadow 9.3.1 on Java 21:

| Version | Result |
|---------|--------|
| 3.129.0 | ✅ Works |
| 3.130.0 | ❌ Fails |
| 3.131.0 | ❌ Fails |
| 3.132.0 | ❌ Fails |

All failing versions show the same error:
```
Libraries for platform win32 cannot be loaded because of incompatible environment
```

**Findings:**
- Issue started at SWT 3.130.0 (not related to Gradle/Shadow versions)
- Appears to be a change in how SWT handles native library loading when packaged in a fat/shadow JAR
- Not related to Java version (tested on Java 21)
- No release notes available on GitHub for SWT 3.130+

**Staying on SWT 3.129.0** until Eclipse fixes this upstream or a workaround is found.

---

## 1) File moving / filesystem behavior

(Completed items are tracked in `docs/Completed.md`. This section is future-work only.)

---

## 2) Move conflict detection & generalization

**Potential follow-ups (for future consideration):**
- Optionally hash/compare files to detect identical content
- Expose more granular conflict policy choices in preferences

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

## 4) Episode DB / table population & responsiveness

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

(Completed items are tracked in `docs/Completed.md`.)

---

## 5) Show matching / selection heuristics

### Future: allow pinning a show ID by extracted show name (not just query string)
**Context:** Today, disambiguation selections are stored as `query string -> series id`, and name overrides are stored as `extracted show -> override text`. A future enhancement would allow a direct “pin by name” rule that bypasses ambiguity even without crafting/maintaining a query string.
**Why it matters:** Provides a simpler, more robust advanced option for users who know the correct show and want to avoid repeated prompts even if normalization rules change.

- Potential shape:
  - `extracted show (or post-override show text) -> series id`
- Likely UI location:
  - unified “Show Matching Rules” editor alongside Overrides and Disambiguations

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

## 6) Preferences model improvements

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

## 7) Preferences dialog drag/drop UX

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

## 8) "Soft TODOs" (non-`TODO:` forward-looking commentary)

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

### Parsing fallbacks and "should never happen" paths
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

## 9) Test-suite notes (future improvements & reliability)

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
