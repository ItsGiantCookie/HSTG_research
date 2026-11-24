package de.germanrangers.hstglabs.core.tech;

import java.util.Objects;

/**
 * What validation needs to know about one HS stage. The hs package fills it from History Stages.
 *
 * @param id       stage id
 * @param external the stage is in HS mode {@code external} (D10)
 */
public record StageModel(String id, boolean external) {

    public StageModel {
        Objects.requireNonNull(id, "id");
    }
}
