package org.tvrenamer.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tvrenamer.controller.util.StringUtils;

/**
 * Pure (side-effect free) evaluator that centralizes show selection decisions.
 *
 * <p>This class intentionally does NOT:
 * <ul>
 *   <li>call the provider</li>
 *   <li>mutate {@link ShowName}, {@link ShowStore}, preferences, or caches</li>
 *   <li>queue disambiguations or interact with UI</li>
 * </ul>
 *
 * <p>It is designed to be used by:
 * <ul>
 *   <li>Runtime selection in {@code ShowStore.downloadShow(...)} to decide whether to auto-resolve or prompt</li>
 *   <li>Preferences Matching tab validation to decide whether an entry is "likely to resolve"</li>
 * </ul>
 *
 * <p>Inputs:
 * <ul>
 *   <li>{@code extractedName}: the string to compare against candidate SeriesName and aliases.
 *       In runtime this should be the extracted show name (as displayed to the user); in override validation
 *       this should be the replacement text.</li>
 *   <li>{@code options}: provider candidates (may be empty).</li>
 *   <li>{@code pinnedId}: optional pinned provider id for this query string; if present and found in candidates,
 *       it wins.</li>
 * </ul>
 */
public final class ShowSelectionEvaluator {

    private ShowSelectionEvaluator() {
        // utility
    }

    public enum OutcomeType {
        RESOLVED,
        AMBIGUOUS,
        NOT_FOUND,
    }

    public static final class Decision {

        private final OutcomeType type;
        private final ShowOption chosen;
        private final String message;

        private Decision(
            final OutcomeType type,
            final ShowOption chosen,
            final String message
        ) {
            this.type = Objects.requireNonNull(type, "type");
            this.chosen = chosen;
            this.message = (message == null) ? "" : message;
        }

        public OutcomeType getType() {
            return type;
        }

        /**
         * @return chosen option when {@link #getType()} is {@link OutcomeType#RESOLVED}; otherwise null.
         */
        public ShowOption getChosen() {
            return chosen;
        }

        /**
         * @return an explainable reason suitable for logs/UI validation messages.
         */
        public String getMessage() {
            return message;
        }

        public boolean isResolved() {
            return type == OutcomeType.RESOLVED;
        }

        public boolean isAmbiguous() {
            return type == OutcomeType.AMBIGUOUS;
        }

        public boolean isNotFound() {
            return type == OutcomeType.NOT_FOUND;
        }

        static Decision resolved(final ShowOption chosen, final String msg) {
            return new Decision(OutcomeType.RESOLVED, chosen, msg);
        }

        static Decision ambiguous(final String msg) {
            return new Decision(OutcomeType.AMBIGUOUS, null, msg);
        }

        static Decision notFound(final String msg) {
            return new Decision(OutcomeType.NOT_FOUND, null, msg);
        }
    }

    private static final Pattern YEAR_TOKEN = Pattern.compile(
        "(^|\\s|\\()(?<year>19\\d{2}|20\\d{2})(\\s|\\)|$)"
    );

    /**
     * Evaluate whether a set of provider candidates can be auto-resolved without prompting,
     * given the extracted show name and optional pinned id.
     *
     * <p>Decision order (high-level):
     * <ol>
     *   <li>If candidates empty → NOT_FOUND</li>
     *   <li>If pinned id matches a candidate → RESOLVED</li>
     *   <li>If candidate SeriesName matches extracted name (case-insensitive) → RESOLVED</li>
     *   <li>If candidate SeriesName matches punctuation-normalized extracted name → RESOLVED</li>
     *   <li>If candidate Alias matches extracted name or normalized extracted name → RESOLVED</li>
     *   <li>Tie-breakers (deterministic; spec-driven)</li>
     *   <li>If exactly one candidate → RESOLVED</li>
     *   <li>Otherwise → AMBIGUOUS</li>
     * </ol>
     *
     * <p>Note: This intentionally does not implement fuzzy matching. Tie-breakers should be
     * deterministic and spec-driven.
     */
    public static Decision evaluate(
        final String extractedName,
        final List<ShowOption> options,
        final String pinnedId
    ) {
        if (options == null || options.isEmpty()) {
            return Decision.notFound("No matches");
        }

        // 1) Pinned id wins if it exists in the current candidate set.
        if (pinnedId != null && !pinnedId.isBlank()) {
            for (ShowOption opt : options) {
                if (opt == null) {
                    continue;
                }
                String id = opt.getIdString();
                if (id != null && pinnedId.equals(id)) {
                    return Decision.resolved(opt, "Resolved via pinned ID");
                }
            }
        }

        final String extracted = safeTrim(extractedName);

        String extractedNormalized = null;
        if (extracted != null && !extracted.isBlank()) {
            // Normalization used ONLY for comparison; do not mutate the caller's extracted string.
            try {
                extractedNormalized = StringUtils.replacePunctuation(extracted);
            } catch (Exception ignored) {
                extractedNormalized = null;
            }
            extractedNormalized = safeTrim(extractedNormalized);
        }
        final String extractedNormalizedFinal = extractedNormalized;

        // Helper: does a candidate name match extracted (raw or normalized)?
        final java.util.function.Predicate<String> matchesExtracted =
            (String candidateName) -> {
                if (candidateName == null) {
                    return false;
                }
                if (extracted != null && !extracted.isBlank()) {
                    if (candidateName.equalsIgnoreCase(extracted)) {
                        return true;
                    }
                }
                if (
                    extractedNormalizedFinal != null &&
                    !extractedNormalizedFinal.isBlank()
                ) {
                    if (
                        candidateName.equalsIgnoreCase(extractedNormalizedFinal)
                    ) {
                        return true;
                    }
                }
                return false;
            };

        // Helper: tokenize for strict token-set comparisons.
        final java.util.function.Function<String, String> canonicalTokens =
            (String s) -> {
                if (s == null) {
                    return "";
                }
                String norm;
                try {
                    norm = StringUtils.replacePunctuation(s);
                } catch (Exception ignored) {
                    norm = s;
                }
                norm = safeTrim(norm);
                if (norm == null || norm.isBlank()) {
                    return "";
                }
                // Lowercase with Locale.ROOT (deterministic) and collapse spaces; keep token order.
                return norm.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            };

        final String extractedTokens = canonicalTokens.apply(extracted);
        final String extractedNormalizedTokens = canonicalTokens.apply(
            extractedNormalizedFinal
        );

        // 2) Exact SeriesName match (raw/normalized) wins, even if other candidates exist.
        for (ShowOption opt : options) {
            if (opt == null) {
                continue;
            }
            String name = opt.getName();
            if (matchesExtracted.test(name)) {
                return Decision.resolved(opt, "Resolves via exact name match");
            }
        }

        // 3) Exact alias match (raw/normalized) wins.
        for (ShowOption opt : options) {
            if (opt == null) {
                continue;
            }
            List<String> aliases = null;
            try {
                aliases = opt.getAliasNames();
            } catch (Exception ignored) {
                aliases = null;
            }
            if (aliases == null || aliases.isEmpty()) {
                continue;
            }
            for (String a : aliases) {
                if (matchesExtracted.test(a)) {
                    return Decision.resolved(
                        opt,
                        "Resolves via exact alias match"
                    );
                }
            }
        }

        // --- Tie-breakers (only after exact name/alias and pinned-id checks) ---

        // TB1: Prefer base title when it exists and other candidates are only parenthetical variants.
        // Example: "The Night Manager" beats "The Night Manager (IN)" and "(CN)".
        ShowOption baseTitle = null;
        int baseTitleCount = 0;
        for (ShowOption opt : options) {
            if (opt == null) {
                continue;
            }
            String name = safeTrim(opt.getName());
            if (name == null || name.isBlank()) {
                continue;
            }
            if (
                extractedNormalizedFinal != null &&
                !extractedNormalizedFinal.isBlank()
            ) {
                if (name.equalsIgnoreCase(extractedNormalizedFinal)) {
                    baseTitle = opt;
                    baseTitleCount++;
                }
            } else if (extracted != null && !extracted.isBlank()) {
                if (name.equalsIgnoreCase(extracted)) {
                    baseTitle = opt;
                    baseTitleCount++;
                }
            }
        }
        if (baseTitleCount == 1 && baseTitle != null) {
            boolean hasParentheticalVariants = false;
            String baseName = safeTrim(baseTitle.getName());
            for (ShowOption opt : options) {
                if (opt == null) {
                    continue;
                }
                String name = safeTrim(opt.getName());
                if (name == null || name.isBlank() || baseName == null) {
                    continue;
                }
                if (name.equalsIgnoreCase(baseName)) {
                    continue;
                }
                // Variant if it starts with base + " (" and ends with ")"
                if (
                    name.regionMatches(
                        true,
                        0,
                        baseName,
                        0,
                        baseName.length()
                    ) &&
                    name.length() > baseName.length() + 3 &&
                    name.charAt(baseName.length()) == ' ' &&
                    name.charAt(baseName.length() + 1) == '(' &&
                    name.endsWith(")")
                ) {
                    hasParentheticalVariants = true;
                }
            }
            if (hasParentheticalVariants) {
                return Decision.resolved(
                    baseTitle,
                    "Preferred base title over parenthetical variants"
                );
            }
        }

        // TB2/TB6: Strict token-set match: prefer candidate whose canonical tokens equal extracted tokens.
        // This favors less-decorated names when one matches exactly and others have extra tokens.
        ShowOption tokenExact = null;
        int tokenExactCount = 0;
        String tokenBasis = !extractedNormalizedTokens.isBlank()
            ? extractedNormalizedTokens
            : extractedTokens;
        if (!tokenBasis.isBlank()) {
            for (ShowOption opt : options) {
                if (opt == null) {
                    continue;
                }
                String candTokens = canonicalTokens.apply(opt.getName());
                if (!candTokens.isBlank() && candTokens.equals(tokenBasis)) {
                    tokenExact = opt;
                    tokenExactCount++;
                }
            }
            if (tokenExactCount == 1 && tokenExact != null) {
                return Decision.resolved(
                    tokenExact,
                    "Preferred exact token match over extra tokens"
                );
            }
        }

        // TB3: Year tolerance (±1) if extracted contains a year token and SeriesName wasn't an exact match.
        Integer extractedYear = parseYearFromText(extracted);
        if (extractedYear == null) {
            extractedYear = parseYearFromText(extractedNormalizedFinal);
        }
        if (extractedYear != null) {
            ShowOption yearHit = null;
            int yearHitCount = 0;
            for (ShowOption opt : options) {
                if (opt == null) {
                    continue;
                }
                Integer y = null;
                try {
                    y = opt.getFirstAiredYear();
                } catch (Exception ignored) {
                    y = null;
                }
                if (y == null) {
                    continue;
                }
                if (Math.abs(y - extractedYear) <= 1) {
                    yearHit = opt;
                    yearHitCount++;
                }
            }
            if (yearHitCount == 1 && yearHit != null) {
                return Decision.resolved(
                    yearHit,
                    "Resolved via FirstAiredYear (±1) match"
                );
            }
        }

        // 4) Single option resolves uniquely.
        if (options.size() == 1) {
            ShowOption only = options.get(0);
            if (only != null) {
                return Decision.resolved(only, "Resolves uniquely");
            }
        }

        // 5) Still ambiguous.
        return Decision.ambiguous("Still ambiguous (would prompt)");
    }

    private static Integer parseYearFromText(final String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        Matcher m = YEAR_TOKEN.matcher(s);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group("year"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeTrim(final String s) {
        if (s == null) {
            return null;
        }
        return s.trim();
    }
}
