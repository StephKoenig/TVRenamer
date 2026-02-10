package org.tvrenamer.controller.metadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tvrenamer.controller.util.StringUtils;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.UserPreferences;

/**
 * Coordinates metadata tagging for video files.
 * Selects appropriate tagger based on file extension.
 */
public class MetadataTaggingController {

    private static final Logger logger = Logger.getLogger(MetadataTaggingController.class.getName());
    private static final UserPreferences userPrefs = UserPreferences.getInstance();

    private final List<VideoMetadataTagger> taggers;

    /**
     * Creates a new MetadataTaggingController with all available taggers.
     */
    public MetadataTaggingController() {
        taggers = new ArrayList<>();
        taggers.add(new Mp4MetadataTagger());
        taggers.add(new MkvMetadataTagger());  // Requires mkvpropedit; skips if not found
    }

    /**
     * Tag a video file with episode metadata if enabled and supported.
     *
     * @param videoFile the file to tag
     * @param episode the episode metadata source
     * @return true if tagging succeeded or was skipped (disabled/unsupported format),
     *         false only if tagging was attempted and failed
     */
    public boolean tagIfEnabled(Path videoFile, FileEpisode episode) {
        if (!userPrefs.isTagVideoMetadata()) {
            logger.fine("Metadata tagging is disabled");
            return true; // Disabled, skip silently
        }

        String filename = videoFile.getFileName().toString();
        String extension = StringUtils.getExtension(filename);

        for (VideoMetadataTagger tagger : taggers) {
            if (tagger.supportsExtension(extension)) {
                logger.log(Level.FINE, () -> "Tagging " + filename + " with " + tagger.getClass().getSimpleName());
                try {
                    return tagger.tagFile(videoFile, episode);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Exception during metadata tagging for: " + videoFile, e);
                    return false;
                }
            }
        }

        // No tagger for this format - not an error
        logger.log(Level.FINE, () -> "No metadata tagger available for extension: " + extension);
        return true;
    }
}
