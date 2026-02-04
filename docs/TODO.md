# TVRenamer — Consolidated TODO Notes

Note: Completed work is tracked in `docs/Completed.md`. Keep this file focused on future work only.

This document consolidates "future work" notes from the codebase. Notes are grouped by impact area and prioritized by user value.

---

## User-Facing Features

### Headless CLI mode (automation/pipelines)
**Why:** Enables scripted usage without SWT/GUI — useful for NAS automation, batch processing, CI pipelines.
**Where:** New entry point (e.g., `org.tvrenamer.controller.CliMain`) + separation of UI vs core logic.
**Effort:** Medium/Large

### Show matching heuristics: improve selection when multiple matches exist
**Context:** When there are multiple exact hits, the code "chooses first one" and notes potential better criteria. When no exact match, it suggests better selection methods like Levenshtein distance.

- Source:
  - `org.tvrenamer.model.ShowName` — `selectShowOption()`
  - `org.tvrenamer.model.ShowSelectionEvaluator`
  - Notes:
    - "could check language? other criteria? case sensitive?"
    - "still might be better ways to choose if we don't have an exact match. Levenshtein distance?"

**Potential follow-ups:**
- Include language and/or year as tie-breakers
- Provide a "choose show" prompt when ambiguity exists (possibly cached)
- Implement fuzzy matching (Levenshtein/Jaro-Winkler), but avoid surprising auto-selections
- Add tests for common ambiguous show names

### Allow pinning a show ID by extracted show name
**Context:** Today, disambiguation selections are stored as `query string -> series id`, and name overrides are stored as `extracted show -> override text`. A future enhancement would allow a direct "pin by name" rule that bypasses ambiguity even without crafting/maintaining a query string.
**Why it matters:** Provides a simpler, more robust advanced option for users who know the correct show and want to avoid repeated prompts even if normalization rules change.

- Potential shape:
  - `extracted show (or post-override show text) -> series id`
- Likely UI location:
  - unified "Show Matching Rules" editor alongside Overrides and Disambiguations

### More robust filename → episode inference
**Context:** Current inference is primarily based on placement (season/episode numbering in the chosen ordering). Comments note that richer metadata could improve inference.

- Source:
  - `org.tvrenamer.model.Show` — notes that future enhancements could use other meta-information to determine which episode a filename refers to, beyond placement.

**Potential follow-ups:**
- Use episode titles parsed from filename as a secondary disambiguator.
- When both season/episode and date patterns exist, cross-check them.
- If provider offers absolute ordering or aired date ordering, optionally use it to break ties.

---

## Code Reliability & Maintenance

### SWT upgrade guardrail: document and investigate SWT 3.130+ native-load incompatibility
**Why:** Enables future SWT upgrades without breaking Windows startup.
**Where:** Dependency management + docs
**Effort:** Small/Medium

**Investigation Results:**
Tested multiple SWT versions with Gradle 9.3.1 and Shadow 9.3.1 on Java 21:

| Version | Result |
|---------|--------|
| 3.129.0 | ✅ Works |
| 3.130.0 | ❌ Fails |
| 3.131.0 | ❌ Fails |
| 3.132.0 | ❌ Fails |

All failing versions show:
```
Libraries for platform win32 cannot be loaded because of incompatible environment
```

**Findings:**
- Issue started at SWT 3.130.0 (not related to Gradle/Shadow versions)
- Appears to be a change in how SWT handles native library loading when packaged in a fat/shadow JAR
- Not related to Java version (tested on Java 21)
- No release notes available on GitHub for SWT 3.130+

**Staying on SWT 3.129.0** until Eclipse fixes this upstream or a workaround is found.

### Episode DB path canonicalization
**Context:** EpisodeDb can detect two strings refer to the same file; it currently chooses not to update the stored key/path even if it knows they match.
**Why it matters:** Normalizing paths can reduce confusion and improve deduplication, but may have pitfalls on Windows/network shares.

- Source:
  - `org.tvrenamer.model.EpisodeDb` — `currentLocationOf(...)`
  - Note: "Though, maybe we should? TODO"

**Potential follow-ups:**
- Decide a consistent canonical form for paths (absolute+normalized vs real path)
- Be careful with UNC/SMB edge cases where "real path" may fail or be slow
- Add tests for path normalization behavior on Windows

### Enforce / normalize destination directory path expectations
**Context:** Destination directory is described as "must be absolute path", but enforcement is unclear.

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

### Parsing fallbacks and "should never happen" paths
**Context:** Parser code contains "this should never happen" style comments indicating areas where behavior could be tightened or more explicitly treated as errors.

- Source:
  - `org.tvrenamer.controller.FilenameParser` — comment noting a mismatch of expected matcher group counts "should never happen", but currently ignored.

**Potential follow-ups:**
- Add structured logging / telemetry for these "should never happen" cases.
- Add unit tests for unexpected matcher behavior.
- Consider turning the branch into a parse-failure with user-visible diagnostic.

### Clarify future listener semantics for show information
**Context:** There's commentary that if callbacks need to send additional information later, the listener interface and code paths should change.

- Source:
  - `org.tvrenamer.model.ShowStore` — comment around `mapStringToShow(...)` noting that in the future, if the listener expands to deliver more information later, current immediate-callback clauses would need to be updated.

**Potential follow-ups:**
- Define whether show mapping is strictly one-shot or can be incremental/async.
- If async, design a listener contract that supports partial updates and finalization.

---

## Test Suite Improvements

### Document/standardize temp-dir cleanup expectations
**Context:** The test suite places emphasis on cleaning up temp directories and leaving the environment as it was found, with notes about doing a "best-effort rm -rf" style cleanup when teardown fails.

- Source:
  - `org.tvrenamer.model.FileEpisodeTest` — teardown/cleanup commentary.

**Potential follow-ups:**
- Centralize temp-dir creation and teardown helpers so cleanup is consistent across tests.
- Make cleanup more failure-tolerant while still surfacing root-cause failures (e.g., log what couldn't be deleted and why).
- Ensure cleanup behavior is robust on Windows where file-locking is common.

---

## Dependency Status (January 2026)

| Dependency | Version | Latest Available | Status |
|------------|---------|------------------|--------|
| SWT | 3.129.0 | 3.131.0 | ❌ Blocked (see above) |
| XStream | 1.4.21 | 1.4.21 | ✅ Latest |
| Commons Codec | 1.21.0 | 1.21.0 | ✅ Updated |
| OkHttp | 5.3.2 | 5.3.2 | ✅ Updated |
| mp4parser | 1.9.56 | 1.9.56 | ✅ New (metadata tagging) |
| JUnit | 5.11.4 | 5.11.4 | ✅ Updated (JUnit 5/Jupiter) |
| Gradle | 9.3.1 | 9.3.1 | ✅ Updated |
| Shadow Plugin | 9.3.1 | 9.3.1 | ✅ Updated |
| Launch4j Plugin | 4.0.0 | 4.0.0 | ✅ Latest |
| SpotBugs Plugin | 6.4.8 | 6.4.8 | ✅ Updated |

---

## Backlog suggestions / how to use this file

- Treat sections above as a backlog seed, not a mandate.
- Before implementing a TODO, confirm current behavior, add/expand tests where feasible, and validate on Windows (primary target).
- When completing a TODO, move it to `docs/Completed.md` with context about the implementation.
