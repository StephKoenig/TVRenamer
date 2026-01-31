# Strings Spec: Encoding & Transformation Responsibilities

This document specifies how TVRenamer handles string encoding, transformation, and sanitization across different contexts. The goal is to keep responsibilities **separate and explicit** to avoid corruption, data loss, or security issues.

---

## Core Principle: Transform at Boundaries

Strings should be transformed **once**, at the appropriate boundary:

| Boundary | Transformation | When |
|----------|---------------|------|
| **URL query parameter** | URL-encode | When constructing API request URLs |
| **Filename output** | Sanitize illegal chars | When generating rename target |
| **Display to user** | Normalize whitespace | When showing in UI (if needed) |
| **XML from provider** | **None** | Never mutate downloaded XML |

**Anti-pattern:** Transforming a string multiple times or at the wrong layer (e.g., URL-encoding an XML payload, or sanitizing a string before it reaches filename construction).

---

## Transformation Functions

### 1. URL Encoding (`StringUtils`)

**Purpose:** Encode strings for use in URL query parameters.

```java
// Encode for URL query string
String encoded = StringUtils.encodeUrlQueryParam("Fish & Chips");
// Result: "Fish+%26+Chips"

// Decode from URL query string
String decoded = StringUtils.decodeUrlQueryParam(encoded);
// Result: "Fish & Chips"
```

**When to use:**
- Building API request URLs (e.g., TVDB search)
- Never for XML payloads or filenames

**Implementation:** Uses `java.net.URLEncoder`/`URLDecoder` with UTF-8.

---

### 2. Filename Sanitization (`StringUtils`)

**Purpose:** Remove or replace characters that are illegal in Windows filenames.

**Illegal characters:** `\ / : * ? " < > |`

```java
// Full sanitization (trims whitespace + replaces illegal chars)
String safe = StringUtils.sanitiseTitle("Mission: Impossible?");
// Result: "Mission- Impossible"

// Just replace illegal chars (preserves whitespace)
String replaced = StringUtils.replaceIllegalCharacters("  Test: File  ");
// Result: "  Test- File  "
```

**Replacement rules:**

| Character | Replacement | Rationale |
|-----------|-------------|-----------|
| `\` | `-` | Path separator |
| `/` | `-` | Path separator |
| `:` | `-` | Drive separator (Windows) |
| `\|` | `-` | Pipe |
| `*` | `-` | Wildcard (preserve word shape, e.g., "C**tgate" → "C--tgate") |
| `?` | (removed) | Wildcard |
| `<` | (removed) | Redirect |
| `>` | (removed) | Redirect |
| `"` | `'` | Quote → apostrophe |
| `` ` `` | `'` | Backtick → apostrophe |

**When to use:**
- Final step of filename construction (in `EpisodeReplacementFormatter.format()`)
- Show directory names (in `Show` constructor)
- User-provided season prefix (in `PreferencesDialog`)

**Call sites (audit):**
1. `EpisodeReplacementFormatter.format()` — applies `sanitiseTitle()` to final output
2. `Show` constructor — applies `sanitiseTitle()` to create `dirName`
3. `PreferencesDialog` — applies `replaceIllegalCharacters()` to season prefix

---

### 3. Query String Normalization (`StringUtils`)

**Purpose:** Transform a filename substring into a provider-friendly search query.

```java
String query = StringUtils.makeQueryString("Marvel's.Agents.of.S.H.I.E.L.D.");
// Result: "marvels agents of shield"
```

**Transformations applied:**
1. Remove apostrophes (e.g., "Bob's" → "Bobs")
2. Expand CamelCase (e.g., "MythBusters" → "Myth Busters")
3. Condense acronyms (e.g., "S.H.I.E.L.D." → "SHIELD")
4. Replace punctuation with spaces
5. Collapse multiple spaces
6. Convert to lowercase

**When to use:**
- Preparing show name for TVDB search
- Never for filenames or display

---

### 4. XML Handling

**Rule:** Downloaded XML payloads must **never** be modified.

The provider returns well-formed XML. Any "encoding" or "special character handling" applied to the raw response risks:
- Corrupting entity references (`&amp;` → `&` → broken XML)
- Breaking character encoding
- Introducing parse failures

**Historical note:** A method called `encodeSpecialCharacters()` previously attempted to "fix" XML and caused bugs. It has been removed; the codebase now correctly passes XML through unchanged.

---

## Tricky Title Examples

These real-world titles exercise edge cases in sanitization:

| Original Title | After `sanitiseTitle()` | Notes |
|----------------|------------------------|-------|
| `Mission: Impossible - Fallout (2018)` | `Mission- Impossible - Fallout (2018)` | Colon replaced |
| `V/H/S` | `V-H-S` | Slashes replaced |
| `? (2021)` | ` (2021)` | Question mark removed (space preserved) |
| `Why?` | `Why` | Question mark removed |
| `"What" (2013)` | `'What' (2013)` | Double quotes → apostrophes |
| `S.W.A.T. (2017)` | `S.W.A.T. (2017)` | Dots are legal, unchanged |
| `*batteries not included` | `-batteries not included` | Asterisk replaced |
| `Woodstock '99` | `Woodstock '99` | Apostrophe is legal, unchanged |
| `Fish & Chips` | `Fish & Chips` | Ampersand is legal in filenames |
| `C**tgate` | `C--tgate` | Asterisks replaced, preserving word shape |

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         FILENAME PARSING                            │
│  "Marvels.Agents.of.S.H.I.E.L.D.S01E01.720p.mkv"                   │
│                           │                                         │
│                           ▼                                         │
│              ┌────────────────────────┐                            │
│              │  FilenameParser        │                            │
│              │  (extract show name)   │                            │
│              └────────────────────────┘                            │
│                           │                                         │
│              "Marvels.Agents.of.S.H.I.E.L.D."                      │
│                           │                                         │
│                           ▼                                         │
│              ┌────────────────────────┐                            │
│              │  makeQueryString()     │  ◄── Normalize for search  │
│              └────────────────────────┘                            │
│                           │                                         │
│              "marvels agents of shield"                            │
│                           │                                         │
│                           ▼                                         │
│              ┌────────────────────────┐                            │
│              │  encodeUrlQueryParam() │  ◄── Encode for URL        │
│              └────────────────────────┘                            │
│                           │                                         │
│              "marvels+agents+of+shield"                            │
│                           │                                         │
└───────────────────────────┼─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         TVDB API                                    │
│  GET /api/GetSeries.php?seriesname=marvels+agents+of+shield        │
│                           │                                         │
│                           ▼                                         │
│              XML Response (DO NOT MODIFY)                          │
│              <SeriesName>Marvel's Agents of S.H.I.E.L.D.</...>     │
│              <EpisodeName>Pilot</EpisodeName>                      │
└───────────────────────────┼─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      FILENAME GENERATION                            │
│                           │                                         │
│              ┌────────────────────────┐                            │
│              │  EpisodeReplacement-   │                            │
│              │  Formatter.format()    │                            │
│              └────────────────────────┘                            │
│                           │                                         │
│              Template: "%S S%0sE%0e %t"                            │
│              Show: "Marvel's Agents of S.H.I.E.L.D."               │
│              Episode: "Pilot"                                       │
│                           │                                         │
│                           ▼                                         │
│              ┌────────────────────────┐                            │
│              │  sanitiseTitle()       │  ◄── Final sanitization    │
│              └────────────────────────┘                            │
│                           │                                         │
│              "Marvel's Agents of S.H.I.E.L.D. S01E01 Pilot.mkv"   │
│              (No illegal chars in this example)                    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Testing Strategy

Tests should verify:

1. **Round-trip URL encoding** — encode then decode returns original
2. **Filename sanitization** — each illegal character is handled correctly
3. **Tricky titles** — real-world edge cases produce safe filenames
4. **Full path integration** — provider data → formatted filename

See: `StringUtilsTest.java`, `TrickyTitlesTest.java`

---

## Changelog

- **2026-01:** Initial spec documenting encoding responsibilities after audit
