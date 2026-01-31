package org.tvrenamer.controller.metadata;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mp4parser.Box;
import org.mp4parser.Container;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.apple.AppleItemListBox;
import org.mp4parser.boxes.iso14496.part12.MetaBox;
import org.mp4parser.boxes.iso14496.part12.MovieBox;
import org.mp4parser.boxes.iso14496.part12.UserDataBox;
import org.tvrenamer.model.Episode;
import org.tvrenamer.model.EpisodePlacement;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.Show;

/**
 * Tags MP4 files with TV episode metadata using mp4parser.
 *
 * Writes iTunes-style TV atoms for broad media manager compatibility:
 * - tvsh: TV Show name
 * - (c)alb: Album (show name - used by some players like Plex)
 * - tvsn: Season number
 * - tves: Episode number
 * - tven: Episode title (TV Episode Name)
 * - (c)nam: Title (filename without extension - general display name)
 * - (c)day: Air date (ISO-8601 format)
 * - stik: Media kind (10 = TV Show)
 */
public class Mp4MetadataTagger implements VideoMetadataTagger {

    private static final Logger logger = Logger.getLogger(Mp4MetadataTagger.class.getName());

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".mp4", ".m4v", ".mov"
    );

    // iTunes atom types (4CC codes)
    private static final String ATOM_TVSH = "tvsh";  // TV Show name
    private static final String ATOM_ALB = "\u00A9alb";  // Album (©alb) - show name for Plex/others
    private static final String ATOM_TVSN = "tvsn";  // TV Season number
    private static final String ATOM_TVES = "tves";  // TV Episode number
    private static final String ATOM_TVEN = "tven";  // TV Episode name/title
    private static final String ATOM_NAM = "\u00A9nam";  // Title (©nam) - filename without extension
    private static final String ATOM_DAY = "\u00A9day";  // Date/Year (©day)
    private static final String ATOM_STIK = "stik";  // Media kind (10 = TV Show)

    // Media kind values for stik atom
    private static final int MEDIA_KIND_TV_SHOW = 10;

    @Override
    public boolean supportsExtension(String extension) {
        if (extension == null) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean tagFile(Path videoFile, FileEpisode episode) {
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

        // Get filename without extension for ©nam (general title/display name)
        String filename = videoFile.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String filenameNoExt = (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;

        // Get air date - use full ISO date (YYYY-MM-DD) if available, else just year
        String airDateStr = null;
        LocalDate airDate = ep.getAirDate();
        if (airDate != null) {
            airDateStr = airDate.toString();  // ISO-8601 format: YYYY-MM-DD
        }

        logger.fine("Tagging " + filename + " with: " +
            "show=" + showName + ", S" + season + "E" + episodeNum +
            ", title=" + episodeTitle + ", airDate=" + airDateStr);

        // Verify file is a valid MP4 container before attempting to parse
        if (!isValidMp4File(videoFile)) {
            logger.fine("Skipping metadata tagging - not a valid MP4 container: " + videoFile);
            return true; // Not an error, just skip
        }

        Path tempFile = null;
        try {
            // Read the MP4 file
            IsoFile isoFile = new IsoFile(videoFile.toString());

            // Navigate to or create the ilst (iTunes metadata) box
            AppleItemListBox ilst = getOrCreateItunesList(isoFile);
            if (ilst == null) {
                logger.warning("Could not create metadata structure in " + videoFile);
                isoFile.close();
                return false;
            }

            // Update metadata atoms for broad media manager compatibility
            setStringAtom(ilst, ATOM_TVSH, showName);
            setStringAtom(ilst, ATOM_ALB, showName);  // Album = show name (Plex, others)
            setIntegerAtom(ilst, ATOM_TVSN, season);
            setIntegerAtom(ilst, ATOM_TVES, episodeNum);
            if (episodeTitle != null && !episodeTitle.isBlank()) {
                setStringAtom(ilst, ATOM_TVEN, episodeTitle);  // TV Episode Name
            }
            setStringAtom(ilst, ATOM_NAM, filenameNoExt);  // Title = filename (display name)
            if (airDateStr != null) {
                setStringAtom(ilst, ATOM_DAY, airDateStr);
            }
            // Set media kind to TV Show for better compatibility
            setIntegerAtom(ilst, ATOM_STIK, MEDIA_KIND_TV_SHOW);

            // Write to temp file, then replace original
            tempFile = Files.createTempFile(
                videoFile.getParent(),
                ".tvr-tag-",
                ".tmp"
            );

            try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw");
                 FileChannel fc = raf.getChannel()) {
                isoFile.getBox(fc);
            }

            isoFile.close();

            // Replace original with tagged version
            Files.move(tempFile, videoFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null; // Successfully moved, don't delete in finally

            logger.fine("Successfully tagged " + videoFile.getFileName());
            return true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to tag " + videoFile, e);
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unexpected error tagging " + videoFile, e);
            return false;
        } finally {
            // Clean up temp file if still exists
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
        }
    }

    /**
     * Navigate to or create the iTunes metadata list box (ilst).
     * Creates the full path moov/udta/meta/ilst if needed.
     */
    private AppleItemListBox getOrCreateItunesList(IsoFile isoFile) {
        // Find or create moov box
        MovieBox moov = findBox(isoFile, MovieBox.class);
        if (moov == null) {
            logger.warning("No moov box found in file");
            return null;
        }

        // Find or create udta box
        UserDataBox udta = findBox(moov, UserDataBox.class);
        if (udta == null) {
            udta = new UserDataBox();
            moov.addBox(udta);
        }

        // Find or create meta box
        MetaBox meta = findBox(udta, MetaBox.class);
        if (meta == null) {
            meta = new MetaBox();
            udta.addBox(meta);
        }

        // Find or create ilst box
        AppleItemListBox ilst = findBox(meta, AppleItemListBox.class);
        if (ilst == null) {
            ilst = new AppleItemListBox();
            meta.addBox(ilst);
        }

        return ilst;
    }

    /**
     * Find a box of the specified type in a container.
     */
    @SuppressWarnings("unchecked")
    private <T extends Box> T findBox(Container container, Class<T> clazz) {
        List<Box> boxes = container.getBoxes();
        for (Box box : boxes) {
            if (clazz.isInstance(box)) {
                return (T) box;
            }
        }
        return null;
    }

    /**
     * Set a string atom in the iTunes metadata list.
     * Creates or updates the atom as needed.
     */
    private void setStringAtom(AppleItemListBox ilst, String atomType, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        // Remove existing atom if present
        removeAtom(ilst, atomType);

        // Create new atom with value
        // mp4parser uses AppleXxxBox classes for specific atoms
        // For generic atoms, we need to create a custom data box
        try {
            Box newAtom = createStringAtom(atomType, value);
            if (newAtom != null) {
                ilst.addBox(newAtom);
            }
        } catch (Exception e) {
            logger.fine("Could not create atom " + atomType + ": " + e.getMessage());
        }
    }

    /**
     * Set an integer atom in the iTunes metadata list.
     */
    private void setIntegerAtom(AppleItemListBox ilst, String atomType, int value) {
        // Remove existing atom if present
        removeAtom(ilst, atomType);

        try {
            Box newAtom = createIntegerAtom(atomType, value);
            if (newAtom != null) {
                ilst.addBox(newAtom);
            }
        } catch (Exception e) {
            logger.fine("Could not create atom " + atomType + ": " + e.getMessage());
        }
    }

    /**
     * Remove an atom from the iTunes list by type.
     */
    private void removeAtom(AppleItemListBox ilst, String atomType) {
        List<Box> boxes = ilst.getBoxes();
        boxes.removeIf(box -> atomType.equals(box.getType()));
    }

    /**
     * Create a string data atom.
     * iTunes metadata atoms have a specific structure:
     * - 4-byte atom type
     * - data box with type indicator and UTF-8 string
     */
    private Box createStringAtom(String atomType, String value) {
        // Use mp4parser's generic mechanism to create iTunes atoms
        // The AppleItemListBox expects boxes with "data" sub-boxes
        return new GenericItunesStringBox(atomType, value);
    }

    /**
     * Create an integer data atom.
     */
    private Box createIntegerAtom(String atomType, int value) {
        return new GenericItunesIntegerBox(atomType, value);
    }

    /**
     * Generic iTunes string box for arbitrary atom types.
     */
    private static class GenericItunesStringBox implements Box {
        private final String type;
        private final String value;

        GenericItunesStringBox(String type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public long getSize() {
            // Box header (8) + data box header (8) + data header (8) + string bytes
            byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return 8 + 8 + 8 + valueBytes.length;
        }

        @Override
        public void getBox(java.nio.channels.WritableByteChannel writableByteChannel) throws IOException {
            byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int dataBoxSize = 8 + 8 + valueBytes.length; // data box header + data header + content
            int totalSize = 8 + dataBoxSize; // outer box header + data box

            ByteBuffer buffer = ByteBuffer.allocate(totalSize);

            // Outer box header
            buffer.putInt(totalSize);
            buffer.put(type.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

            // Data box header
            buffer.putInt(dataBoxSize);
            buffer.put("data".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

            // Data atom content header (type + locale)
            buffer.putInt(1); // Type: 1 = UTF-8 string
            buffer.putInt(0); // Locale (reserved)

            // String data
            buffer.put(valueBytes);

            buffer.flip();
            writableByteChannel.write(buffer);
        }
    }

    /**
     * Generic iTunes integer box for arbitrary atom types.
     */
    private static class GenericItunesIntegerBox implements Box {
        private final String type;
        private final int value;

        GenericItunesIntegerBox(String type, int value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public long getSize() {
            // Box header (8) + data box header (8) + data header (8) + 4 bytes for int
            return 8 + 8 + 8 + 4;
        }

        @Override
        public void getBox(java.nio.channels.WritableByteChannel writableByteChannel) throws IOException {
            int dataBoxSize = 8 + 8 + 4; // data box header + data header + int content
            int totalSize = 8 + dataBoxSize; // outer box header + data box

            ByteBuffer buffer = ByteBuffer.allocate(totalSize);

            // Outer box header
            buffer.putInt(totalSize);
            buffer.put(type.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

            // Data box header
            buffer.putInt(dataBoxSize);
            buffer.put("data".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

            // Data atom content header (type + locale)
            buffer.putInt(21); // Type: 21 = signed integer (BE)
            buffer.putInt(0);  // Locale (reserved)

            // Integer data (big-endian)
            buffer.putInt(value);

            buffer.flip();
            writableByteChannel.write(buffer);
        }
    }

    /**
     * Check if a file is a valid MP4 container by looking for the 'ftyp' box signature.
     * MP4 files should have 'ftyp' within the first 12 bytes (after the box size).
     *
     * @param file the file to check
     * @return true if the file appears to be a valid MP4 container
     */
    private boolean isValidMp4File(Path file) {
        // Minimum size for a valid MP4 with ftyp box
        try {
            long fileSize = Files.size(file);
            if (fileSize < 12) {
                return false;
            }

            byte[] header = new byte[12];
            try (java.io.InputStream is = Files.newInputStream(file)) {
                int read = is.read(header);
                if (read < 12) {
                    return false;
                }
            }

            // Check for 'ftyp' at offset 4 (after 4-byte size field)
            // Format: [4 bytes size][4 bytes type 'ftyp'][4 bytes brand]
            String boxType = new String(header, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if ("ftyp".equals(boxType)) {
                return true;
            }

            // Some files may have a different structure, but ftyp should be near the start
            // For safety, we require ftyp at offset 4
            return false;

        } catch (IOException e) {
            logger.fine("Could not read file header: " + file + " - " + e.getMessage());
            return false;
        }
    }
}
