# TVRenamer v1.0.1334

## New features / improvements

- **Unified Matching experience (Preferences → Matching):** consolidated workflow for editing **Overrides** and **Disambiguations** with online validation and save gating.
- **Unified show selection evaluator:** introduced `org.tvrenamer.model.ShowSelectionEvaluator` as the single source of truth for show auto-selection decisions, used by both runtime selection and Matching-tab validation.
- **Deterministic selection heuristics (tie-breakers):**
  - Prefer base title over parenthetical variants when the base exists (e.g., `Title` vs `Title (IN)`/`(CN)`).
  - Prefer exact canonical token matches over candidates with extra tokens (strict).
  - Use `FirstAiredYear ± 1` when the extracted name contains a year token (conservative, user-correctable via Matching rules).
- **Overrides now affect runtime provider lookup consistently:** preserved extracted show name for UI/debugging while applying overrides to the effective provider lookup name.

## Major bug fixes

- Fixed Matching tab **Save/OK button gating** where validated rows never re-enabled Save (status column text is blank by design; gating now respects the OK icon).
- Fixed Matching tab **Save persistence** where Overrides/Disambiguations were not written due to saving the wrong table columns (status column vs key/value columns).
- Fixed runtime behavior where show name overrides could validate in Preferences but not actually affect show lookup for dragged-in files.
- Removed legacy dev/IDE artifacts:
  - Removed `etc/idea` (IntelliJ project templates).
  - Removed legacy `etc/` scripts/configs (Ant/Ivy-era run scripts, Checkstyle/formatter profiles, and unused override defaults).