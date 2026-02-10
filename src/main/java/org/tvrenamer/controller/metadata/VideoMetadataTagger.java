package org.tvrenamer.controller.metadata;

import java.nio.file.Path;

import org.tvrenamer.model.FileEpisode;

/**
 * Interface for tagging video files with TV episode metadata.
 * Implementations exist for different container formats (MP4, MKV, etc.)
 */
public interface VideoMetadataTagger {

    /**
     * Check if this tagger supports the given file extension.
     *
     * @param extension file extension including the dot (e.g., ".mp4")
     * @return true if this tagger can handle the format
     */
    boolean supportsExtension(String extension);

    /**
     * Write TV metadata to the video file.
     *
     * @param videoFile the video file to tag
     * @param episode the episode data to write
     * @return true if tagging succeeded, false otherwise
     */
    boolean tagFile(Path videoFile, FileEpisode episode);

    /**
     * Check if the external tool required by this tagger is available.
     *
     * @return true if the tool is installed and accessible
     */
    boolean isToolAvailable();

    /**
     * Get the name of the external tool used by this tagger, for display in UI.
     *
     * @return tool name (e.g. "AtomicParsley", "mkvpropedit"), or "none" if unavailable
     */
    String getToolName();
}
