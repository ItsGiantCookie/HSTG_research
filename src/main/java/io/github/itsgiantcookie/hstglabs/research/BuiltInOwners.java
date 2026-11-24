package io.github.itsgiantcookie.hstglabs.research;

import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerType;
import io.github.itsgiantcookie.hstglabs.api.owner.RegisterOwnerTypesEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/**
 * The built-in research owners (docs/design.md §5.10, D2, D31), registered through the public event.
 *
 * <ul>
 *   <li>{@code hstg_labs:server}: one owner for the whole server, global stages
 *       ({@code owner_model = SERVER}).</li>
 *   <li>{@code hstg_labs:team}: the player's team, individual stages unlocked for every member
 *       ({@code owner_model = TEAM}, the default). With FTB Teams installed, {@code compat.ftbteams}
 *       registers this id with FTB team ids; without it, this class registers a team of one keyed by
 *       the player's UUID. FTB's own id for a player in no party is the player's UUID too, so keys stay
 *       the same when FTB Teams is added to a world.</li>
 * </ul>
 */
public final class BuiltInOwners {

    public static final ResourceLocation SERVER = ResourceLocation.fromNamespaceAndPath("hstg_labs", "server");
    public static final ResourceLocation TEAM = ResourceLocation.fromNamespaceAndPath("hstg_labs", "team");
    public static final OwnerId SERVER_OWNER = new OwnerId(SERVER, "server");

    private BuiltInOwners() {
    }

    /** {@code withTeamOfOne}: false when a team mod's compat registers {@link #TEAM} itself. */
    public static void register(IEventBus modBus, boolean withTeamOfOne) {
        modBus.addListener(RegisterOwnerTypesEvent.class, e -> {
            e.register(SERVER, new ServerOwner());
            if (withTeamOfOne) {
                e.register(TEAM, new TeamOfOne());
            }
        });
    }

    static final class ServerOwner implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(SERVER_OWNER);
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            return server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList();
        }

        @Override
        public boolean individualStages() {
            return false;
        }
    }

    /** Every player is their own team (D31 fallback). */
    static final class TeamOfOne implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(new OwnerId(TEAM, player.getUUID().toString()));
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            try {
                return List.of(UUID.fromString(owner.key()));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }

        @Override
        public boolean individualStages() {
            return true;
        }
    }
}
