package io.github.itsgiantcookie.hstglabs.hs;

import io.github.itsgiantcookie.hstglabs.core.tech.StageModel;
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

    /** Stands for a locking stage that no technology owns (HS's api gives entries, not their ids). */
    public static final String OTHER_STAGE = "<a stage no technology owns>";

    /**
     * Which stages lock the item {@code itemId} (by plain item id, docs/design.md §8.3 ladder checks).
     * {@code knownStages} are the stage ids we can name (the technologies' stages); any further locking
     * stage is reported as {@link #OTHER_STAGE}.
     */
    public static java.util.Set<String> stagesLockingItem(String itemId, java.util.Collection<String> knownStages) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String stage : knownStages) {
            StageEntry entry = StageStates.findStage(stage);
            if (entry != null && entry.getItems().contains(itemId)) {
                out.add(stage);
            }
        }
        long locking = StageStates.loadedStages().stream().filter(e -> e.getItems().contains(itemId)).count();
        if (locking > out.size()) {
            out.add(OTHER_STAGE);
        }
        return out;
    }

    /** Runs {@code onChange} on the server thread whenever HS reloads its stage definitions. */
    public static void onDefinitionsChanged(Runnable onChange) {
        NeoForge.EVENT_BUS.addListener(StageDefinitionsChangedEvent.class, event -> onChange.run());
    }
}
