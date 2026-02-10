## New features / improvements

### Duplicate cleanup dialog overhaul
The duplicate file cleanup dialog has been significantly improved for both safety and usability:

- **Safe defaults:** Files are now unchecked by default, Cancel is the default button (Enter
  dismisses safely), and the Delete button is disabled until at least one file is selected.
- **Smarter matching:** Fuzzy duplicate detection now compares show names — not just season/episode
  numbers — so unrelated files from different shows in the same folder are no longer flagged as
  duplicates during rename-only operations.
- **More context for decisions:** New Size and Modified columns help identify which copy to keep.
  Right-click any file to "Open Folder" and inspect it directly.

### MP4 metadata tagging rewrite
The built-in mp4parser library (unmaintained since ~2017) has been replaced with external tools
that handle MP4 containers correctly:

- **AtomicParsley** (preferred) — surgical iTunes atom edits without rewriting the file.
- **ffmpeg** (fallback) — ubiquitous, rewrites container with `-c copy` but does so correctly.

TVRenamer detects whichever tool is installed. If neither is available, tagging is silently
skipped and the file is left untouched. MKV tagging continues to use mkvpropedit as before.

### 24-item codebase quality sweep
A comprehensive codebase review identified and resolved 24 improvement opportunities across
bug fixes, consolidation, modernisation, safety, and maintainability:

- **Bug fix:** Fixed `StringUtils.removeLast()` case-sensitivity mismatch that could corrupt
  output when removing format tags from filenames.
- **Thread safety:** Synchronized all shared-state accessors in `FileEpisode` (prevents data
  races when listings arrive on background threads while the UI reads episode data). Changed
  `UserPreferences` override maps to `ConcurrentHashMap`.
- **New shared utilities:** `ProcessRunner` (process execution with timeout and cleanup) and
  `ExternalToolDetector` (PATH + platform-specific tool detection) — replaced ~130 lines of
  duplicated code across taggers and theme manager.
- **API improvements:** Added `TaggingResult` enum (replaces boolean) for tagging outcomes.
  Added `isToolAvailable()` / `getToolName()` to the `VideoMetadataTagger` interface.
- **Java records:** Converted `EpisodePlacement` and `ScoredOption` to Java records.
- **Decomposed 310-line method:** `ShowSelectionEvaluator.evaluate()` broken into 8 named,
  independently testable methods.
- **Modernised patterns:** `ThreadLocal.withInitial()`, `Objects.equals()`, `String.isBlank()`,
  `StandardCharsets`. Consolidated filename extension extraction (5 sites to 1). Standardised
  logging to use lambda suppliers in hot paths.
- **New test coverage:** `ProcessRunner`, `ExternalToolDetector`, XML escaping, basename
  extraction.
- **Null-vs-empty convention:** Collection-returning methods now return empty lists instead of
  null, eliminating defensive null checks throughout.
- **Resource cleanup:** Font disposal in AboutDialog, XPath expression caching, dialog boilerplate
  consolidation, file I/O moved off UI thread in duplicate dialog.

Full details: `docs/code improvement opportunities.md`

## Dependency updates

- **Removed** unused `commons-codec` dependency (no imports in codebase)
- **Updated** JUnit Jupiter 5.11.4 to 5.14.2
- **Updated** GitHub Actions: checkout v4 to v6, setup-java v4 to v5, setup-gradle v4 to v5,
  upload-artifact v4 to v6
- All other dependencies already at latest: SWT 3.132.0, XStream 1.4.21, OkHttp 5.3.2,
  Gradle 9.3.1, Shadow 9.3.1, Launch4j 4.0.0, SpotBugs 6.4.8

---

**Requirements:** Java 17+ runtime

**Artifacts:**
- `TVRenamer.exe` — Windows executable (recommended)
- `tvrenamer-242.jar` — Cross-platform fat JAR
- `tvrenamer.jar` — Stable-named fat JAR (same content as versioned)
