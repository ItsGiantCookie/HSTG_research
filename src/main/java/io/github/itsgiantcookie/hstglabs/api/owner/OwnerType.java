package io.github.itsgiantcookie.hstglabs.api.owner;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Maps players to research owners (docs/design.md §5.10, §9.2); registered through
 * {@link RegisterOwnerTypesEvent}. Built in: server and team owners.
 */
@ApiStatus.Experimental
public interface OwnerType {

    /** The owner {@code player} researches for, or empty if this type does not apply to them. */
    Optional<OwnerId> ownerOf(ServerPlayer player);

    /** Every member of {@code owner}, online or not; individual stages are unlocked for each. */
    Collection<UUID> members(OwnerId owner, MinecraftServer server);

    /** True if completed research unlocks HS individual stages per member, false for global stages. */
    boolean individualStages();
}
