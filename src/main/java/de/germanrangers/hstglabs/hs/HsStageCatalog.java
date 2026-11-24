package de.germanrangers.hstglabs.hs;

import de.germanrangers.hstglabs.core.tech.StageModel;
import java.util.Optional;
import net.bananemdnsa.historystages.api.stage.StageDefinitionsChangedEvent;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The stage facts technology validation needs (docs/design.md §8.3), read from History Stages:
 * whether a stage is loaded and whether it is in mode {@code external} (D10). Facts:
 * docs/hs-api-facts.md (findStage, getMode, StageMode, StageDefinitionsChangedEvent).
 */
public final class HsStageCatalog {

    private HsStageCatalog() {
    }

    /** The stage with this id, or empty if HS has no such stage loaded. Server thread. */
    public static Optional<StageModel> find(String stageId) {
        StageEntry entry = StageStates.findStage(stageId);
        return entry == null ? Optional.empty() : Optional.of(new StageModel(stageId, entry.getMode() == StageMode.EXTERNAL));
    }

    /** Runs {@code onChange} on the server thread whenever HS reloads its stage definitions. */
    public static void onDefinitionsChanged(Runnable onChange) {
        NeoForge.EVENT_BUS.addListener(StageDefinitionsChangedEvent.class, event -> onChange.run());
    }
}
