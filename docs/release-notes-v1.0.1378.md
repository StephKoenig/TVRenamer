## New features / improvements

### "Always overwrite destination" preference
New preference option to automatically overwrite existing destination files instead of creating versioned suffixes like `(1)`, `(2)`. Useful when re-processing files to a curated library.

### Duplicate video cleanup with user confirmation
New preference option to detect and offer deletion of duplicate video files after successful moves. When enabled:
- Detects duplicates by base filename (same name, different extension) and by episode identity (S01E02 vs 1x02)
- Shows a confirmation dialog listing all duplicates with checkboxes
- Only video files are deleted (not subtitles like .srt, .sub, .idx)
- Files are deleted to the OS recycle bin

### Fuzzy episode matching
Added lightweight season/episode pattern extraction for conflict detection and duplicate cleanup. Recognizes formats like `S01E02`, `1x02`, `s1e2` to identify files representing the same episode even when filenames differ.

### Move order matches table display
Files are now moved in the order they appear in the table (top to bottom), matching the visual order shown to the user.

## Code improvements

- Progress tick (checkmark icon) now correctly visible after cross-filesystem copy+delete moves
- TableEditor properly disposed after copy progress completes, revealing underlying status icon
- Diagnostic logging reduced to appropriate levels (FINE instead of INFO for routine operations)

## Bug fixes

- **Overwrite option now works correctly**: The early destination existence check was failing immediately without consulting the overwrite preference. Now properly allows moves when overwrite is enabled.

## Requirements

- Java 17+ runtime
- Windows (SWT native dependency)

