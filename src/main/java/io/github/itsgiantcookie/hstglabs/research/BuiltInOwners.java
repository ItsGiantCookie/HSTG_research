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
 *       ({@code owner_model = TEAM}, the default). The team id comes from a {@link TeamProvider}:
 *       FTB Teams when it is installed (WP-08 step 3), otherwise a team of one keyed by the player's
 *       UUID.</li>
 * </ul>
 */
public final class BuiltInOwners {

    public static final ResourceLocation SERVER = ResourceLocation.fromNamespaceAndPath("hstg_labs", "server");
    public static final ResourceLocation TEAM = ResourceLocation.fromNamespaceAndPath("hstg_labs", "team");
    public static final OwnerId SERVER_OWNER = new OwnerId(SERVER, "server");

    /** Where team ids and members come from. */
    public interface TeamProvider {

        /** The team key of {@code player}; the player's own UUID when they are in no team. */
        String teamKey(UUID player, MinecraftServer server);

        /** Every member of the team with this key, online or not. */
        Collection<UUID> members(String teamKey, MinecraftServer server);
    }

    /** A team of one per player: the fallback without a team mod (D31). */
    public static final TeamProvider SOLO = new TeamProvider() {
        @Override
        public String teamKey(UUID player, MinecraftServer server) {
            return player.toString();
        }

        @Override
        public Collection<UUID> members(String teamKey, MinecraftServer server) {
            try {
                return List.of(UUID.fromString(teamKey));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
    };

    private static volatile TeamProvider teams = SOLO;

    private BuiltInOwners() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterOwnerTypesEvent.class, e -> {
            e.register(SERVER, new ServerOwner());
            e.register(TEAM, new TeamOwner());
        });
    }

    /** Replaces the team-of-one fallback, e.g. with FTB Teams. */
    public static void setTeamProvider(TeamProvider provider) {
        teams = provider;
    }

    public static TeamProvider teamProvider() {
        return teams;
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

    static final class TeamOwner implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(new OwnerId(TEAM, teams.teamKey(player.getUUID(), player.server)));
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            return teams.members(owner.key(), server);
        }

        @Override
        public boolean individualStages() {
            return true;
        }
    }
}
