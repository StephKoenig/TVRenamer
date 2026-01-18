# TVRenamer v1.0.1353

## New features / improvements

- **Help menu groundwork:** Added a TODO to create simple static help pages and wire the **Help → Help** menu item to open them (planned via GitHub Pages).
- **Consistent dialog placement:** Major custom dialogs (About, Preferences, Select Shows) are now positioned relative to the main window (centered with a small offset) instead of appearing in OS-random locations.
- **Improved distribution artifacts:**
  - Produce a stable fat jar: `tvrenamer.jar` (easy for scripts and local testing).
  - Produce an additional versioned fat jar: `tvrenamer-<commitCount>.jar` (preferred for Releases and side-by-side installs).
  - CI artifacts now include both jars and the Windows EXE.

## Major bug fixes

- **Version consistency across artifacts:** EXE/JAR versioning is now based on **git commit count** everywhere (including CI) to prevent mismatched version schemes and incorrect “Update available” prompts.
- **SWT upgrade stability:** Pinned SWT to **3.129.0** after discovering SWT ≥ 3.130 could fail to load on a Windows 11 x64 environment despite WebView2 + VC runtimes being installed.

## Notes

- Releases should prefer attaching the **versioned** jar (`tvrenamer-<commitCount>.jar`) so downloaded files are self-describing.
- The stable `tvrenamer.jar` remains ideal for quick local runs and scripted testing.