package de.germanrangers.hstglabs.api.reward;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.ApiStatus;

/**
 * Something granted when a technology completes (docs/design.md §8.1 {@code rewards}, §9.2);
 * registered through {@link RegisterRewardTypesEvent}. Built in: {@code hstg_labs:unlock_stage}.
 *
 * @param <T> the parsed reward entry
 */
@ApiStatus.Experimental
public interface RewardType<T> {

    /** Fields of this type inside a technology's reward entry. */
    MapCodec<T> codec();

    /** Grants the reward. Server thread. */
    void apply(T reward, RewardContext context);
}
