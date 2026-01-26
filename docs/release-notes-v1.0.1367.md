# TVRenamer v1.0.1367

## New features / improvements
- **Multi-episode filename parsing (single file contains multiple episodes)**
  - TVRenamer now recognizes common “multi-episode in one file” patterns (case-insensitive), including:
    - `S01E04E05` / `S01E04E05E06` (explicit list)
    - `S02E04-E06` and `S02E04-06` (inclusive range)
  - When a multi-episode file is detected:
    - TVRenamer selects the **lowest episode** in the span for matching/lookup.
    - The episode title used for rename output gets a suffix like **`(A-B)`** (compact numbers, no leading zeros).
      - Example: `The Hulk S04E01-E07 Silver Linings.mkv` → title token becomes `Silver Linings (1-7)`.

## Bug fixes
- **Build JDK updated**
  - Builds now use a **Java 21 toolchain** while keeping runtime compatibility at **Java 17+** (bytecode target).
- **Repository rename alignment**
  - Updated in-app and README links to reflect the GitHub repository rename to **TVRenamer**.
- **Ignore IDE output**
  - Added ignore rules for JDTLS/Eclipse project artifacts (e.g., `bin/`, `.classpath`, `.project`, `.settings/`) to avoid accidental commits.

## Artifacts / requirements
- **Runtime:** Java 17+
- **Build tooling:** Java 21 (toolchain)
