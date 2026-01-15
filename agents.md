# Agent Notes (TVRenamer)

This file is agent-facing documentation for working on **TVRenamer**. It focuses on: available tooling, how to build locally on Windows, how the project compiles remotely via GitHub Actions, and how to perform **manual GitHub Releases** using tested CI artifacts.

---

## Project basics

- **UI framework:** SWT (native UI toolkit)
- **Build system:** Gradle
- **Primary target OS:** Windows (SWT dependency is configured for Windows in `build.gradle`)
- **Java version:** JDK 17 (toolchain enforced)

Key files:
- `build.gradle` — build + packaging (ShadowJar, Launch4j)
- `.github/workflows/windows-build.yml` — Windows CI build + artifact uploads
- `gradlew` / `gradlew.bat` — Gradle wrapper scripts

---

## Local development environment (Windows)

### Shells you can use
- **PowerShell**: recommended for interactive Windows work
- **Git Bash / sh**: works fine for Gradle and git commands as well

If you're an agent interacting with a Windows environment, prefer commands that are stable across shells (e.g., `gradlew.bat` on Windows, or `./gradlew` if you know you’re in a Unix-like shell).

### Prerequisites
- **Git**
- **JDK 17**
  - CI uses Temurin 17.
  - Locally you can use any JDK 17 distribution (Temurin, Microsoft, Oracle, etc).
- **Gradle wrapper** is included; you do not need a system Gradle install.

### Common local commands

From the repo root:

Build + run tests:
```/dev/null/commands.txt#L1-3
./gradlew test
./gradlew build
./gradlew clean build
```

Create runnable fat jar (ShadowJar):
```/dev/null/commands.txt#L1-1
./gradlew shadowJar
```

Create Windows exe (Launch4j):
```/dev/null/commands.txt#L1-1
./gradlew createExe
```

Full packaging pipeline (similar to CI intent):
```/dev/null/commands.txt#L1-1
./gradlew build shadowJar createExe
```

Notes:
- The build generates a version file (`tvrenamer.version`) at build time using git commit count.
- Output locations of interest:
  - JARs: `build/libs/`
  - EXE: `build/launch4j/TVRenamer.exe`

---

## Remote compile (GitHub Actions CI)

### What runs in CI
CI workflow file: `.github/workflows/windows-build.yml`

Triggers:
- `push` to `master` or `main`
- `pull_request` targeting `master` or `main`

Environment:
- `runs-on: windows-latest`
- JDK 17 (Temurin via `actions/setup-java`)
- Gradle 8.5 via `gradle/actions/setup-gradle`

Build step (as configured in CI):
```/dev/null/commands.txt#L1-1
gradle build shadowJar createExe --info
```

Artifacts uploaded by CI:
- `TVRenamer-Windows-Exe` → `build/launch4j/TVRenamer.exe`
- `TVRenamer-JAR` → `build/libs/*.jar`

### Practical workflow for agents
1. Make changes locally.
2. Run `./gradlew test` (or at least `./gradlew build`) before pushing.
3. Push to `origin/master` (or open a PR) and let Actions build Windows artifacts.
4. Download the artifact to validate the packaged output.

---

## GitHub CLI (`gh`) usage

The GitHub CLI is useful for:
- Watching Actions runs
- Viewing logs
- Downloading artifacts
- Creating PRs
- Creating Releases

Assumptions:
- You are authenticated (`gh auth status`)
- The local repo is connected to the correct GitHub remote

Common commands:

List recent runs for `master`:
```/dev/null/gh.txt#L1-1
gh run list --branch master --limit 10
```

Watch the latest run until completion (returns non-zero exit on failure):
```/dev/null/gh.txt#L1-1
gh run watch --exit-status
```

View logs (useful on failures):
```/dev/null/gh.txt#L1-1
gh run view --log-failed
```

Download artifacts from the latest run into a local directory:
```/dev/null/gh.txt#L1-1
gh run download --dir ./artifacts
```

Download a specific run:
```/dev/null/gh.txt#L1-1
gh run download <run-id> --dir ./artifacts
```

Create a PR (recommended when not pushing directly to `master`):
```/dev/null/gh.txt#L1-2
gh pr create --fill
gh pr view --web
```

---

## Manual GitHub Release procedure (using tested CI artifacts)

This project intentionally does **not** auto-release on every successful build. Releases are created manually when you decide the current state is ready.

### Versioning
- The project embeds a build version of the form: `1.0.<commitCount>`
- Release tags should match that scheme: `v1.0.<commitCount>`
- `<commitCount>` is computed from the Git repository, consistent with the build logic:
  - `git rev-list --count HEAD`

### Release source of truth
- Releases should use artifacts from the **latest successful GitHub Actions run** on `master` (Windows build), rather than an unverified local build.
- Attach **all JARs** and the Windows EXE.

### Preconditions / safety checks
1. Confirm `HEAD` is the commit you want to release.
2. Confirm there is a **successful** Actions run for that commit on `master`.
3. Confirm the tag does **not** already exist:
   - If `v1.0.<commitCount>` already exists, stop and ask the user what to do next (do not overwrite tags).

### Steps (high level)
1. Compute `<commitCount>` for `HEAD` and construct the tag `v1.0.<commitCount>`.
2. Locate the latest successful CI run for `master` (preferably matching `HEAD`).
3. Download artifacts into a local folder (e.g. `./artifacts`):
   - `TVRenamer-Windows-Exe`
   - `TVRenamer-JAR`
4. Create and push the git tag:
   - `git tag v1.0.<commitCount>`
   - `git push origin v1.0.<commitCount>`
5. Create the GitHub Release and upload assets:
   - include `TVRenamer.exe`
   - include **all** `*.jar` files from the artifact

### Notes
- GitHub Actions “artifacts” are great for testing but are not a substitute for Releases (they can expire and are less discoverable).
- GitHub Releases are intended as the long-lived distribution channel for end users.

---

## Platform notes (SWT + Windows)

- SWT is platform-native; UI behavior and theming can vary by OS and SWT version.
- Some UI elements (menus, tab headers, title bars) may remain OS-themed and not fully controllable from SWT.
- If you change UI theming behavior, validate on Windows first (CI builds on Windows).

---

## Repository hygiene / gotchas

- Line endings: Windows checkouts may flip LF/CRLF depending on Git settings. Avoid churn by not reformatting unrelated files.
- Generated outputs should not be committed:
  - `build/` is output-only
- Documentation directory:
  - `docs/` is ignored by `.gitignore` in this repo (do not expect CI to include it unless ignore rules change)

---

## When interacting as an agent

When making changes:
- Prefer minimal, targeted diffs.
- Run `./gradlew test` before pushing.
- If UI changes are involved, ensure the packaged EXE and fat JAR still build successfully (CI will confirm).

When diagnosing CI failures:
- Check the run logs via GitHub Actions UI or `gh run view --log-failed`.
- Confirm the run corresponds to the commit you expect (SHA matches).

---