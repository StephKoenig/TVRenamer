package org.tvrenamer.model;

public enum UserPreference {
    REPLACEMENT_MASK,
    MOVE_SELECTED,
    RENAME_SELECTED,
    REMOVE_EMPTY,
    DELETE_ROWS,
    DEST_DIR,
    SEASON_PREFIX,
    LEADING_ZERO,
    ADD_SUBDIRS,
    IGNORE_REGEX,

    SHOW_NAME_OVERRIDES,
    PREFER_DVD_ORDER,
    THEME_MODE,

    // File timestamp policy (e.g., preserve original mtime vs set to now) for move/rename operations.
    FILE_MTIME_POLICY,

    // Since these are only meaningful at startup, they probably should not be watched
    UPDATE_CHECK,
    @SuppressWarnings("unused")
    PRELOAD_FOLDER,
}
