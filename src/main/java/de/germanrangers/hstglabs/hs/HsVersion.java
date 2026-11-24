package de.germanrangers.hstglabs.hs;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.api.HistoryStagesAPI;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Startup check of the History Stages API generation (docs/design.md §7.1, "Version" row and rule 7).
 * <p>
 * {@code API_VERSION} / {@code API_MINOR} are compile-time constants, so a plain read would only log
 * what we compiled against. The installed values are read reflectively, so the log shows a 6.x
 * release that raised the generation on its own.
 * <p>
 * Facts: docs/hs-api-facts.md, "Version".
 */
public final class HsVersion {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** What this build was compiled against (inlined by javac). */
    public static final int COMPILED_API_VERSION = HistoryStagesAPI.API_VERSION;
    public static final int COMPILED_API_MINOR = HistoryStagesAPI.API_MINOR;

    private HsVersion() {
    }

    public static int runtimeApiVersion() {
        return runtimeConstant("API_VERSION", COMPILED_API_VERSION);
    }

    public static int runtimeApiMinor() {
        return runtimeConstant("API_MINOR", COMPILED_API_MINOR);
    }

    public static void logAtStartup() {
        int version = runtimeApiVersion();
        int minor = runtimeApiMinor();
        String modVersion = ModList.get().getModContainerById("historystages")
              .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        LOGGER.info("HS API {}.{} (History Stages {}, compiled against API {}.{})",
              version, minor, modVersion, COMPILED_API_VERSION, COMPILED_API_MINOR);
        if (version != COMPILED_API_VERSION) {
            LOGGER.warn("History Stages API generation changed from {} to {}; this build may not work with it", COMPILED_API_VERSION, version);
        }
    }

    private static int runtimeConstant(String field, int fallback) {
        try {
            return HistoryStagesAPI.class.getField(field).getInt(null);
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }
}
