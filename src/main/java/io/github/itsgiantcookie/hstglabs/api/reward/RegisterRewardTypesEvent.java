package io.github.itsgiantcookie.hstglabs.api.reward;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link RewardType}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterRewardTypesEvent extends RegisterIdEvent<RewardType<?>> {

    @ApiStatus.Internal
    public RegisterRewardTypesEvent(Registrar<RewardType<?>> registrar) {
        super(registrar);
    }
}
