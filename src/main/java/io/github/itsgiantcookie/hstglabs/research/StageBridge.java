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

    enum Scope {
        GLOBAL,
        INDIVIDUAL
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
