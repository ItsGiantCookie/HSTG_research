package de.germanrangers.hstglabs.api.reward;

import de.germanrangers.hstglabs.api.owner.OwnerId;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/** What a {@link RewardType} is applied to: the owner that finished a technology. */
@ApiStatus.Experimental
public interface RewardContext {

    MinecraftServer server();

    OwnerId owner();

    /** The technology that was completed. */
    ResourceLocation technology();

    /** Members of {@link #owner()} who are online right now. */
    Collection<ServerPlayer> onlineMembers();
}
