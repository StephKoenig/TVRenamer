package org.tvrenamer.controller.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tvrenamer.model.Episode;
import org.tvrenamer.model.EpisodePlacement;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.Show;

/**
 * Tags MKV files with TV episode metadata using mkvpropedit (MKVToolNix).
 *
 * This tagger requires mkvpropedit to be installed on the system.
 * If mkvpropedit is not found, MKV files are silently skipped.
 *
 * Writes Matroska tags at multiple target levels for broad compatibility:
 * - Target 70 (Collection): Show name, content type
 * - Target 60 (Season): Season number
 * - Target 50 (Episode): Episode title, number, air date
 */
public class MkvMetadataTagger implements VideoMetadataTagger {

    private static final Logger logger = Logger.getLogger(MkvMetadataTagger.class.getName());

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".mkv", ".webm");

    // Cached mkvpropedit path (null = not checked yet, empty = not found)
    private static volatile String mkvpropeditPath = null;
    private static final Object DETECTION_LOCK = new Object();

    // Process timeout in seconds
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    @Override
    public boolean supportsExtension(String extension) {
        if (extension == null) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean tagFile(Path videoFile, FileEpisode episode) {
        // Check if mkvpropedit is available
        String mkvpropedit = getMkvpropeditPath();
        if (mkvpropedit == null || mkvpropedit.isEmpty()) {
            logger.fine("mkvpropedit not found - skipping MKV tagging for " + videoFile);
            return true; // Not an error, just skip
        }

        // Validate inputs
        Show show = episode.getActualShow();
        Episode ep = episode.getActualEpisode();

        if (show == null) {
            logger.warning("Cannot tag: missing show data for " + videoFile);
            return false;
        }

        if (ep == null) {
            logger.warning("Cannot tag: missing episode data for " + videoFile);
            return false;
        }

        EpisodePlacement placement = episode.getEpisodePlacement();
        if (placement == null) {
            logger.warning("Cannot tag: missing episode placement for " + videoFile);
            return false;
        }

        // Extract metadata
        String showName = show.getName();
        int season = placement.season;
        int episodeNum = placement.episode;
        String episodeTitle = ep.getTitle();
        LocalDate airDate = ep.getAirDate();

        // Get filename without extension for segment title (display name)
        String filename = videoFile.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String filenameNoExt = (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;

        logger.fine("Tagging MKV " + videoFile.getFileName() + " with: " +
            "show=" + showName + ", S" + season + "E" + episodeNum +
            ", title=" + episodeTitle + ", segmentTitle=" + filenameNoExt);

        Path tagsFile = null;
        try {
            // Generate XML tags file
            tagsFile = generateTagsXml(showName, season, episodeNum, episodeTitle, airDate);

            // Run mkvpropedit
            boolean success = runMkvpropedit(mkvpropedit, videoFile, tagsFile, filenameNoExt);

            if (success) {
                logger.fine("Successfully tagged MKV: " + videoFile.getFileName());
            }
            return success;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to tag MKV: " + videoFile, e);
            return false;
        } finally {
            // Clean up temp file
            if (tagsFile != null) {
                try {
                    Files.deleteIfExists(tagsFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
        }
    }

    /**
     * Get the path to mkvpropedit, detecting it if necessary.
     * Result is cached for performance.
     *
     * @return path to mkvpropedit executable, or empty string if not found
     */
    private static String getMkvpropeditPath() {
        if (mkvpropeditPath != null) {
            return mkvpropeditPath;
        }

        synchronized (DETECTION_LOCK) {
            // Double-check after acquiring lock
            if (mkvpropeditPath != null) {
                return mkvpropeditPath;
            }

            mkvpropeditPath = detectMkvpropedit();
            if (mkvpropeditPath.isEmpty()) {
                logger.info("mkvpropedit not found - MKV tagging will be disabled");
            } else {
                logger.info("Found mkvpropedit: " + mkvpropeditPath);
            }
            return mkvpropeditPath;
        }
    }

    /**
     * Detect mkvpropedit installation.
     *
     * @return path to mkvpropedit, or empty string if not found
     */
    private static String detectMkvpropedit() {
        // Try PATH first
        if (isExecutableInPath("mkvpropedit")) {
            return "mkvpropedit";
        }

        // On Windows, check common installation paths
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String[] windowsPaths = {
                "C:\\Program Files\\MKVToolNix\\mkvpropedit.exe",
                "C:\\Program Files (x86)\\MKVToolNix\\mkvpropedit.exe"
            };
            for (String path : windowsPaths) {
                if (Files.isExecutable(Paths.get(path))) {
                    return path;
                }
            }
        }

        // On macOS, check Homebrew
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            String homebrewPath = "/usr/local/bin/mkvpropedit";
            if (Files.isExecutable(Paths.get(homebrewPath))) {
                return homebrewPath;
            }
            // Apple Silicon Homebrew location
            String armHomebrewPath = "/opt/homebrew/bin/mkvpropedit";
            if (Files.isExecutable(Paths.get(armHomebrewPath))) {
                return armHomebrewPath;
            }
        }

        return "";
    }

    /**
     * Check if an executable is available in PATH by trying to run it.
     */
    private static boolean isExecutableInPath(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
            process.destroyForcibly();
        } catch (IOException | InterruptedException e) {
            // Not found or not executable
        }
        return false;
    }

    /**
     * Generate Matroska tags XML file.
     */
    private Path generateTagsXml(String showName, int season, int episodeNum,
                                  String episodeTitle, LocalDate airDate) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Tags>\n");

        // Series/Collection level (Target 70)
        xml.append("  <Tag>\n");
        xml.append("    <Targets>\n");
        xml.append("      <TargetTypeValue>70</TargetTypeValue>\n");
        xml.append("      <TargetType>COLLECTION</TargetType>\n");
        xml.append("    </Targets>\n");
        xml.append("    <Simple>\n");
        xml.append("      <Name>TITLE</Name>\n");
        xml.append("      <String>").append(escapeXml(showName)).append("</String>\n");
        xml.append("    </Simple>\n");
        xml.append("    <Simple>\n");
        xml.append("      <Name>COLLECTION</Name>\n");
        xml.append("      <String>").append(escapeXml(showName)).append("</String>\n");
        xml.append("    </Simple>\n");
        xml.append("    <Simple>\n");
        xml.append("      <Name>CONTENT_TYPE</Name>\n");
        xml.append("      <String>TV Show</String>\n");
        xml.append("    </Simple>\n");
        xml.append("  </Tag>\n");

        // Season level (Target 60)
        xml.append("  <Tag>\n");
        xml.append("    <Targets>\n");
        xml.append("      <TargetTypeValue>60</TargetTypeValue>\n");
        xml.append("      <TargetType>SEASON</TargetType>\n");
        xml.append("    </Targets>\n");
        xml.append("    <Simple>\n");
        xml.append("      <Name>PART_NUMBER</Name>\n");
        xml.append("      <String>").append(season).append("</String>\n");
        xml.append("    </Simple>\n");
        xml.append("    <Simple>\n");
        xml.append("      <Name>TITLE</Name>\n");
        xml.append("      <String>Season ").append(season).append("</String>\n");
        xml.append("    </Simple>\n");
        xml.append("  </Tag>\n");

        // Episode level (Target 50)
        xml.append("  <Tag>\n");
        xml.append("    <Targets>\n");
        xml.append("      <TargetTypeValue>50</TargetTypeValue>\n");
        xml.append("      <TargetType>EPISODE</TargetType>\n");
        xml.append("    </Targets>\n");
        if (episodeTitle != null && !episodeTitle.isBlank()) {
            xml.append("    <Simple>\n");
            xml.append("      <Name>TITLE</Name>\n");
            xml.append("      <String>").append(escapeXml(episodeTitle)).append("</String>\n");
            xml.append("    </Simple>\n");
        }
        xml.append("    <Simple>\n");
        xml.append("      <Name>PART_NUMBER</Name>\n");
        xml.append("      <String>").append(episodeNum).append("</String>\n");
        xml.append("    </Simple>\n");
        if (airDate != null) {
            String dateStr = airDate.toString(); // ISO-8601
            xml.append("    <Simple>\n");
            xml.append("      <Name>DATE_RELEASED</Name>\n");
            xml.append("      <String>").append(dateStr).append("</String>\n");
            xml.append("    </Simple>\n");
            xml.append("    <Simple>\n");
            xml.append("      <Name>DATE_RECORDED</Name>\n");
            xml.append("      <String>").append(dateStr).append("</String>\n");
            xml.append("    </Simple>\n");
        }
        xml.append("  </Tag>\n");

        xml.append("</Tags>\n");

        // Write to temp file
        Path tagsFile = Files.createTempFile("tvr-mkv-tags-", ".xml");
        Files.writeString(tagsFile, xml.toString(), StandardCharsets.UTF_8);
        return tagsFile;
    }

    /**
     * Run mkvpropedit to apply tags and segment title.
     */
    private boolean runMkvpropedit(String mkvpropedit, Path videoFile, Path tagsFile,
                                    String segmentTitle) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                mkvpropedit,
                videoFile.toString(),
                "--tags", "global:" + tagsFile.toString(),
                "--edit", "info",
                "--set", "title=" + segmentTitle
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output (for logging on failure)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warning("mkvpropedit timed out for: " + videoFile);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warning("mkvpropedit failed (exit " + exitCode + ") for: " + videoFile +
                    "\nOutput: " + output);
                return false;
            }

            return true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to run mkvpropedit for: " + videoFile, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("mkvpropedit interrupted for: " + videoFile);
            return false;
        }
    }

    /**
     * Escape special XML characters.
     */
    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Check if mkvpropedit is available on this system.
     * Useful for UI to show/hide MKV tagging options.
     *
     * @return true if mkvpropedit is installed and accessible
     */
    public static boolean isMkvpropeditAvailable() {
        String path = getMkvpropeditPath();
        return path != null && !path.isEmpty();
    }
}
