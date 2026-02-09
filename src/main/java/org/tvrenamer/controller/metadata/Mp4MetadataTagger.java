package org.tvrenamer.controller.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * Tags MP4 files with TV episode metadata using an external tool.
 *
 * <p>Prefers AtomicParsley (surgical iTunes atom edits) with ffmpeg as a
 * fallback (full container rewrite with {@code -c copy}).  If neither tool
 * is found, MP4 files are silently skipped.
 *
 * <p>Writes iTunes-style TV atoms for broad media manager compatibility:
 * tvsh, ©alb, tvsn, tves, tven, ©nam, ©day, stik.
 */
public class Mp4MetadataTagger implements VideoMetadataTagger {

    private static final Logger logger = Logger.getLogger(Mp4MetadataTagger.class.getName());

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".mp4", ".m4v", ".mov"
    );

    /** Which external tool was detected. */
    private enum Tool { ATOMIC_PARSLEY, FFMPEG, NONE }

    // Cached detection result
    private static volatile String toolPath = null;
    private static volatile Tool detectedTool = null;
    private static final Object DETECTION_LOCK = new Object();

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
        // Ensure tool detection has run
        ensureDetected();

        if (detectedTool == Tool.NONE) {
            logger.fine("No MP4 tagging tool found - skipping " + videoFile);
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

        // Filename without extension for the title/display name
        String filename = videoFile.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String filenameNoExt = (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;

        String airDateStr = (airDate != null) ? airDate.toString() : null;

        logger.fine("Tagging MP4 " + filename + " with: " +
            "show=" + showName + ", S" + season + "E" + episodeNum +
            ", title=" + episodeTitle + ", tool=" + detectedTool);

        if (detectedTool == Tool.ATOMIC_PARSLEY) {
            return tagWithAtomicParsley(videoFile, showName, season, episodeNum,
                episodeTitle, filenameNoExt, airDateStr);
        } else {
            return tagWithFfmpeg(videoFile, showName, season, episodeNum,
                episodeTitle, filenameNoExt, airDateStr);
        }
    }

    // ---- AtomicParsley ----

    private boolean tagWithAtomicParsley(Path videoFile, String showName,
            int season, int episodeNum, String episodeTitle,
            String filenameNoExt, String airDateStr) {
        List<String> cmd = new ArrayList<>();
        cmd.add(toolPath);
        cmd.add(videoFile.toString());
        cmd.add("--overWrite");

        cmd.add("--TVShowName");
        cmd.add(showName);
        cmd.add("--TVSeasonNum");
        cmd.add(String.valueOf(season));
        cmd.add("--TVEpisodeNum");
        cmd.add(String.valueOf(episodeNum));

        if (episodeTitle != null && !episodeTitle.isBlank()) {
            cmd.add("--TVEpisode");
            cmd.add(episodeTitle);
        }

        cmd.add("--title");
        cmd.add(filenameNoExt);
        cmd.add("--album");
        cmd.add(showName);

        if (airDateStr != null) {
            cmd.add("--year");
            cmd.add(airDateStr);
        }

        cmd.add("--stik");
        cmd.add("TV Show");

        return runProcess(cmd, videoFile);
    }

    // ---- ffmpeg fallback ----

    private boolean tagWithFfmpeg(Path videoFile, String showName,
            int season, int episodeNum, String episodeTitle,
            String filenameNoExt, String airDateStr) {

        // ffmpeg cannot edit in place; write to temp then replace.
        Path tempFile = null;
        try {
            String ext = "";
            String name = videoFile.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                ext = name.substring(dot);
            }
            tempFile = Files.createTempFile(videoFile.getParent(), ".tvr-tag-", ext);

            List<String> cmd = new ArrayList<>();
            cmd.add(toolPath);
            cmd.add("-y");            // overwrite temp file
            cmd.add("-i");
            cmd.add(videoFile.toString());
            cmd.add("-c");
            cmd.add("copy");          // no re-encoding

            addFfmpegMeta(cmd, "show", showName);
            addFfmpegMeta(cmd, "season_number", String.valueOf(season));
            addFfmpegMeta(cmd, "episode_sort", String.valueOf(episodeNum));
            addFfmpegMeta(cmd, "title", filenameNoExt);
            addFfmpegMeta(cmd, "album", showName);

            if (episodeTitle != null && !episodeTitle.isBlank()) {
                addFfmpegMeta(cmd, "episode_id", episodeTitle);
            }
            if (airDateStr != null) {
                addFfmpegMeta(cmd, "date", airDateStr);
            }
            addFfmpegMeta(cmd, "media_type", "10");

            cmd.add(tempFile.toString());

            boolean ok = runProcess(cmd, videoFile);
            if (ok) {
                Files.move(tempFile, videoFile, StandardCopyOption.REPLACE_EXISTING);
                tempFile = null; // moved successfully
                return true;
            }
            return false;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed ffmpeg tagging for: " + videoFile, e);
            return false;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static void addFfmpegMeta(List<String> cmd, String key, String value) {
        cmd.add("-metadata");
        cmd.add(key + "=" + value);
    }

    // ---- Process execution (shared) ----

    private boolean runProcess(List<String> command, Path videoFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

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
                logger.warning(detectedTool + " timed out for: " + videoFile);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warning(detectedTool + " failed (exit " + exitCode + ") for: "
                    + videoFile + "\nOutput: " + output);
                return false;
            }

            return true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to run " + detectedTool + " for: " + videoFile, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning(detectedTool + " interrupted for: " + videoFile);
            return false;
        }
    }

    // ---- Tool detection ----

    private static void ensureDetected() {
        if (detectedTool != null) {
            return;
        }

        synchronized (DETECTION_LOCK) {
            if (detectedTool != null) {
                return;
            }

            // Try AtomicParsley first
            String ap = detectAtomicParsley();
            if (!ap.isEmpty()) {
                toolPath = ap;
                detectedTool = Tool.ATOMIC_PARSLEY;
                logger.info("Found AtomicParsley: " + toolPath);
                return;
            }

            // Fall back to ffmpeg
            String ff = detectFfmpeg();
            if (!ff.isEmpty()) {
                toolPath = ff;
                detectedTool = Tool.FFMPEG;
                logger.info("Found ffmpeg (fallback): " + toolPath);
                return;
            }

            toolPath = "";
            detectedTool = Tool.NONE;
            logger.info("No MP4 tagging tool found - MP4 tagging will be disabled");
        }
    }

    private static String detectAtomicParsley() {
        // Try PATH (binary may be named AtomicParsley or atomicparsley)
        for (String name : new String[] { "AtomicParsley", "atomicparsley" }) {
            if (isExecutableInPath(name)) {
                return name;
            }
        }

        // Windows common locations
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String[] paths = {
                "C:\\Program Files\\AtomicParsley\\AtomicParsley.exe",
                "C:\\Program Files (x86)\\AtomicParsley\\AtomicParsley.exe"
            };
            for (String path : paths) {
                if (Files.isExecutable(Paths.get(path))) {
                    return path;
                }
            }
        }

        // macOS Homebrew
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            for (String path : new String[] {
                "/usr/local/bin/AtomicParsley",
                "/opt/homebrew/bin/AtomicParsley"
            }) {
                if (Files.isExecutable(Paths.get(path))) {
                    return path;
                }
            }
        }

        return "";
    }

    private static String detectFfmpeg() {
        if (isExecutableInPath("ffmpeg")) {
            return "ffmpeg";
        }

        // Windows common locations
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String[] paths = {
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe"
            };
            for (String path : paths) {
                if (Files.isExecutable(Paths.get(path))) {
                    return path;
                }
            }
        }

        // macOS Homebrew
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            for (String path : new String[] {
                "/usr/local/bin/ffmpeg",
                "/opt/homebrew/bin/ffmpeg"
            }) {
                if (Files.isExecutable(Paths.get(path))) {
                    return path;
                }
            }
        }

        return "";
    }

    private static boolean isExecutableInPath(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Drain output to avoid blocking
            process.getInputStream().readAllBytes();
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
     * Check if an MP4 tagging tool is available on this system.
     *
     * @return true if AtomicParsley or ffmpeg is installed and accessible
     */
    public static boolean isToolAvailable() {
        ensureDetected();
        return detectedTool != Tool.NONE;
    }

    /**
     * Get a description of the detected MP4 tagging tool, for display in UI.
     *
     * @return tool name (e.g. "AtomicParsley", "ffmpeg"), or "none" if unavailable
     */
    public static String getDetectedToolName() {
        ensureDetected();
        return switch (detectedTool) {
            case ATOMIC_PARSLEY -> "AtomicParsley";
            case FFMPEG -> "ffmpeg";
            case NONE -> "none";
        };
    }
}
