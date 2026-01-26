# TVRenamer v1.0.1371

## New features / improvements
- **SpotBugs static analysis (local)**
  - Added SpotBugs to the Gradle build so you can run a local static-analysis pass.
  - Run: `./gradlew spotbugsMain`
  - Report: `build/reports/spotbugs/main.html`
  - This is intended to be **local-only for now** (not enforced in CI).

- **Release process hygiene (avoid stale jars)**
  - Documented a safer release-upload approach to avoid accidentally attaching old versioned jars when `build/libs/` accumulates artifacts (common when `clean` fails due to Windows file locks).
  - Prefer CI artifacts and/or upload explicit jar filenames instead of `build/libs/*.jar`.

## Bug fixes
- **SpotBugs-driven robustness and correctness improvements**
  - Tightened exception handling and defensive checks in download/task submission paths to reduce risk of unexpected null-related failures.
  - Improved map-iteration patterns in move/conflict logic (avoids keySet+get patterns that can be error-prone and flagged by static analysis).
  - Made string lowercasing deterministic in a few code paths by using `Locale.ROOT` where appropriate.

## Notes
- **Runtime requirement:** Java 17+
- **Build tooling:** Java 21 toolchain (bytecode targets Java 17)
