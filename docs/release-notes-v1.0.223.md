## New features / improvements

### Fuzzy matching for show selection
- **Smarter auto-selection:** When searching for shows, TVRenamer now uses Levenshtein distance to find close matches. If a candidate scores 80%+ similarity with a 10%+ gap over the next best option, it's automatically selected — reducing prompts for typos or minor variations in show names.
- **Improved disambiguation dialog:** When manual selection is needed, options are now sorted by similarity score (best first). The top option is marked "★ Recommended" with its match percentage if it scores 70%+, making it easy to spot the most likely match.
- **Alias support:** Fuzzy matching also considers show aliases from the provider, using the best score across all names.

### Performance optimization
- **Reduced I/O for batch moves:** When moving multiple files to the same destination directory, TVRenamer now verifies each directory only once instead of creating a temporary probe file for every file. This significantly reduces disk activity for large batches.

## Bug fixes

### Duplicate cleanup dialog fix
- Fixed an issue where the duplicate cleanup dialog would incorrectly list files that were just moved as deletion candidates. Now only pre-existing duplicates are shown.

### Action button counter fix
- Fixed a bug where clicking the action button after files were already processed would increment the "Processed" counter without actually processing anything. Already-completed rows are now correctly skipped.

---

**Requirements:** Java 17+ runtime

**Artifacts:**
- `TVRenamer.exe` — Windows executable (recommended)
- `tvrenamer-223.jar` — Cross-platform fat JAR
- `tvrenamer.jar` — Stable-named fat JAR (same content as versioned)
