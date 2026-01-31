## New Features

### MKV Metadata Tagging
TVRenamer can now embed TV metadata directly into MKV and WebM files using [MKVToolNix](https://mkvtoolnix.download/).

**How it works:**
- Requires MKVToolNix to be installed (mkvpropedit must be accessible)
- If MKVToolNix is not installed, MKV files are silently skipped (not an error)
- Writes Matroska tags at multiple levels for broad media manager compatibility:
  - **Collection level**: Show name, content type
  - **Season level**: Season number
  - **Episode level**: Episode title, number, air date
- Sets segment title to filename for display in players like VLC

This complements the existing **MP4/M4V/MOV tagging** which uses iTunes-style atoms and works with Plex, Kodi, Jellyfin, iTunes, and VLC.

**To enable:** Check "Tag video files with episode metadata" in Preferences.

### Additional Features (since last release)

- **Always Overwrite**: Option to overwrite existing destination files instead of creating versioned copies like `(1)`, `(2)`
- **Duplicate Cleanup**: After moving a file, optionally delete other video files representing the same episode. Shows a confirmation dialog listing files to be deleted.
- **Fuzzy Episode Matching**: Detects files like "S01E02" vs "1x02" as the same episode for conflict detection and duplicate cleanup
- **Parse Failure Diagnostics**: When files can't be parsed, shows specific reasons (no show name found, no season/episode pattern, etc.) with a summary dialog after batch processing

## Improvements

- Updated preferences tooltip to describe supported tagging formats (MP4 and MKV)
- Comprehensive documentation updates (README, Tagging Spec, TODO, Completed)

## Library Versions

| Component | Version |
|-----------|---------|
| SWT (Windows x64) | 3.129.0 |
| XStream | 1.4.21 |
| Apache Commons Codec | 1.21.0 |
| OkHttp | 5.3.2 |
| mp4parser | 1.9.56 |
| JUnit | 5.11.4 |
| Gradle | 9.3.1 |

## Requirements

- **Windows x64** (64-bit)
- **Java 17+** runtime
- **MKVToolNix** (optional, for MKV tagging only)

## Downloads

- `TVRenamer.exe` - Windows executable
- `tvrenamer-1392.jar` - Versioned fat JAR
- `tvrenamer.jar` - Stable fat JAR name
