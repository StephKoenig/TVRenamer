package org.tvrenamer.model;

import static org.tvrenamer.model.util.Constants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.tvrenamer.controller.GlobalOverridesPersistence;

public class GlobalOverrides {

    private static final Logger logger = Logger.getLogger(
        GlobalOverrides.class.getName()
    );

    private static final GlobalOverrides INSTANCE = loadOrCreate();

    private final Map<String, String> showNames;

    private GlobalOverrides() {
        showNames = new HashMap<>();
    }

    public static GlobalOverrides getInstance() {
        return INSTANCE;
    }

    private static GlobalOverrides loadOrCreate() {
        GlobalOverrides overrides = GlobalOverridesPersistence.retrieve(
            OVERRIDES_FILE
        );

        if (overrides != null) {
            logger.fine(
                "Successfully read overrides from: " +
                    OVERRIDES_FILE.toAbsolutePath()
            );
            return overrides;
        }

        overrides = new GlobalOverrides();
        persist(overrides);
        return overrides;
    }

    public static void persist(GlobalOverrides overrides) {
        GlobalOverridesPersistence.persist(overrides, OVERRIDES_FILE);
        logger.fine("Successfully saved/updated overrides");
    }

    public String getShowName(String showName) {
        String name = showNames.get(showName);
        return (name != null) ? name : showName;
    }
}
