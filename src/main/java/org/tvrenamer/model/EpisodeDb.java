package org.tvrenamer.model;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tvrenamer.controller.AddEpisodeListener;
import org.tvrenamer.controller.util.FileUtilities;

public class EpisodeDb implements java.beans.PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(
        EpisodeDb.class.getName()
    );
    private static final UserPreferences prefs = UserPreferences.getInstance();

    private final Map<String, FileEpisode> episodes = new ConcurrentHashMap<>(
        1000
    );
    private List<String> ignoreKeywords = prefs.getIgnoreKeywords();

    public EpisodeDb() {
        prefs.addPropertyChangeListener(this);
    }

    private String ignorableReason(String fileName) {
        for (String ignoreKeyword : ignoreKeywords) {
            if (fileName.contains(ignoreKeyword)) {
                return ignoreKeyword;
            }
        }
        return null;
    }

    private FileEpisode add(final String pathname) {
        Path path = Paths.get(pathname);
        final FileEpisode episode = new FileEpisode(path);
        episode.setIgnoreReason(ignorableReason(pathname));
        if (!episode.wasParsed()) {
            // Note: unparsed files are still inserted so users can see what failed.
            // Follow-up UX improvements are tracked in docs/todo.md ("Improve handling of \"unparsed\" files").
            logger.warning("Couldn't parse file: " + pathname);
        }
        episodes.put(pathname, episode);
        return episode;
    }

    /**
     * Remove the given key from the Episode database
     *
     * This is called when the user removes a row from the table. It's possible
     * (even if unlikely) that the user might delete the entry, only to re-add
     * it later. And this works fine. But it does cause us to recreate the
     * FileEpisode from scratch. It might be nice to put removed episodes
     * "aside" somewhere that we could still find them, but just know they're
     * not actively in the table.
     *
     * @param key
     *            the key to remove from the Episode database
     */
    public void remove(String key) {
        episodes.remove(key);
    }

    public FileEpisode get(String key) {
        return episodes.get(key);
    }

    private boolean fileIsVisible(Path path) {
        boolean isVisible = false;
        try {
            if (Files.exists(path)) {
                if (Files.isHidden(path)) {
                    logger.finer("ignoring hidden file " + path);
                } else {
                    isVisible = true;
                }
            }
        } catch (IOException | SecurityException e) {
            logger.finer("could not access file; treating as hidden: " + path);
        }
        return isVisible;
    }

    /**
     * Get the current location -- and, therefore, the database key -- for the
     * file that has been referred to by the given key.
     *
     * That is, we know where the file USED TO be. It may still be there; it may
     * have moved. Tell the caller where it is now, which is also how to retrieve
     * its FileEpisode object.
     *
     * @param key
     *            a String, representing a path to the last known location of the
     *            file,
     *            to look up and check
     * @return the current location, if the file still exists; null if the file
     *         is no longer valid
     *
     *         The method might change the internal database, if it detects the file
     *         has
     *         been moved. That means, the key given will no longer be valid when
     *         the
     *         method returns. The return value does not explicitly give an
     *         indication
     *         of whether or not that's true. Callers must simply use the returned
     *         value
     *         as the key after this function returns, or must do a comparison with
     *         the
     *         previous key to see if it's still valid.
     *
     */
    public String currentLocationOf(final String key) {
        if (key == null) {
            return null;
        }
        FileEpisode ep = episodes.get(key);
        if (ep == null) {
            return null;
        }
        Path currentLocation = ep.getPath();
        if (
            fileIsVisible(currentLocation) &&
            Files.isRegularFile(currentLocation)
        ) {
            // OK, the file is good! But that could be true even if
            // it were moved. Now try to see if it's been moved, or if
            // it's still where we think it is.
            String direct = currentLocation.toString();
            if (key.equals(direct)) {
                return key;
            }
            // Even if the strings don't match directly, we're not going
            // to change anything if they both refer to the same file.
            // Note: path canonicalization policy (including UNC/SMB edge cases) is tracked in docs/todo.md
            // under "Consider canonicalization of file paths in EpisodeDb".
            Path keyPath = Paths.get(key);
            if (FileUtilities.isSameFile(currentLocation, keyPath)) {
                return key;
            }
            // The file has been moved. We update our database, and inform the
            // caller of the new key.
            episodes.remove(key);
            episodes.put(direct, ep);
            return direct;
        } else {
            // The file has disappeared out from under us (or, bizarrely, been replaced
            // by a directory? Anything is possible...). Remove it from the db and let
            // the caller know by returning null.
            episodes.remove(key);
            return null;
        }
    }

    private void addFileToQueue(
        final Queue<FileEpisode> contents,
        final Path path
    ) {
        final Path absPath = path.toAbsolutePath();
        final String key = absPath.toString();
        if (episodes.containsKey(key)) {
            logger.info("already in table: " + key);
        } else {
            FileEpisode ep = add(key);
            contents.add(ep);
        }
    }

    private void addFileIfVisible(
        final Queue<FileEpisode> contents,
        final Path path
    ) {
        if (fileIsVisible(path) && Files.isRegularFile(path)) {
            addFileToQueue(contents, path);
        }
    }

    private void addFilesRecursively(
        final Queue<FileEpisode> contents,
        final Path parent,
        final Path filename
    ) {
        if (parent == null) {
            logger.warning("cannot add files; parent is null");
            return;
        }
        if (filename == null) {
            logger.warning("cannot add files; filename is null");
            return;
        }
        final Path fullpath = parent.resolve(filename);
        if (fileIsVisible(fullpath)) {
            if (Files.isDirectory(fullpath)) {
                try (
                    DirectoryStream<Path> files = Files.newDirectoryStream(
                        fullpath
                    )
                ) {
                    if (files != null) {
                        // recursive call
                        files.forEach(pth ->
                            addFilesRecursively(
                                contents,
                                fullpath,
                                pth.getFileName()
                            )
                        );
                    }
                } catch (IOException ioe) {
                    logger.warning("IO Exception descending " + fullpath);
                }
            } else {
                addFileToQueue(contents, fullpath);
            }
        }
    }

    /**
     * Add the given folder to the queue. This is intended to support the
     * "Add Folder" functionality. This method itself does only sanity
     * checking, and if everything's in order, calls addFilesRecursively()
     * to do the actual work.
     *
     * @param pathname the name of a folder
     */
    public void addFolderToQueue(final String pathname) {
        if (!prefs.isRecursivelyAddFolders()) {
            logger.warning(
                "cannot add folder when preference \"add files recursively\" is off"
            );
            return;
        }

        if (pathname == null) {
            logger.warning("cannot add files; pathname is null");
            return;
        }

        Queue<FileEpisode> contents = new ConcurrentLinkedQueue<>();
        final Path path = Paths.get(pathname);
        addFilesRecursively(contents, path.getParent(), path.getFileName());
        publish(contents);
    }

    /**
     * Add the given array of filename Strings, each of which are expected to be
     * found within the directory given by the pathPrefix, to the queue.
     * This is intended to support the "Add Files" functionality.
     *
     * @param pathPrefix the directory where the fileNames are found
     * @param fileNames  an array of Strings presumed to represent filenames
     */
    public void addFilesToQueue(final String pathPrefix, String[] fileNames) {
        Queue<FileEpisode> contents = new ConcurrentLinkedQueue<>();
        if (pathPrefix != null) {
            Path path = Paths.get(pathPrefix);
            Path parent = path.getParent();

            for (String fileName : fileNames) {
                path = parent.resolve(fileName);
                addFileIfVisible(contents, path);
            }
            publish(contents);
        }
    }

    /**
     * Add the given array of filename Strings to the queue. This is intended
     * to support Drag and Drop.
     *
     * @param fileNames an array of Strings presumed to represent filenames
     */
    public void addArrayOfStringsToQueue(final String[] fileNames) {
        Queue<FileEpisode> contents = new ConcurrentLinkedQueue<>();
        boolean descend = prefs.isRecursivelyAddFolders();
        for (final String fileName : fileNames) {
            final Path path = Paths.get(fileName);
            if (descend) {
                addFilesRecursively(
                    contents,
                    path.getParent(),
                    path.getFileName()
                );
            } else {
                addFileIfVisible(contents, path);
            }
        }
        publish(contents);
    }

    /**
     * Add the contents of the preload folder to the queue.
     *
     * This can involve scanning a large directory tree, so it runs on a background
     * thread to avoid blocking the UI thread.
     *
     */
    public void preload() {
        if (!prefs.isRecursivelyAddFolders()) {
            return;
        }

        final String preload = prefs.getPreloadFolder();
        if (preload == null) {
            return;
        }

        final Thread t = new Thread(
            () -> {
                try {
                    addFolderToQueue(preload);
                } catch (Exception e) {
                    logger.log(
                        Level.WARNING,
                        "Exception while preloading folder: " + preload,
                        e
                    );
                }
            },
            "EpisodeDbPreload"
        );
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void propertyChange(java.beans.PropertyChangeEvent evt) {
        if (
            "preference".equals(evt.getPropertyName()) &&
            (evt.getNewValue() == UserPreference.IGNORE_REGEX)
        ) {
            ignoreKeywords = prefs.getIgnoreKeywords();
            for (FileEpisode ep : episodes.values()) {
                ep.setIgnoreReason(ignorableReason(ep.getFilepath()));
            }
            listeners.forEach(AddEpisodeListener::refreshDestinations);
        }
    }

    /**
     * Standard object method to represent this EpisodeDb as a string.
     *
     * @return string version of this; just says how many episodes are in the map.
     */
    @Override
    public String toString() {
        return "{EpisodeDb with " + episodes.size() + " files}";
    }

    private final Queue<AddEpisodeListener> listeners =
        new ConcurrentLinkedQueue<>();

    /**
     * Register interest in files and folders that are added to the queue.
     *
     * @param listener
     *                 the AddEpisodeListener that should be called when we have
     *                 finished processing
     *                 a folder or array of files
     */
    public void subscribe(AddEpisodeListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify registered interested parties that we've finished adding a folder or
     * array of files to the queue, and pass the queue to each listener.
     *
     * @param episodes
     *                 the queue of FileEpisode objects we've created since the last
     *                 time we
     *                 published
     */
    private void publish(Queue<FileEpisode> episodes) {
        for (AddEpisodeListener listener : listeners) {
            listener.addEpisodes(episodes);
        }
    }
}
