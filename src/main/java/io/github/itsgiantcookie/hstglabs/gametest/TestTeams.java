package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerType;
import io.github.itsgiantcookie.hstglabs.api.owner.RegisterOwnerTypesEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/**
 * A team owner type whose membership GameTests set directly ({@code hstg_labs:gametest_team}). Team
 * behaviour (independent teams, adopting a team's progress on joining, offline members) is ours, so
 * it is tested with teams we control; real FTB parties need logged-in players, which mock players
 * are not. Registered only in dev runs with GameTests enabled.
 */
public final class TestTeams {

    public static final ResourceLocation TYPE = HstgLabs.rl("gametest_team");

    private static final Map<String, Set<UUID>> TEAMS = new ConcurrentHashMap<>();

    private TestTeams() {
    }

    public static boolean enabled() {
        return System.getProperty("neoforge.enabledGameTestNamespaces") != null;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterOwnerTypesEvent.class, e -> e.register(TYPE, new Owner()));
    }

    /** Creates or replaces a team; returns its owner id. */
    public static OwnerId team(String key, UUID... members) {
        TEAMS.put(key, ConcurrentHashMap.newKeySet());
        for (UUID m : members) {
            join(key, m);
        }
        return new OwnerId(TYPE, key);
    }

    /** Moves {@code player} into team {@code key}, out of any other. */
    public static void join(String key, UUID player) {
        TEAMS.values().forEach(t -> t.remove(player));
        TEAMS.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(player);
    }

    public static OwnerType ownerType() {
        return new Owner();
    }

    /** What {@link Owner#ownerOf} answers for a player with this UUID: their team, or a team of one. */
    public static OwnerId ownerOf(UUID player) {
        return new OwnerId(TYPE, TEAMS.entrySet().stream().filter(e -> e.getValue().contains(player))
              .map(Map.Entry::getKey).findFirst().orElse(player.toString()));
    }

    static final class Owner implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(TestTeams.ownerOf(player.getUUID()));
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            Set<UUID> members = TEAMS.get(owner.key());
            if (members != null) {
                return List.copyOf(members);
            }
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
