package de.germanrangers.hstglabs.api.lab;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.ApiStatus;

/**
 * Computes a member's stats instead of reading them from its lab type (docs/design.md §5.4): tiered
 * labs, multiblock controllers. Registered as a {@link Factory} per block entity type id through
 * {@link RegisterLabStatsProvidersEvent}.
 */
@ApiStatus.Experimental
public interface LabStatsProvider {

    /** Current stats; may change while the block entity lives. Server thread. */
    LabStats stats();

    /** Creates the provider for one block entity of the registered type. */
    @ApiStatus.Experimental
    @FunctionalInterface
    interface Factory {

        LabStatsProvider create(BlockEntity blockEntity);
    }
}
