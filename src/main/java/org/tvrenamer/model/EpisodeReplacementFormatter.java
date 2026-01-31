package org.tvrenamer.model;

import static org.tvrenamer.model.ReplacementToken.*;
import static org.tvrenamer.model.util.Constants.*;

import org.tvrenamer.controller.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles replacement token substitution for episode renaming.
 *
 * <p>This class is responsible for taking a user-provided template string
 * (e.g., "%S S%0sE%0e %t") and replacing the tokens with actual episode
 * information to produce the final filename.
 *
 * <p>All methods are static and pure (no side effects), making this class
 * safe to use from any context.
 */
public final class EpisodeReplacementFormatter {

    private static final Logger logger = Logger.getLogger(
        EpisodeReplacementFormatter.class.getName()
    );

    // Cached DateTimeFormatters (thread-safe and expensive to create)
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("d");
    private static final DateTimeFormatter DAY_LZ_FORMAT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("M");
    private static final DateTimeFormatter MONTH_LZ_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR_FULL_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YEAR_SHORT_FORMAT = DateTimeFormatter.ofPattern("yy");

    private EpisodeReplacementFormatter() {
        // Utility class - prevent instantiation
    }

    /**
     * Replace the control strings in the replacement template with episode information.
     *
     * @param replacementTemplate the template provided by the user via preferences
     * @param show the TV show that matches this file
     * @param episode the episode that matches this file
     * @param placement the season/episode numbers from the filename
     * @param resolution the screen resolution (e.g., "720p") from the filename
     * @param episodeTitleOverride optional override for episode title (for multi-episode files)
     * @return the template string with episode information substituted
     */
    public static String format(
            final String replacementTemplate,
            final Show show,
            final Episode episode,
            final EpisodePlacement placement,
            final String resolution,
            final String episodeTitleOverride) {

        final String showName = show.getName();

        String episodeTitle = (episodeTitleOverride != null)
            ? episodeTitleOverride
            : episode.getTitle();

        if (episodeTitle.length() > MAX_TITLE_LENGTH) {
            logger.fine("truncating episode title: " + episodeTitle);
            episodeTitle = episodeTitle.substring(0, MAX_TITLE_LENGTH);
        }

        // Use replace() instead of replaceAll() - tokens are literals, not regex.
        // This avoids regex overhead and the need for Matcher.quoteReplacement().
        String result = replacementTemplate
            .replace(SEASON_NUM.getToken(), String.valueOf(placement.season))
            .replace(SEASON_NUM_LEADING_ZERO.getToken(), StringUtils.zeroPadTwoDigits(placement.season))
            .replace(EPISODE_NUM.getToken(), StringUtils.formatDigits(placement.episode))
            .replace(EPISODE_NUM_LEADING_ZERO.getToken(), StringUtils.zeroPadThreeDigits(placement.episode))
            .replace(SHOW_NAME.getToken(), showName)
            .replace(EPISODE_TITLE.getToken(), episodeTitle)
            .replace(EPISODE_TITLE_NO_SPACES.getToken(), StringUtils.makeDotTitle(episodeTitle))
            .replace(EPISODE_RESOLUTION.getToken(), resolution);

        // Handle air date tokens
        final LocalDate airDate = episode.getAirDate();
        if (airDate == null) {
            logger.log(Level.WARNING,
                "Episode air date not found for {0}, {1}, \"{2}\"",
                new Object[]{showName, placement, episodeTitle});
        }

        result = substituteAirDate(airDate, result);

        return StringUtils.sanitiseTitle(result);
    }

    /**
     * Replace date tokens in the template with air date information.
     *
     * @param airDate the episode air date (may be null)
     * @param template the template string (may be partially filled in)
     * @return the template with date tokens replaced
     */
    static String substituteAirDate(final LocalDate airDate, final String template) {
        if (airDate == null) {
            return removeTokens(template,
                DATE_DAY_NUM, DATE_DAY_NUMLZ,
                DATE_MONTH_NUM, DATE_MONTH_NUMLZ,
                DATE_YEAR_FULL, DATE_YEAR_MIN);
        }

        return template
            .replace(DATE_DAY_NUM.getToken(), DAY_FORMAT.format(airDate))
            .replace(DATE_DAY_NUMLZ.getToken(), DAY_LZ_FORMAT.format(airDate))
            .replace(DATE_MONTH_NUM.getToken(), MONTH_FORMAT.format(airDate))
            .replace(DATE_MONTH_NUMLZ.getToken(), MONTH_LZ_FORMAT.format(airDate))
            .replace(DATE_YEAR_FULL.getToken(), YEAR_FULL_FORMAT.format(airDate))
            .replace(DATE_YEAR_MIN.getToken(), YEAR_SHORT_FORMAT.format(airDate));
    }

    /**
     * Remove the specified tokens from the string (used when data is unavailable).
     */
    private static String removeTokens(final String orig, final ReplacementToken... tokens) {
        String result = orig;
        for (ReplacementToken token : tokens) {
            result = result.replace(token.getToken(), "");
        }
        return result;
    }
}
