# TVRenamer v1.0.1356

## New features / improvements
- **Session-only Move toggle (main screen)**
  - There is now a **Move** checkbox on the main window that lets you temporarily enable/disable moving files **for this run only**.
  - This **does not change your saved Preferences** — when you restart TVRenamer, it goes back to whatever your Preferences are set to.
  - Practical use: keep Move enabled in Preferences for normal use, but quickly uncheck **Move** when you want a rename-only pass.

- **Action button now reflects your current mode**
  The main action button label updates based on your current Rename/Move settings:
  - **Dry Run**: Rename is OFF and Move is OFF (no changes will be made)
  - **Rename**: Rename is ON, Move is OFF
  - **Move**: Rename is OFF, Move is ON
  - **Full**: Rename is ON and Move is ON (rename + move)

- **Preferences → Matching table layout polish**
  - The “Overrides” and “Disambiguations” tables now start with a fixed status/icon column and split the remaining width evenly between the two text columns, so the tables fill their available space more neatly.
