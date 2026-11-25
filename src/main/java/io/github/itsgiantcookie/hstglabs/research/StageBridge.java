package io.github.itsgiantcookie.hstglabs.research;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * What research needs from History Stages (docs/design.md §6.1: research sees only this interface;
 * the hs package implements it). Server thread.
 */
public interface StageBridge {

    /** Global, individual, or empty if HS has no such stage. */
    Optional<Scope> scopeOf(String stage);

    boolean isUnlockedGlobal(String stage, MinecraftServer server);

    /** Whether an online player has an individual stage; false when they are offline. */
    boolean isUnlockedIndividual(String stage, UUID player, MinecraftServer server);

    /** HS's unlock with its checks (logic, lost, vetoes); true if newly unlocked. Cause {@code ADDON}. */
    boolean unlockGlobal(String stage, MinecraftServer server);

    /** As {@link #unlockGlobal} for one player, online or offline (offline: synced at next login). */
    boolean unlockIndividual(String stage, UUID player, MinecraftServer server);

    /**
     * Whether {@code player} may queue research for {@code stage} (§4.3, CLAUDE.md): HS's graph says
     * REACHABLE and the stage's other requirements are met. {@code unlockGlobal} does not check
     * dependency groups, so this is our gate.
     */
    QueueCheck canQueue(String stage, net.minecraft.server.level.ServerPlayer player);

    enum Scope {
        GLOBAL,
        INDIVIDUAL
    }

    /** Outcome of {@link #canQueue}. */
    enum QueueCheck {
        OK,
        UNKNOWN_STAGE,
        ALREADY_UNLOCKED,
        /** Prerequisite stages are missing (HS graph: LOCKED or BLOCKED). */
        PREREQUISITES_MISSING,
        /** Other requirements (items, XP, …) are not met. */
        REQUIREMENTS_UNMET
    }

    /** Stage changes HS reports, forwarded by the hs package. */
    interface Listener {

        /** A stage was unlocked; {@code player} is set for an individual stage. */
        void unlocked(String stage, Optional<UUID> player);

        /** A stage was relocked. */
        void locked(String stage, Optional<UUID> player);

        /** A stage was lost for good. */
        void lost(String stage, Optional<UUID> player);
    }
}
