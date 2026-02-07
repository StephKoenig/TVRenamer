package org.tvrenamer.controller;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tvrenamer.controller.util.StringUtils;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.UserPreferences;
import org.tvrenamer.model.util.Constants;

public class FilenameParser {

    private static final Logger logger = Logger.getLogger(
        FilenameParser.class.getName()
    );

    /**
     * Reasons why filename parsing may fail.
     * Used to provide actionable feedback to users.
     */
    public enum ParseFailureReason {
        /** Could not extract show name from filename. */
        NO_SHOW_NAME("Could not extract show name from filename"),
        /** No season/episode pattern found (e.g., S01E02, 1x03). */
        NO_SEASON_EPISODE("Could not find season/episode pattern (e.g., S01E02, 1x03)"),
        /** Filename too short to parse meaningfully. */
        FILENAME_TOO_SHORT("Filename too short to parse"),
        /** Filename contains no recognizable alphanumeric text. */
        NO_ALPHANUMERIC("Filename contains no recognizable text");

        private final String userMessage;

        ParseFailureReason(String userMessage) {
            this.userMessage = userMessage;
        }

        /**
         * Returns a user-friendly message describing this failure reason.
         * @return the user message
         */
        public String getUserMessage() {
            return userMessage;
        }
    }

    private static final String FILENAME_BEGINS_WITH_SEASON =
        "(([sS]\\d\\d?[eE]\\d\\d?)|([sS]?\\d\\d?[x.]?\\d\\d\\d?)).*";
    private static final String DIR_LOOKS_LIKE_SEASON = "[sS][0-3]\\d";

    // We sometimes find folders like "MyShow.Season02"; in this case, we want to
    // strip away ".Season02" and be left with just "MyShow".
    private static final String EXCESS_SEASON = "[^A-Za-z]Season[ _-]?\\d\\d?";

    // Match standard display resolutions: 480p through 4320p (3-4 digits + p)
    // and compact notations like 4k/8k (1 digit + k).  The previous broad
    // pattern (\d+[pk]) caused false positives on BBC programme IDs (e.g.
    // "p032kjnx" → "032k").
    private static final String RESOLUTION_REGEX = "\\D(\\d{3,4}p|\\d[kK]).*";

    private static final String[] REGEX = {
        // --- Multi-episode (single file) patterns ---
        //
        // Explicit list in a single file:
        //   S01E04E05
        //   S01E04E05E06
        //
        // We capture: showName (1), season (2), startEp (3), endEp (4)
        "(.+?[^a-zA-Z0-9]\\D*?)[sS](\\d\\d*)[eE](\\d\\d*)[eE](\\d\\d*)(?:[eE]\\d\\d*)*.*",
        // Inclusive ranges in a single file:
        //   S02E04-E06
        //   S02E04-06
        //
        // We capture: showName (1), season (2), startEp (3), endEp (4)
        "(.+?[^a-zA-Z0-9]\\D*?)[sS](\\d\\d*)[eE](\\d\\d*)-(?:[eE])?(\\d\\d*).*",
        // --- Single-episode patterns ---
        // this one matches SXXEXX:
        "(.+?[^a-zA-Z0-9]\\D*?)[sS](\\d\\d*)[eE](\\d\\d*).*",
        // this one matches Season-XX-Episode-XX:
        "(.+?[^a-zA-Z0-9]\\D*?)Season[- ](\\d\\d*)[- ]?Episode[- ](\\d\\d*).*",
        // BBC-style: Show_Series_X_-_YY.Title (uses "_Series_" as anchor;
        // everything before it is the show name):
        "(.+)_Series_(\\d\\d*)_-_(\\d\\d*)\\..*",
        // this one matches sXX.eXX:
        "(.+[^a-zA-Z0-9]\\D*?)[sS](\\d\\d*)\\D*?[eE](\\d\\d*).*",
        // this one matches SSxEE, with an optional leading "S"
        "(.+[^a-zA-Z0-9]\\D*?)[Ss](\\d\\d?)x(\\d\\d\\d?).*",
        // this one works for titles with years; note, this can be problematic when
        // the filename contains a year as part of the air date, rather than as part
        // of the show name or title
        "(.+?\\d{4}[^a-zA-Z0-9]\\D*?)[sS]?(\\d\\d?)\\D*?(\\d\\d).*",
        // this one matches SXXYY; note, must be exactly four digits
        "(.+?[^a-zA-Z0-9]\\D*?)[sS](\\d\\d)(\\d\\d)\\D.*",
        // this one matches everything else:
        "(.+[^a-zA-Z0-9]\\D*?)(\\d\\d?)\\D+(\\d\\d).*",
        // truly last resort:
        "(.+[^a-zA-Z0-9]+)(\\d\\d?)(\\d\\d).*",
    };

    // REGEX is a series of regular expressions for different patterns comprising
    // show name, season number, and episode number.  We also want to be able to
    // recognize episode resolution ("720p", etc.)  To make the resolution optional,
    // we compile the patterns with the resolution first, and then compile the
    // basic patterns.  So we need an array twice the size of REGEX to hold the
    // two options for each.
    private static final Pattern[] COMPILED_REGEX = new Pattern[REGEX.length *
    2];

    static {
        // Recognize the "with resolution" pattern first, since the basic patterns
        // would always permit the resolution and just see it as "junk".

        for (int i = 0; i < REGEX.length; i++) {
            // Add the resolution regex to the end of the basic pattern
            COMPILED_REGEX[i] = Pattern.compile(REGEX[i] + RESOLUTION_REGEX);
        }
        for (int i = 0; i < REGEX.length; i++) {
            // Now add the basic patterns to the end of the array.
            // That is, pattern 0 becomes compiled pattern 6, etc.
            COMPILED_REGEX[i + REGEX.length] = Pattern.compile(REGEX[i]);
        }
    }

    private FilenameParser() {
        // singleton
    }

    /**
     * Parses the filename of the given FileEpisode.<p>
     *
     * Gets the path associated with the FileEpisode, and tries to extract the
     * episode-related information from it.  Uses a hard-coded, ordered list
     * of common patterns that such filenames tend to follow.  As soon as it
     * matches one, it:<ol>
     * <li>starts the process of looking up the show name from the provider,
     *     which is done in a separate thread</li>
     * <li>updates the FileEpisode with the found information</li></ol><p>
     *
     * This method doesn't return anything, it just updates the FileEpisode.
     * A caller could check <code>episode.wasParsed()</code> after this returns,
     * to see if the episode was successfully parsed or not.
     *
     * @param episode
     *   the FileEpisode whose filename we are to try to parse
     */
    public static void parseFilename(final FileEpisode episode) {
        Path filePath = episode.getPath();
        if (filePath == null) {
            episode.setFailToParse(ParseFailureReason.NO_SHOW_NAME);
            return;
        }

        String withShowName = insertShowNameIfNeeded(filePath);

        // Early validation: filename too short
        if (withShowName == null || withShowName.length() < 4) {
            episode.setFailToParse(ParseFailureReason.FILENAME_TOO_SHORT);
            return;
        }

        String strippedName = stripJunk(withShowName);

        // Early validation: no alphanumeric content
        if (!containsAlphanumeric(strippedName)) {
            episode.setFailToParse(ParseFailureReason.NO_ALPHANUMERIC);
            return;
        }

        Matcher matcher;
        for (Pattern patt : COMPILED_REGEX) {
            matcher = patt.matcher(strippedName);
            if (matcher.matches()) {
                String foundName = StringUtils.trimFoundShow(matcher.group(1));
                String overriddenName =
                    UserPreferences.getInstance().resolveShowName(foundName);
                ShowName.mapShowName(overriddenName);

                // Preserve the extracted show name for UI/display/debugging, but apply overrides
                // to the effective lookup name used to query the provider.
                episode.setExtractedFilenameShow(foundName);
                episode.setFilenameShow(overriddenName);

                // Multi-episode patterns include an extra capture group for "end episode"
                // (A..B in a single file). When present:
                // - store span on the episode (for later "(A-B)" suffix)
                // - select the lowest episode (A) for placement/lookup
                //
                // IMPORTANT: Many existing single-episode patterns also have 4 capture groups
                // (the 4th being resolution). We must therefore only treat a match as
                // "multi-episode" if we can parse (season, startEp, endEp) and endEp looks valid.
                if (matcher.groupCount() >= 4) {
                    String seasonStr = matcher.group(2);
                    String startEpStr = matcher.group(3);
                    String endEpStr = matcher.group(4);

                    int startEp;
                    int endEp;
                    try {
                        startEp = Integer.parseInt(startEpStr);
                        endEp = Integer.parseInt(endEpStr);
                    } catch (NumberFormatException ignored) {
                        // Not a multi-episode match; fall through to normal handling.
                        startEp = -1;
                        endEp = -1;
                    }

                    // Only treat as multi-episode if B is >= A; otherwise this is likely
                    // a single-episode pattern where group(4) is actually resolution.
                    if (startEp >= 0 && endEp >= startEp) {
                        episode.setMultiEpisodeSpan(startEp, endEp);

                        // Use the lowest episode for placement/matching.
                        episode.setEpisodePlacement(seasonStr, startEpStr);

                        // Multi-episode patterns currently don't capture resolution; keep it empty.
                        episode.setFilenameResolution("");
                        episode.setParsed();
                        return;
                    }
                }

                // Single-episode patterns: group 4 (if present) is resolution.
                String resolution = "";
                if (matcher.groupCount() == 4) {
                    resolution = matcher.group(4);
                } else if (matcher.groupCount() != 3) {
                    // This should never happen and so we should probably consider it
                    // an error if it does, but not important.
                    continue;
                }

                episode.setEpisodePlacement(matcher.group(2), matcher.group(3));
                episode.setFilenameResolution(resolution);
                episode.setParsed();

                return;
            }
        }

        // Diagnose why parsing failed
        ParseFailureReason reason = diagnoseFailure(strippedName);
        episode.setFailToParse(reason);
    }

    /**
     * Diagnose why parsing failed by checking for common patterns.
     */
    private static ParseFailureReason diagnoseFailure(String strippedName) {
        // Check if we have any season/episode pattern at all
        if (!containsEpisodePattern(strippedName)) {
            return ParseFailureReason.NO_SEASON_EPISODE;
        }
        // Has episode pattern but couldn't extract show name
        return ParseFailureReason.NO_SHOW_NAME;
    }

    // Basic pattern to detect if ANY episode numbering exists (even if we can't fully parse it)
    private static final Pattern BASIC_EP_PATTERN = Pattern.compile(
        ".*([sS]\\d+[eE]\\d+|\\d+[xX]\\d+|\\d{1,2}\\d{2}).*"
    );

    private static boolean containsEpisodePattern(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return BASIC_EP_PATTERN.matcher(s).matches();
    }

    private static boolean containsAlphanumeric(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String stripJunk(String input) {
        String output = input;
        output = StringUtils.removeLast(output, "hdtv");
        output = StringUtils.removeLast(output, "dvdrip");
        return output;
    }

    private static String extractParentName(Path parent) {
        if (parent == null) {
            return Constants.EMPTY_STRING;
        }

        Path parentPathname = parent.getFileName();
        if (parentPathname == null) {
            return Constants.EMPTY_STRING;
        }

        String parentName = parentPathname.toString();
        return parentName.replaceFirst(EXCESS_SEASON, "");
    }

    private static String insertShowNameIfNeeded(final Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException(
                "insertShowNameIfNeeded received null argument."
            );
        }

        final Path justNamePath = filePath.getFileName();
        if (justNamePath == null) {
            throw new IllegalArgumentException(
                "insertShowNameIfNeeded received path with no name."
            );
        }

        final String pName = justNamePath.toString();
        logger.fine("pName = " + pName);
        if (pName.matches(FILENAME_BEGINS_WITH_SEASON)) {
            Path parent = filePath.getParent();
            String parentName = extractParentName(parent);
            while (
                StringUtils.toLower(parentName).startsWith("season") ||
                parentName.matches(DIR_LOOKS_LIKE_SEASON) ||
                parentName.equals(Constants.DUPLICATES_DIRECTORY)
            ) {
                parent = parent.getParent();
                parentName = extractParentName(parent);
            }
            logger.fine(
                "appending parent directory '" +
                    parentName +
                    "' to filename '" +
                    pName +
                    "'"
            );
            return parentName + " " + pName;
        }
        return pName;
    }

    /**
     * Extract just the season and episode numbers from a filename.
     *
     * This is a lightweight extraction used for conflict detection, without
     * triggering show lookups or creating FileEpisode objects.
     *
     * @param filename the filename to parse (just the name, not full path)
     * @return an int array [season, episode], or null if not parsed
     */
    public static int[] extractSeasonEpisode(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        for (Pattern pattern : COMPILED_REGEX) {
            Matcher matcher = pattern.matcher(filename);
            if (matcher.matches()) {
                int groupCount = matcher.groupCount();
                // Need at least 3 groups: show (1), season (2), episode (3)
                if (groupCount >= 3) {
                    try {
                        int season = Integer.parseInt(matcher.group(2));
                        int episode = Integer.parseInt(matcher.group(3));
                        return new int[] { season, episode };
                    } catch (NumberFormatException ignored) {
                        // Try next pattern
                    }
                }
            }
        }
        return null;
    }
}
