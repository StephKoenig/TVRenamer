# Episode Chain Propagation

## Problem

When a filename matches an episode number that has ambiguous titles (typically from
mismatched air vs DVD ordering in the listings provider), TVRenamer shows a Combo
dropdown with both options. Often, consecutive episodes share overlapping titles
in a chain pattern:

```
ep18: [Cry Wolf, Crash Diet]
ep19: [Crash Diet, Rainy Day]
ep20: [Rainy Day, Crack-Up]
```

Today each row is independent — the user must manually select the correct title
for every ambiguous episode, even though selecting one logically determines the
rest of the chain.

## Solution

When the user selects an episode title from a Combo, **chain propagation** finds
other episodes (same show) that share the selected title as an option and
pre-selects their alternative. This cascades through the chain:

1. User selects "Crash Diet" for ep18
2. ep19 also has "Crash Diet" as an option → pre-select "Rainy Day" instead
3. ep20 also has "Rainy Day" as an option → pre-select "Crack-Up" instead
4. "Crack-Up" doesn't overlap with any other episode → chain ends

Pre-selections are non-destructive: the Combo retains both options and the user
can override any choice, which re-triggers propagation from that point.

## Algorithm

Constraint propagation with recursion and a visited set:

```
propagateEpisodeChain(sourceEp, selectedTitle, visited):
    for each (tableItem, otherEp) in the table:
        skip if otherEp == sourceEp or already visited
        skip if different show
        skip if otherEp.optionCount() != 2

        titleIndex = otherEp.indexOfEpisodeTitle(selectedTitle)
        if titleIndex < 0: continue          // title not in this ep's options

        otherIndex = 1 - titleIndex          // the OTHER option (0↔1)
        if already selected correctly: continue

        otherEp.setChosenEpisode(otherIndex) // update model
        update Combo widget to match          // update UI
        visited.add(otherEp)

        newTitle = otherEp.getEpisodeTitle(otherIndex)
        propagateEpisodeChain(otherEp, newTitle, visited)   // cascade
```

### Guards

- **Infinite loops**: `Set<FileEpisode> visited` passed through recursion prevents
  revisiting the same episode.
- **Combo re-entry**: A `propagatingChain` boolean flag in ResultsTable prevents
  the Combo ModifyListener from triggering nested propagation when the widget is
  updated programmatically.
- **Same show only**: `otherEp.getActualShow() == sourceEp.getActualShow()` prevents
  cross-show interference.

### Constraints

- Only episodes with exactly 2 options participate. Episodes with 1 option (no
  ambiguity) or 3+ options (can't determine which to pick) are skipped.
- Title matching is by exact string equality on the Episode title.

## Data Flow

```
User clicks Combo for ep18 → selects "Crash Diet"
  │
  ├─ Combo ModifyListener fires
  │   ├─ ep18.setChosenEpisode(idx)
  │   ├─ propagatingChain = true
  │   └─ propagateEpisodeChain(ep18, "Crash Diet", {ep18})
  │       │
  │       ├─ ep19 has "Crash Diet" at index 0 → flip to index 1 ("Rainy Day")
  │       │   ├─ ep19.setChosenEpisode(1)
  │       │   ├─ combo19.select(1)
  │       │   └─ propagateEpisodeChain(ep19, "Rainy Day", {ep18, ep19})
  │       │       │
  │       │       └─ ep20 has "Rainy Day" at index 0 → flip to index 1 ("Crack-Up")
  │       │           ├─ ep20.setChosenEpisode(1)
  │       │           ├─ combo20.select(1)
  │       │           └─ propagateEpisodeChain(ep20, "Crack-Up", {ep18, ep19, ep20})
  │       │               └─ no more matches → return
  │       └─ no more matches at this level → return
  │
  └─ propagatingChain = false
```

## Edge Cases

| Case | Behaviour |
|------|-----------|
| No chain (title unique to one episode) | No propagation — behaves exactly as before |
| Long chain (5+ episodes) | Recursion follows the full chain to completion |
| Different shows with same title | No cross-show propagation (same-show guard) |
| 3+ options per episode | Skipped — only 2-option episodes participate |
| User changes mind | Re-selecting triggers fresh propagation from that point |
| Files loaded after initial propagation | No retroactive propagation; chain runs at selection time |

## Files Modified (Phase 1 — Chain Propagation)

| File | Change |
|------|--------|
| `FileEpisode.java` | Add `getEpisodeTitle(int)` and `indexOfEpisodeTitle(String)` |
| `ResultsTable.java` | Add `propagatingChain` flag, `propagateEpisodeChain()` method, update Combo listener |

---

## Phase 2 — Fuzzy Pre-Selection from Filename

### Problem

Chain propagation solves cascading once the user picks an option, but it doesn't help
with the **initial** selection. Many filenames already contain the episode title:

```
CHiPs.S03E18.Off.Road.1080p.WEBRip.mkv
```

The two Combo options might be "Off Road" and "Kidnap". We can fuzzy-match the
filename text against the options to pre-select the right one automatically.

### Algorithm

When episode options are loaded and `optionCount() == 2`:

1. Extract title-like text from the filename: strip the show name, `S##E##`,
   resolution, codec tags, and extension — leaving tokens like `Off.Road`.
2. Normalize: replace dots/underscores with spaces, collapse whitespace.
3. Score each option's episode title against the extracted text using the existing
   `ShowSelectionEvaluator.similarity()` (Levenshtein-based, 0.0–1.0).
4. If best score >= 0.6 AND gap to second-best >= 0.15, pre-select the winner.
5. Run chain propagation from the pre-selected choice to cascade.

### Where it hooks in

The natural point is `FileEpisode.listingsComplete()` — after `actualEpisodes` is
populated and `buildReplacementTextOptions()` is called. If fuzzy matching picks
an option, set `chosenEpisode` before the Combo is created. The chain propagation
would then run from `setProposedDestColumn()` in ResultsTable (after the Combo is
wired up).

Alternatively, do the fuzzy pre-selection in `ResultsTable.setComboBoxProposedDest()`
just before creating the Combo — this keeps all selection logic in one place and
gives immediate access to trigger propagation.

### Existing code to reuse

- `ShowSelectionEvaluator.similarity()` — Levenshtein distance scoring (0.0–1.0)
- `FilenameParser` — already strips show name and S##E## from filenames; the
  remaining text after `placement` is extracted could be reused
- `FileEpisode.getEpisodeTitle(int)` and `indexOfEpisodeTitle(String)` — added
  in Phase 1

### Files to modify

| File | Change |
|------|--------|
| `FileEpisode.java` | Add method to extract title-like text from original filename |
| `ResultsTable.java` | Call fuzzy pre-selection before Combo creation, then propagate |

### Thresholds

- **Minimum score**: 0.6 (same ballpark as show fuzzy matching at 0.8, but lower
  because filename text is often abbreviated/truncated)
- **Minimum gap**: 0.15 (prevents false positives when both options score similarly)
- These should be tuned empirically with real filenames
