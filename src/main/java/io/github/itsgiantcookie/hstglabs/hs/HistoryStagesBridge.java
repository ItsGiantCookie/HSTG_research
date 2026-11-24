package io.github.itsgiantcookie.hstglabs.hs;

import io.github.itsgiantcookie.hstglabs.research.StageBridge;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The one place research talks to History Stages (docs/design.md §6.1, §7.1). Facts with
 * {@code path:line}: docs/hs-api-facts.md ("Stages and state", "Events").
 */
public final class HistoryStagesBridge implements StageBridge {

    public static final HistoryStagesBridge INSTANCE = new HistoryStagesBridge();

    /** Last {@code StageEvent.Unlocked} cause per stage, for GameTests and {@code /hslabs debug}. */
    private static final Map<String, String> LAST_UNLOCK_CAUSE = new ConcurrentHashMap<>();
    /** Players HS reported an individual unlock for, per stage (online or offline), for GameTests. */
    private static final Map<String, Set<UUID>> INDIVIDUAL_UNLOCKS = new ConcurrentHashMap<>();
    /** Stages whose next {@code BeforeUnlock} a GameTest wants vetoed (setup only). */
    private static final Set<String> VETO_NEXT = ConcurrentHashMap.newKeySet();

    private HistoryStagesBridge() {
    }

    /** Forwards HS stage events to {@code listener}. Call once at mod construction. */
    public static void listen(StageBridge.Listener listener) {
        NeoForge.EVENT_BUS.addListener(StageEvent.Unlocked.class, e -> {
            LAST_UNLOCK_CAUSE.put(e.getStageId(), e.getCause().name());
            listener.unlocked(e.getStageId(), Optional.empty());
        });
        NeoForge.EVENT_BUS.addListener(StageEvent.IndividualUnlocked.class, e -> {
            INDIVIDUAL_UNLOCKS.computeIfAbsent(e.getStageId(), s -> ConcurrentHashMap.newKeySet()).add(e.getPlayerUUID());
            listener.unlocked(e.getStageId(), Optional.of(e.getPlayerUUID()));
        });
        NeoForge.EVENT_BUS.addListener(StageEvent.Locked.class, e -> listener.locked(e.getStageId(), Optional.empty()));
        NeoForge.EVENT_BUS.addListener(StageEvent.IndividualLocked.class, e -> listener.locked(e.getStageId(), Optional.of(e.getPlayerUUID())));
        NeoForge.EVENT_BUS.addListener(StageEvent.LostForGood.class,
              e -> listener.lost(e.getStageId(), e.isGlobal() ? Optional.empty() : Optional.ofNullable(e.getPlayerUUID())));
        NeoForge.EVENT_BUS.addListener(StageEvent.BeforeUnlock.class, e -> {
            if (VETO_NEXT.remove(e.getStageId())) {
                e.setCanceled(true);
            }
        });
    }

    @Override
    public Optional<Scope> scopeOf(String stage) {
        StageScope scope = StageStates.scopeOf(stage);
        if (scope == null) {
            return Optional.empty();
        }
        return Optional.of(scope == StageScope.GLOBAL ? Scope.GLOBAL : Scope.INDIVIDUAL);
    }

    @Override
    public boolean isUnlockedGlobal(String stage, MinecraftServer server) {
        return StageStates.isUnlockedGlobal(stage, server.overworld());
    }

    @Override
    public boolean isUnlockedIndividual(String stage, UUID player, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online != null && StageStates.isUnlockedIndividual(stage, online);
    }

    @Override
    public boolean unlockGlobal(String stage, MinecraftServer server) {
        return StageStates.unlockGlobal(stage, server.overworld());
    }

    @Override
    public boolean unlockIndividual(String stage, UUID player, MinecraftServer server) {
        return StageStates.unlockIndividual(stage, player, server);
    }

    // ---- GameTest setup only (CLAUDE.md: admin paths only in command/ and GameTest setup) ----

    /** The cause of the last global unlock of {@code stage}, e.g. {@code ADDON}. */
    public static Optional<String> lastUnlockCause(String stage) {
        return Optional.ofNullable(LAST_UNLOCK_CAUSE.get(stage));
    }

    /** Whether HS reported an individual unlock of {@code stage} for {@code player} (online or offline) since start. */
    public static boolean individualUnlockReported(String stage, UUID player) {
        return INDIVIDUAL_UNLOCKS.getOrDefault(stage, Set.of()).contains(player);
    }

    /** GameTest setup: relock a global stage so a test starts from a known state. */
    public static void relockGlobalForTest(String stage, MinecraftServer server) {
        StageStates.relockGlobal(stage, server.overworld());
    }

    /** GameTest setup: veto the next unlock of {@code stage}, as another addon's BeforeUnlock listener would. */
    public static void vetoNextUnlockForTest(String stage) {
        VETO_NEXT.add(stage);
    }
}
