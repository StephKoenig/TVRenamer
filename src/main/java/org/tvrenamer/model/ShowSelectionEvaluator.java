package org.tvrenamer.model;

import java.util.List;
import java.util.Objects;
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

        // 2) Exact SeriesName match (raw/normalized).
        for (ShowOption opt : options) {
            if (opt == null) {
                continue;
            }
            String name = opt.getName();
            if (matchesExtracted.test(name)) {
                // Prefer the base title match immediately.
                return Decision.resolved(opt, "Resolves via exact name match");
            }
        }

        // 3) Exact alias match (raw/normalized).
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

    private static String safeTrim(final String s) {
        if (s == null) {
            return null;
        }
        return s.trim();
    }
}
