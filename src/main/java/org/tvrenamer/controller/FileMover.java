package org.tvrenamer.controller;

import static org.tvrenamer.model.util.Constants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tvrenamer.controller.util.FileUtilities;
import org.tvrenamer.model.FileEpisode;
import org.tvrenamer.model.MoveObserver;
import org.tvrenamer.model.UserPreferences;

public class FileMover implements Callable<Boolean> {

    static final Logger logger = Logger.getLogger(FileMover.class.getName());
    static final UserPreferences userPrefs = UserPreferences.getInstance();

    private static String safePath(Path p) {
        return (p == null) ? "<null>" : p.toString();
    }

    private final FileEpisode episode;
    private final Path destRoot;
    private final String destBasename;
    private final String destSuffix;
    private MoveObserver observer = null;
    Integer destIndex = null;

    /**
     * Constructs a FileMover to move the given episode.
     *
     * @param episode
     *    the FileEpisode we intend to move
     */
    public FileMover(FileEpisode episode) {
        this.episode = episode;

        destRoot = episode.getMoveToPath();
        destBasename = episode.getDestinationBasename();
        destSuffix = episode.getFilenameSuffix();
    }

    /**
     * Sets the progress observer for this FileMover
     *
     * @param observer
     *   the observer to add
     */
    public void addObserver(MoveObserver observer) {
        this.observer = observer;
    }

    private void setFailureAndLog(
        Path source,
        Path dest,
        String message,
        Throwable t
    ) {
        episode.setFailToMove();
        if (t == null) {
            logger.warning(
                message +
                    "\n  source=" +
                    safePath(source) +
                    "\n  dest=" +
                    safePath(dest)
            );
        } else {
            logger.log(
                Level.WARNING,
                message +
                    "\n  source=" +
                    safePath(source) +
                    "\n  dest=" +
                    safePath(dest),
                t
            );
        }
    }

    /**
     * Gets the current location of the file to be moved
     *
     * @return the Path where the file is currently located
     */
    Path getCurrentPath() {
        return episode.getPath();
    }

    /**
     * Gets the size (in bytes) of the file to be moved
     *
     * @return the size of the file
     */
    long getFileSize() {
        return episode.getFileSize();
    }

    /**
     * The filename of the destination we want to move the file to.
     * We may not be able to actually use this filename due to a conflict,
     * in which case, we will probably add an index and use a subdirectory.
     * But this is the name we WANT to change it to.
     *
     * @return the filename that we want to move the file to
     */
    String getDesiredDestName() {
        return destBasename + destSuffix;
    }

    /**
     * Gets the name of the directory we should move the file to, as a Path.
     *
     * We call it the "moveToDirectory" because "destinationDirectory" is used more
     * to refer to the top-level directory: the one the user specified in the dialog
     * for user preferences.  This is the subdirectory of that folder that the file
     * should actually be placed in.
     *
     * @return the directory we should move the file to, as a Path.
     */
    Path getMoveToDirectory() {
        return destRoot;
    }

    /**
     * Try to clean up, and log errors, after a move attempt has failed.
     * This is more likely to be useful in the case where the "move" was
     * actually a copy-and-delete, but it's possible a straightforward
     * move also could have partially completed before failing.
     *
     * @param source
     *            The source file we had wanted to move.
     * @param dest
     *            The destination where we tried to move the file.
     *
     */
    private void failToCopy(final Path source, final Path dest) {
        // Mark failure early; we may add more detail below.
        episode.setFailToMove();

        if (dest != null && Files.exists(dest)) {
            // An incomplete copy was done. Try to clean it up.
            FileUtilities.deleteFile(dest);
            if (Files.exists(dest)) {
                logger.warning(
                    "made incomplete copy of \"" +
                        source +
                        "\" to \"" +
                        dest +
                        "\" and could not clean it up"
                );
                return;
            }
        }

        logger.warning("failed to move " + source);

        // Best-effort cleanup of newly created empty folders, if the destination file is not present.
        if (dest != null && Files.notExists(dest)) {
            Path outDir = userPrefs.getDestinationDirectory();
            if (outDir != null) {
                Path parent = dest.getParent();
                while (
                    (parent != null) &&
                    !FileUtilities.isSameFile(parent, outDir)
                ) {
                    boolean rmdired = FileUtilities.rmdir(parent);
                    if (rmdired) {
                        logger.info("removing empty directory " + parent);
                    } else {
                        break;
                    }
                    parent = parent.getParent();
                }
            }
        }
    }

    /**
     * Copies the source file to the destination, and deletes the source.
     *
     * <p>If the destination cannot be created or is a read-only file, the
     * method returns <code>false</code>.  Otherwise, the contents of the
     * source are copied to the destination, the source is deleted, and
     * <code>true</code> is returned.
     *
     * @param source
     *            The source file to move.
     * @param dest
     *            The destination where to move the file.
     *
     */
    private void copyAndDelete(final Path source, final Path dest) {
        if (observer != null) {
            observer.initializeProgress(episode.getFileSize());
        }

        boolean ok = FileUtilities.copyWithUpdates(source, dest, observer);
        if (ok) {
            ok = FileUtilities.deleteFile(source);
            if (!ok) {
                setFailureAndLog(
                    source,
                    dest,
                    "failed to delete original after copy",
                    null
                );
            }
        }

        if (ok) {
            episode.setCopied();
        } else {
            failToCopy(source, dest);
        }
    }

    private void finishMove(final Path actualDest) {
        // TODO: why do we set the file modification time to "now"? Would like to
        // at least make this behavior configurable.
        try {
            FileTime now = FileTime.fromMillis(System.currentTimeMillis());
            Files.setLastModifiedTime(actualDest, now);
        } catch (IOException ioe) {
            // The file moved, but we couldn't set mtime. Keep behavior (mark failure),
            // but improve diagnostics.
            setFailureAndLog(
                actualDest,
                actualDest,
                "unable to set modification time",
                ioe
            );
        }
    }

    /**
     * Execute the file move action.  This method assumes that all sanity checks have been
     * completed and that everything is ready to go: source file and destination directory
     * exist, destination file doesn't, etc.
     *
     * At the end, if the move was successful, it sets the file modification time.
     * Does not return a value, but sets the episode status.
     *
     * @param srcPath
     *    the Path to the file to be moved
     * @param destPath
     *    the Path to which the file should be moved
     * @param tryRename
     *    if false, do not try to simply rename the file; always do a "copy-and-delete"
     */
    private void doActualMove(
        final Path srcPath,
        final Path destPath,
        final boolean tryRename
    ) {
        logger.fine("Going to move\n  '" + srcPath + "'\n  '" + destPath + "'");
        episode.setMoving();

        if (tryRename) {
            Path actualDest = FileUtilities.renameFile(srcPath, destPath);
            if (actualDest == null) {
                setFailureAndLog(
                    srcPath,
                    destPath,
                    "unable to rename/move file",
                    null
                );
                failToCopy(srcPath, destPath);
                return;
            }
            if (destPath.equals(actualDest)) {
                episode.setRenamed();
            } else {
                episode.setPath(actualDest);
                logger.warning(
                    "actual destination did not match intended:\n  " +
                        actualDest +
                        "\n  " +
                        destPath
                );
                episode.setMisnamed();
                return;
            }
        } else {
            logger.info("different disks: " + srcPath + " and " + destPath);
            copyAndDelete(srcPath, destPath);
            if (!episode.isSuccess()) {
                return;
            }
        }

        episode.setPath(destPath);
        finishMove(destPath);
    }

    /**
     * Execute the move using real paths.  Also does side-effects, like
     * updating the FileEpisode.
     *
     * @param realSrc
     *    the "real" Path of the source file to be moved
     * @param destPath
     *    the "real" destination where the file should be moved; can contain
     *    non-existent directories, which will be created
     * @param destDir
     *    an existent ancestor of destPath
     */
    private void tryToMoveRealPaths(Path realSrc, Path destPath, Path destDir) {
        boolean tryRename = FileUtilities.areSameDisk(realSrc, destDir);
        Path srcDir = realSrc.getParent();

        doActualMove(realSrc, destPath, tryRename);
        if (!episode.isSuccess()) {
            logger.info("failed to move " + realSrc);
            return;
        }

        logger.info("successful:\n  " + realSrc + "\n  " + destPath);
        if (userPrefs.isRemoveEmptiedDirectories()) {
            FileUtilities.removeWhileEmpty(srcDir);
        }
    }

    /**
     * Create the version string for the destination filename.
     *
     * @return destination filename with a version added
     */
    String versionString() {
        if (destIndex == null) {
            return "";
        }
        return " (" + destIndex + ")";
    }

    /**
     * Check/verify numerous things, and if everything is as it should be,
     * execute the move.
     *
     * This sanity-checks the move: the source file must exist, the destination
     * file should not, etc.  It may actually change things, e.g., if the
     * destination directory doesn't exist, it will try to create it.  It also
     * gathers information, like whether the source and destination are on the
     * same file store.  And it does side-effects, like updating the FileEpisode.
     */
    private void tryToMoveFile() {
        Path srcPath = episode.getPath();
        if (Files.notExists(srcPath)) {
            logger.info("Path no longer exists: " + srcPath);
            episode.setNoFile();
            return;
        }

        final Path realSrc;
        try {
            realSrc = srcPath.toRealPath();
        } catch (IOException ioe) {
            setFailureAndLog(
                srcPath,
                null,
                "could not get real path of source",
                ioe
            );
            return;
        }

        episode.setFileVerified();

        Path destDir = destRoot;
        String filename = destBasename + destSuffix;
        if (destIndex != null) {
            if (userPrefs.isMoveEnabled()) {
                destDir = destRoot.resolve(DUPLICATES_DIRECTORY);
            }
            filename = destBasename + versionString() + destSuffix;
        }

        if (!FileUtilities.ensureWritableDirectory(destDir)) {
            setFailureAndLog(
                srcPath,
                destDir,
                "not attempting to move; destination directory not writable",
                null
            );
            return;
        }

        // Some UNC/SMB paths can fail real-path resolution even when the location is valid and writable.
        // Prefer real paths when available, but fall back to an absolute/normalized path instead of hard-failing.
        Path resolvedDestDir;
        try {
            resolvedDestDir = destDir.toRealPath();
        } catch (IOException ioe) {
            logger.log(
                Level.INFO,
                "could not get real path of destination directory; continuing with non-real path:\n  destDir=" +
                    safePath(destDir) +
                    "\n  source=" +
                    safePath(srcPath),
                ioe
            );
            resolvedDestDir = destDir.toAbsolutePath().normalize();
        }

        Path destPath = resolvedDestDir.resolve(filename);
        if (Files.exists(destPath)) {
            if (destPath.equals(realSrc)) {
                logger.info("nothing to be done to " + srcPath);
                episode.setAlreadyInPlace();
                return;
            }
            setFailureAndLog(
                srcPath,
                destPath,
                "cannot move; destination exists",
                null
            );
            return;
        }

        tryToMoveRealPaths(realSrc, destPath, resolvedDestDir);
    }

    /**
     * Attempt the move, and update the FileEpisode.
     *
     * Using the attributes set in this instance, execute the move
     * functionality, and update the status of the FileEpisode with
     * the result.
     *
     * @return true on success, false otherwise.
     */
    @Override
    public Boolean call() {
        try {
            // There are numerous reasons why the move would fail. Instead of calling
            // setFailToMove on the episode in each individual case, make the functionality
            // into a subfunction, and set the episode here for any of the failure cases.
            tryToMoveFile();
        } catch (RuntimeException e) {
            // RuntimeException is unexpected here; log with full context.
            setFailureAndLog(
                getCurrentPath(),
                destRoot,
                "unexpected runtime exception during file move",
                e
            );
        } catch (Exception e) {
            // Defensive: keep legacy behavior but improve diagnostics.
            setFailureAndLog(
                getCurrentPath(),
                destRoot,
                "exception caught doing file move",
                e
            );
        } finally {
            if (observer != null) {
                observer.finishProgress(episode);
            }
        }

        return episode.isSuccess();
    }
}
