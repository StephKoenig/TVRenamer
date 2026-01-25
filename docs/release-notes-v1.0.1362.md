# TVRenamer v1.0.1362

## Bug fixes
- **Runtime Move toggle now affects real behavior**
  - The **Move** checkbox on the main screen is intended to be a **session-only override** (it does not change saved Preferences).
  - In v1.0.1356, toggling **Move** could update some UI labels but **did not reliably prevent moving files**, which could be confusing and risky.
  - In v1.0.1362, toggling **Move** now updates the in-memory move setting for this run, so:
    - the **Proposed File Name** column reflects the current mode, and
    - executing actions will **not move files** when **Move** is unchecked.

## Notes
- This is a small follow-up release to v1.0.1356 to ensure the new session-only Move control is trustworthy and consistent across UI and execution.