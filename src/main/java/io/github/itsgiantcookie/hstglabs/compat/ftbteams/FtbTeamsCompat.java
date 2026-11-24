package io.github.itsgiantcookie.hstglabs.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import io.github.itsgiantcookie.hstglabs.api.HSLabsAPI;
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
 * FTB Teams as research owners (docs/design.md §5.10, D31), built on our public api only. Loaded only
 * when FTB Teams is installed; then it provides {@code hstg_labs:team}.
 *
 * <p>The key is FTB's effective team id ({@code Team.getTeamId()}): the party's id while the player is
 * in a party, otherwise the player's own UUID. So joining a party adopts the party's queue and progress,
 * leaving it returns to the player's own, and a world that adds FTB Teams later keeps every solo key.
 * Facts: docs/ftb-teams-facts.md.
 */
public final class FtbTeamsCompat {

    public static final String MOD_ID = "ftbteams";
    public static final ResourceLocation TEAM = ResourceLocation.fromNamespaceAndPath(HSLabsAPI.MOD_ID, "team");

    private FtbTeamsCompat() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterOwnerTypesEvent.class, e -> e.register(TEAM, new FtbTeamOwner()));
    }

    /** The research owner of a player: their FTB team. */
    static final class FtbTeamOwner implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(new OwnerId(TEAM, teamKey(player.getUUID())));
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            UUID id;
            try {
                id = UUID.fromString(owner.key());
            } catch (IllegalArgumentException e) {
                return List.of();
            }
            if (!FTBTeamsAPI.api().isManagerLoaded()) {
                return List.of(id);
            }
            return FTBTeamsAPI.api().getManager().getTeamByID(id).<Collection<UUID>>map(Team::getMembers).orElse(List.of(id));
        }

        @Override
        public boolean individualStages() {
            return true;
        }

        private static String teamKey(UUID player) {
            if (!FTBTeamsAPI.api().isManagerLoaded()) {
                return player.toString();
            }
            return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player).map(t -> t.getTeamId().toString()).orElse(player.toString());
        }
    }
}
