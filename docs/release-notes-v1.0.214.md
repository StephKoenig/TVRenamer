## Important: Version Number Reset

This release marks a fresh start for the codebase. To simplify maintenance and establish clear ownership, the git history has been consolidated - all prior commits have been squashed into a single initial commit with full attribution to the original TVRenamer authors.

**What this means:**
- Version numbering resets from v1.0.1394 to v1.0.214
- All functionality from the previous version is preserved
- Future versions will increment normally from here

If you were using a previous version, we recommend updating to this release as the baseline going forward.

## Bug fixes

- **Multi-episode filenames now Plex-compatible**: Files containing multiple episodes (e.g., `Show.S01E04E05.mkv`) now output the episode range in the filename position (`S01E04e05`) instead of appending `(4-5)` to the title. This allows Plex and other media servers to correctly index all episodes in the file.
  - Two consecutive episodes: `E04e05` (with separator to avoid ambiguity with high episode numbers like 405)
  - Three or more episodes: `E01-e04` (dash-separated range)

## Improvements

- **Increased file copy buffer**: Buffer size increased from 4 MiB to 8 MiB for better performance on modern networks and large media files.

- **Case-insensitive ignore keywords**: The ignore keywords feature now works regardless of case in filenames.

## Recent features (since v1.0.1381)

For users updating from older versions, this release also includes:
- Embedded help system with static HTML pages
- Enhanced Preferences rename tab with pill-styled tokens and live preview
- MKV and MP4 metadata tagging support
- Improved filename parsing diagnostics
