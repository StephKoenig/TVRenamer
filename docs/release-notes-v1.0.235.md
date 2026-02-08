## New features / improvements

### Smart episode title selection
When aired and DVD orderings give different episode titles for the same episode number,
TVRenamer now handles them intelligently:

- **Fuzzy pre-selection from filename:** If the filename contains the episode title
  (e.g., `CHiPs.S03E18.Off.Road.1080p.WEBRip.mkv`), TVRenamer extracts the title text
  and fuzzy-matches it against the two options to automatically pre-select the correct one.
- **Chain propagation:** Selecting (or auto-selecting) a title for one episode automatically
  cascades through adjacent episodes that share overlapping title options. For example,
  picking one title for episode 18 determines episodes 19 and 20 in sequence. Selections
  are non-destructive — the dropdown keeps both options and overriding any choice
  re-triggers propagation from that point.

### Clickable "set Hint" link for unfound shows
When a show can't be found on TheTVDB, the Proposed File Path column now shows a
clickable *"click to set Hint"* link that jumps directly to the Preferences Matching tab
with the show name pre-filled — just type the correct name and save. Unfound shows whose
override changed are automatically retried.

### BBC-style filename pattern support
Added recognition of `Show_Series_X_-_YY.Title` filename patterns commonly used by
BBC content. Also tightened the resolution regex to avoid false positives on programme IDs.

### SWT upgraded to 3.132.0
Updated the SWT UI toolkit from 3.129.0 to 3.132.0 (latest), bringing platform and
accessibility improvements. Resolved a compatibility issue where SWT 3.130+ required
specific manifest attributes that were lost in fat JAR packaging.

## Bug fixes

### Dedupe safety gate for non-video files
Moving subtitle or metadata files (`.srt`, `.nfo`, etc.) no longer triggers the
duplicate video scan. Previously, the scan could surface the actual video files as
"duplicates", risking accidental deletion.

### NAS recycle bin cleanup
Removed temporary writability probe files that accumulated in NAS recycle bins. Destination
writability is now validated at move time with a clear OS-level error if the folder isn't
writable.

### Combo widget visual artifacts on Clear Completed
Fixed visual ghosting where Combo dropdown widgets on remaining rows would briefly stay at
their old positions when completed rows were cleared. Row deletions are now batched into a
single repaint.

## Code quality

- Auto-retry unfound shows after override changes in Preferences (no manual re-add needed)
- Updated help pages with documentation for all new features
- Removed low-value TODO items and cleaned up internal comments

---

**Requirements:** Java 17+ runtime

**Artifacts:**
- `TVRenamer.exe` — Windows executable (recommended)
- `tvrenamer-235.jar` — Cross-platform fat JAR
- `tvrenamer.jar` — Stable-named fat JAR (same content as versioned)
