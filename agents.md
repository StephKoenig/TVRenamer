# Agent Notes (TVRenamer)

This file is agent-facing documentation for working on **TVRenamer**. It focuses on:
- project orientation (what it is and how it’s built)
- local development on Windows (build/run/debug logging)
- how CI builds Windows artifacts
- how we collaborate (TODO workflow, completed-work record, commits, PRs, releases)

This doc is intentionally pragmatic: it should be enough for an agent joining cold to build, test, diagnose, and ship changes without guesswork.

---

## Project basics

- **UI framework:** SWT (native UI toolkit)
- **Build system:** Gradle (wrapper included)
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

If you're running in a Windows environment, prefer commands that are stable across shells:
- use `gradlew.bat` in PowerShell/CMD
- use `./gradlew` in Git Bash / sh

### Prerequisites
- **Git**
- **JDK 17**
  - CI uses Temurin 17.
  - Locally you can use any JDK 17 distribution (Temurin, Microsoft, Oracle, etc.).
- **Gradle wrapper** is included; no system Gradle install required.

### Common local commands (repo root)

Fast feedback:
```/dev/null/commands.txt#L1-2
./gradlew test
./gradlew build
```

Default “compile after each change”:
- Run at least `./gradlew build` after each change to catch compile issues early.
- Prefer `./gradlew test` when the change is non-trivial or touches logic that has coverage.

Clean build:
- Preferred when feasible, but may be run on request during a session (Windows file locks can make `clean` fail).
```/dev/null/commands.txt#L1-1
./gradlew clean build
```

Packaging (matches CI intent):
```/dev/null/commands.txt#L1-1
./gradlew clean build shadowJar createExe
```

If Windows file locks prevent `clean`, fall back temporarily:
```/dev/null/commands.txt#L1-1
./gradlew build shadowJar createExe
```

When to run packaging tasks (`shadowJar` / `createExe`):
- Run packaging when you change **UI behavior**, **SWT/layout**, **startup/Launcher**, **resources/icons**, **gradle packaging config**, or anything that might behave differently when launched as an EXE vs from your IDE.
- Prefer running packaging before pushing changes that affect end-user flows, so CI artifacts are likely to work first try.
- For small internal refactors, `./gradlew build` is usually sufficient; defer packaging until requested or before release.

Artifacts:
- JARs: `build/libs/`
- EXE: `build/launch4j/TVRenamer.exe`

Notes:
- On Windows, `clean` can fail if the last built EXE/JAR is still running or open/locked. Close any running `TVRenamer.exe` / Java process and retry.
- The build generates a version file (`tvrenamer.version`) at build time using git commit count.

### Running and log capture (debug file logging)

TVRenamer uses `java.util.logging` with its primary configuration in `/logging.properties`.
A file log (`tvrenamer.log`) is created only when:
- debug is enabled via `-Dtvrenamer.debug=true`, OR
- a fatal error occurs (then we write exception + environment summary)

The log file is written next to the executable/jar if possible, otherwise to `%TEMP%`. The log is overwritten each run.

Enable debug file logging:
- JVM property: `-Dtvrenamer.debug=true`
- PowerShell can mis-parse `-Dname=value`; use the quoted form:

```/dev/null/commands.txt#L1-1
java "-Dtvrenamer.debug=true" -jar .\tvrenamer.jar
```

---

## Working style (how we’ve been operating)

### Compile locally after changes
Preferred loop:
1. Make a small, targeted change.
2. Run a local compile after each change (default):
   - `./gradlew build` (minimum)
   - `./gradlew test` when appropriate
3. Run `./gradlew clean ...` when requested during the session (or when locks/outputs make it necessary):
   - `./gradlew clean build`
   - `./gradlew clean build shadowJar createExe` for full packaging parity
4. Only then commit/push.

### Keep diffs focused
- Avoid unrelated reformatting.
- Avoid line-ending churn on Windows.

---

## TODO + Completed workflow (docs-driven)

This repo keeps:
- **future work** in `docs/TODO.md`, and
- a durable **completed-work record** in `docs/Completed.md`.

This avoids `docs/TODO.md` turning into a changelog while still preserving engineering context (what shipped, why it mattered, and where it lives).

When implementing an item from `docs/TODO.md`:
1. **Do the implementation first**, including tests and any required UI/prefs wiring.
2. **Update `docs/TODO.md` (future only)**:
   - Move completed items out of “Top candidates”.
   - Add/adjust any new top candidates discovered during the work.
   - Keep the file focused on *forward-looking* items.
3. **Add a record to `docs/Completed.md`**:
   - Title, Why, Where (key files/classes), What we did.
   - Capture important assumptions/gotchas (threading, UI thread ownership, persistence keys, encoding responsibilities, etc.).
   - Optionally link to a spec (`docs/*.md`) and/or the versioned release notes file.
4. **Clean up in-code TODO comments**:
   - Remove TODOs that are now addressed.
   - Replace them with a short “Note: addressed; see docs/TODO.md …” where future context is still valuable.
5. **Prefer small commits**:
   - Ideally: one commit per focused TODO item, plus a follow-up commit for documentation/comment cleanup if needed.

Goal: keep code clean, keep `docs/TODO.md` as the single source of truth for future work, and keep `docs/Completed.md` as the durable record of finished work.

---

## Git workflow (commit and push)

Common sequence:
```/dev/null/git.txt#L1-6
git status
git diff
git add -A
git commit -m "Meaningful summary of change"
git push
```

This repo’s current working mode is to commit and push directly to `master` (after a local compile).
If you need review/approval or want to isolate risk, create a PR instead:
```/dev/null/git.txt#L1-2
gh pr create --fill
gh pr view --web
```

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
2. Compile locally after each change (preferred).
3. Push to `origin/master` (or open a PR) and let Actions build Windows artifacts.
4. Use CI artifacts as the source of truth for releases.

---

## GitHub CLI (`gh`) usage

The GitHub CLI is useful for:
- watching Actions runs / build status
- viewing logs
- downloading artifacts
- creating PRs
- creating Releases

Assumptions:
- the local repo is connected to the correct GitHub remote
- you are authenticated (`gh auth status`)

Common commands:

List recent runs for `master`:
```/dev/null/gh.txt#L1-1
gh run list --branch master --limit 10
```

Watch the latest run until completion:
```/dev/null/gh.txt#L1-1
gh run watch --exit-status
```

View logs (useful on failures):
```/dev/null/gh.txt#L1-1
gh run view --log-failed
```

Download artifacts from the latest run:
```/dev/null/gh.txt#L1-1
gh run download --dir ./artifacts
```

Download artifacts for a specific run:
```/dev/null/gh.txt#L1-1
gh run download <run-id> --dir ./artifacts
```

---

## Manual GitHub Release procedure (using tested CI artifacts)

### Release notes format (Markdown)
When creating/editing a GitHub Release, write release notes in **Markdown** and use this structure:

1. **New features / improvements** (first)
2. **Bug fixes** (second)
3. Optional: Requirements / Artifacts / CI run link

This makes releases easy to scan and consistent across versions.

#### Keep a committed release-notes record (recommended)
To prevent “empty release body” mistakes and to keep a durable record in the repo, write the release notes into a versioned Markdown file under `docs/`, then publish that file as the GitHub Release body.

Suggested filename:
- `docs/release-notes-v1.0.<commitCount>.md`

Workflow:
1. Create/update the notes file and commit it (so it is part of the release changeset).
2. Publish it to the GitHub Release body:
```/dev/null/release-notes.txt#L1-1
gh release edit v1.0.<commitCount> --notes-file docs/release-notes-v1.0.<commitCount>.md
```

Notes:
- This repo prefers keeping these `docs/release-notes-*.md` files as a record.
- If you create the release first with an empty body, you can still fix it later using the same `gh release edit ... --notes-file ...` command.

This project intentionally does **not** auto-release on every successful build. Releases are created manually when you decide the current state is ready.

### Versioning
- The project embeds a build version of the form: `1.0.<commitCount>`
- Release tags should match that scheme: `v1.0.<commitCount>`
- `<commitCount>` is computed from git:
  - `git rev-list --count HEAD`

### Release source of truth
- Releases should use artifacts from the **latest successful GitHub Actions run** on `master` (Windows build), rather than an unverified local build.
- Attach **all JARs** and the Windows EXE.

### Preconditions / safety checks
1. Confirm `HEAD` is the commit you want to release.
2. Confirm there is a **successful** Actions run for that commit on `master`.
3. Confirm the tag does **not** already exist.
   - If `v1.0.<commitCount>` already exists, stop and ask the user what to do next (do not overwrite tags).

### Steps (high level)
1. Compute `<commitCount>` and the tag name:
   - `git rev-list --count HEAD` → `v1.0.<commitCount>`
2. Download artifacts from the matching successful CI run (prefer the exact commit SHA).
3. Create and push the git tag:
```/dev/null/release.txt#L1-2
git tag v1.0.<commitCount>
git push origin v1.0.<commitCount>
```
4. Create the GitHub Release and upload assets:
   - include `TVRenamer.exe`
   - include all `*.jar` files from the artifact

Notes:
- GitHub Actions artifacts can expire; Releases are the long-lived distribution channel.

---

## Platform notes (SWT + Windows)

- SWT is platform-native; UI behavior and theming can vary by OS and SWT version.
- Some UI elements (menus, tab headers, title bars) may remain OS-themed and not fully controllable from SWT.
- Validate UI changes on Windows first (CI builds on Windows).

---

## Repository hygiene / gotchas

- Line endings: Windows checkouts may flip LF/CRLF depending on Git settings. Avoid churn by not reformatting unrelated files.
- Generated outputs should not be committed:
  - `build/` is output-only
- `docs/` is tracked and intended for specs/planning; CI does not publish it as an artifact.

---

## When diagnosing issues

CI failures:
- use `gh run view --log-failed` to quickly see why a run failed
- confirm the run corresponds to the commit SHA you expect

Runtime issues:
- enable file logging with `-Dtvrenamer.debug=true` and attach `tvrenamer.log` snippets to the investigation

### Debugging workflow hygiene (temporary changes)
When investigating a bug, it’s common to add temporary diagnostics (extra logging, environment dumps, assertions, etc.).

Guideline:
- Do **not** commit/push debug-only changes while the investigation is in progress unless the user explicitly requests it.
- Keep temporary debug instrumentation local until you’ve identified the root cause and are ready to ship a real fix.
- If a temporary diagnostic ends up being genuinely useful long-term, keep it — but make it intentional (clean message, appropriate log level, and documented where needed).
