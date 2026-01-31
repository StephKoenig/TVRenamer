package org.tvrenamer.controller.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.tvrenamer.controller.util.StringUtils.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for real-world tricky titles that exercise edge cases in filename sanitization.
 *
 * <p>These titles are drawn from actual movies and TV shows with problematic characters:
 * colons, slashes, question marks, quotes, asterisks, apostrophes, etc.
 *
 * <p>See also: docs/Strings Spec.md for the complete encoding specification.
 */
public class TrickyTitlesTest {

    // -------------------------------------------------------------------------
    // sanitiseTitle() tests - for filename output
    // -------------------------------------------------------------------------

    @Test
    public void testSanitiseTitle_MissionImpossible() {
        // Colon is illegal in Windows filenames; replaced with hyphen
        assertEquals(
            "Mission- Impossible - Fallout (2018)",
            sanitiseTitle("Mission: Impossible - Fallout (2018)")
        );
    }

    @Test
    public void testSanitiseTitle_VHS() {
        // Slashes are illegal; replaced with hyphens
        assertEquals("V-H-S", sanitiseTitle("V/H/S"));
    }

    @Test
    public void testSanitiseTitle_QuestionMark() {
        // Question marks are removed entirely
        // Note: whitespace trimming happens BEFORE character replacement,
        // so "? (2021)" -> " (2021)" (space remains because ? was not whitespace)
        assertEquals(" (2021)", sanitiseTitle("? (2021)"));
        assertEquals("Why", sanitiseTitle("Why?"));
    }

    @Test
    public void testSanitiseTitle_DoubleQuotes() {
        // Double quotes are replaced with apostrophes
        assertEquals("'What' (2013)", sanitiseTitle("\"What\" (2013)"));
    }

    @Test
    public void testSanitiseTitle_SWAT() {
        // Dots are legal in filenames; unchanged
        assertEquals("S.W.A.T. (2017)", sanitiseTitle("S.W.A.T. (2017)"));
    }

    @Test
    public void testSanitiseTitle_BatteriesNotIncluded() {
        // Asterisks are replaced with hyphens
        assertEquals("-batteries not included", sanitiseTitle("*batteries not included"));
    }

    @Test
    public void testSanitiseTitle_Woodstock99() {
        // Single quotes/apostrophes are legal; unchanged
        assertEquals("Woodstock '99", sanitiseTitle("Woodstock '99"));
    }

    @Test
    public void testSanitiseTitle_Ampersand() {
        // Ampersands are legal in filenames; unchanged
        assertEquals("Fish & Chips", sanitiseTitle("Fish & Chips"));
    }

    @Test
    public void testSanitiseTitle_MultipleAsterisks() {
        // Multiple asterisks should each be replaced (preserves word shape)
        assertEquals("C--tgate", sanitiseTitle("C**tgate"));
    }

    @Test
    public void testSanitiseTitle_AllIllegalChars() {
        // Combined test: all illegal chars in one string
        // Input: Test\/:*?"<>|End
        // Expected: Test----''End (backslash, slash, colon, asterisk -> hyphen;
        //           question mark, less-than, greater-than -> removed;
        //           double-quote, pipe -> apostrophe, hyphen)
        assertEquals("Test----'-End", sanitiseTitle("Test\\/:*?\"<>|End"));
    }

    // -------------------------------------------------------------------------
    // replaceIllegalCharacters() tests - same transformations, no trim
    // -------------------------------------------------------------------------

    @Test
    public void testReplaceIllegalCharacters_PreservesWhitespace() {
        // Unlike sanitiseTitle, replaceIllegalCharacters does NOT trim whitespace
        assertEquals(
            "  Mission- Impossible  ",
            replaceIllegalCharacters("  Mission: Impossible  ")
        );
    }

    // -------------------------------------------------------------------------
    // makeQueryString() tests - for provider search queries
    // -------------------------------------------------------------------------

    @Test
    public void testMakeQueryString_MissionImpossible() {
        // Colons become spaces, then normalized
        assertEquals(
            "mission impossible fallout (2018)",
            makeQueryString("Mission: Impossible - Fallout (2018)")
        );
    }

    @Test
    public void testMakeQueryString_VHS() {
        // Slashes become spaces
        assertEquals("v h s", makeQueryString("V/H/S"));
    }

    @Test
    public void testMakeQueryString_SWAT() {
        // Dots in acronyms are condensed by the acronym-detection logic
        assertEquals("swat (2017)", makeQueryString("S.W.A.T. (2017)"));
    }

    @Test
    public void testMakeQueryString_Woodstock99() {
        // Apostrophes are removed entirely for query normalization
        assertEquals("woodstock 99", makeQueryString("Woodstock '99"));
    }

    @Test
    public void testMakeQueryString_BatteriesNotIncluded() {
        // Asterisks become spaces
        assertEquals("batteries not included", makeQueryString("*batteries not included"));
    }

    // -------------------------------------------------------------------------
    // Unicode and special character tests
    // -------------------------------------------------------------------------

    @Test
    public void testSanitiseTitle_NonAscii() {
        // Non-ASCII characters (accented letters) should pass through unchanged
        // (they are legal in Windows filenames)
        assertEquals("Café Society", sanitiseTitle("Café Society"));
        assertEquals("Amélie", sanitiseTitle("Amélie"));
        assertEquals("El Niño", sanitiseTitle("El Niño"));
    }

    @Test
    public void testSanitiseTitle_EmDash() {
        // Em-dash (—) is NOT an illegal character; should pass through
        assertEquals("The Movie — Extended Cut", sanitiseTitle("The Movie — Extended Cut"));
    }

    @Test
    public void testSanitiseTitle_CurlyQuotes() {
        // Curly quotes (\u201C, \u201D) are NOT the same as ASCII double quote (")
        // They are legal in filenames and should pass through unchanged
        assertEquals("\u201CTest\u201D", sanitiseTitle("\u201CTest\u201D"));
    }

    @Test
    public void testSanitiseTitle_Ellipsis() {
        // Unicode ellipsis (…) is legal; should pass through
        assertEquals("To Be Continued…", sanitiseTitle("To Be Continued…"));
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    public void testSanitiseTitle_OnlyIllegalChars() {
        // A title consisting only of illegal characters
        assertEquals("", sanitiseTitle("?<>"));
        assertEquals("---", sanitiseTitle("\\/:"));
    }

    @Test
    public void testSanitiseTitle_LeadingTrailingIllegal() {
        // Illegal chars at start/end with whitespace
        assertEquals("Title", sanitiseTitle("  ?Title?  "));
    }

    @Test
    public void testMakeQueryString_PreservesParenthesesAndAmpersand() {
        // Parentheses and ampersand are preserved in query strings
        assertEquals("law & order (uk)", makeQueryString("Law & Order (UK)"));
    }

    @Test
    public void testIsLegalFilenameCharacter() {
        // Verify the character classification
        assertFalse(isLegalFilenameCharacter('\\'));
        assertFalse(isLegalFilenameCharacter('/'));
        assertFalse(isLegalFilenameCharacter(':'));
        assertFalse(isLegalFilenameCharacter('*'));
        assertFalse(isLegalFilenameCharacter('?'));
        assertFalse(isLegalFilenameCharacter('"'));
        assertFalse(isLegalFilenameCharacter('<'));
        assertFalse(isLegalFilenameCharacter('>'));
        assertFalse(isLegalFilenameCharacter('|'));
        assertFalse(isLegalFilenameCharacter('`'));

        // These ARE legal
        assertEquals(true, isLegalFilenameCharacter('&'));
        assertEquals(true, isLegalFilenameCharacter('.'));
        assertEquals(true, isLegalFilenameCharacter('\''));
        assertEquals(true, isLegalFilenameCharacter('-'));
        assertEquals(true, isLegalFilenameCharacter('('));
        assertEquals(true, isLegalFilenameCharacter(')'));
    }
}
