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
        private final List<ScoredOption> scoredOptions;

        private Decision(
            final OutcomeType type,
            final ShowOption chosen,
            final String message,
            final List<ScoredOption> scoredOptions
        ) {
            this.type = Objects.requireNonNull(type, "type");
            this.chosen = chosen;
            this.message = (message == null) ? "" : message;
            this.scoredOptions = scoredOptions;
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

        /**
         * @return sorted list of options with similarity scores (best first), or null if not computed.
         *         Useful for disambiguation dialogs to show ranked options.
         */
        public List<ScoredOption> getScoredOptions() {
            return scoredOptions;
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
            return new Decision(OutcomeType.RESOLVED, chosen, msg, null);
        }

        static Decision ambiguous(final String msg, final List<ScoredOption> scoredOptions) {
            return new Decision(OutcomeType.AMBIGUOUS, null, msg, scoredOptions);
        }

        static Decision notFound(final String msg) {
            return new Decision(OutcomeType.NOT_FOUND, null, msg, null);
        }
    }

    private static final Pattern YEAR_TOKEN = Pattern.compile(
        "(^|\\s|\\()(?<year>19\\d{2}|20\\d{2})(\\s|\\)|$)"
    );

    // Fuzzy matching thresholds
    private static final double FUZZY_AUTO_SELECT_MIN_SCORE = 0.80;
    private static final double FUZZY_AUTO_SELECT_MIN_GAP = 0.10;
    private static final double FUZZY_RECOMMENDED_MIN_SCORE = 0.70;

    /**
     * Holds a ShowOption together with its similarity score for ranking.
     */
    public static final class ScoredOption implements Comparable<ScoredOption> {
        private final ShowOption option;
        private final double score;

        public ScoredOption(ShowOption option, double score) {
            this.option = option;
            this.score = score;
        }

        public ShowOption getOption() {
            return option;
        }

        public double getScore() {
            return score;
        }

        /**
         * @return true if this option's score is high enough to be marked as recommended
         */
        public boolean isRecommended() {
            return score >= FUZZY_RECOMMENDED_MIN_SCORE;
        }

        @Override
        public int compareTo(ScoredOption other) {
            // Higher scores first
            return Double.compare(other.score, this.score);
        }
    }

    /**
     * Calculate Levenshtein (edit) distance between two strings.
     * This is the minimum number of single-character edits (insertions, deletions, substitutions)
     * required to change one string into the other.
     */
    private static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return (s1 == null && s2 == null) ? 0 : Integer.MAX_VALUE;
        }
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // Use two rows instead of full matrix for space efficiency
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[len2];
    }

    /**
     * Calculate normalized similarity score (0.0 to 1.0) between two strings.
     * Uses Levenshtein distance normalized by max length.
     *
     * @return 1.0 for identical strings, 0.0 for completely different strings
     */
    private static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        String a = s1.toLowerCase(Locale.ROOT);
        String b = s2.toLowerCase(Locale.ROOT);
        if (a.equals(b)) {
            return 1.0;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLen);
    }

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
                // Broad catch intentional: utility method may throw various exceptions; graceful fallback.
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
                    // Broad catch intentional: utility method may throw various exceptions; graceful fallback.
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
                // Broad catch intentional: defensive for external data; graceful fallback.
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
                    // Broad catch intentional: defensive for external data; graceful fallback.
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

        // TB7: Fuzzy string matching
        // Score all candidates by similarity to extracted name, sorted best-first.
        // Auto-select if best score >= threshold AND gap to second-best >= min gap.
        final String compareText = (extracted != null && !extracted.isBlank())
            ? extracted
            : extractedNormalizedFinal;

        List<ScoredOption> scored = new java.util.ArrayList<>();
        for (ShowOption opt : options) {
            if (opt == null) {
                continue;
            }
            String name = opt.getName();
            double score = similarity(compareText, name);

            // Also check aliases and use best score
            List<String> aliases = null;
            try {
                aliases = opt.getAliasNames();
            } catch (Exception ignored) {
                aliases = null;
            }
            if (aliases != null) {
                for (String alias : aliases) {
                    double aliasScore = similarity(compareText, alias);
                    if (aliasScore > score) {
                        score = aliasScore;
                    }
                }
            }
            scored.add(new ScoredOption(opt, score));
        }
        java.util.Collections.sort(scored);

        if (scored.size() >= 1) {
            double bestScore = scored.get(0).getScore();
            double secondScore = scored.size() > 1 ? scored.get(1).getScore() : 0.0;

            if (bestScore >= FUZZY_AUTO_SELECT_MIN_SCORE
                    && (bestScore - secondScore) >= FUZZY_AUTO_SELECT_MIN_GAP) {
                return Decision.resolved(
                    scored.get(0).getOption(),
                    String.format("Fuzzy match: %.0f%% (gap: %.0f%%)",
                        bestScore * 100, (bestScore - secondScore) * 100)
                );
            }
        }

        // 5) Still ambiguous - return with scored options for UI ranking.
        return Decision.ambiguous("Still ambiguous (would prompt)", scored);
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
        } catch (NumberFormatException ignored) {
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
