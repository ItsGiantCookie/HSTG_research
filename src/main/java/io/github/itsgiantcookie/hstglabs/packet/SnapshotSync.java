package io.github.itsgiantcookie.hstglabs.packet;

import io.github.itsgiantcookie.hstglabs.api.event.ResearchEvent;
import io.github.itsgiantcookie.hstglabs.api.tech.TechDefinitionsReloadedEvent;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * When clients get a fresh {@link TechSnapshotPayload} (docs/design.md §6.3): on login, after every
 * definitions reload, and when an owner's queue or research changes (to that owner's online members).
 * Stage changes reach clients through the definitions reload HS triggers and through these events.
 */
public final class SnapshotSync {

    private SnapshotSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                TechSnapshotPayload.send(java.util.List.of(player));
            }
        });
        NeoForge.EVENT_BUS.addListener(TechDefinitionsReloadedEvent.class, e -> sendAll());
        NeoForge.EVENT_BUS.addListener(ResearchEvent.QueueChanged.class, SnapshotSync::toOwner);
        NeoForge.EVENT_BUS.addListener(ResearchEvent.UnitCompleted.class, SnapshotSync::toOwner);
        NeoForge.EVENT_BUS.addListener(ResearchEvent.Completed.class, e -> sendAll()); // a stage changed for everyone who sees it
        NeoForge.EVENT_BUS.addListener(ResearchEvent.Blocked.class, SnapshotSync::toOwner);
    }

    private static void toOwner(ResearchEvent e) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ResearchManager.get().ifPresent(m -> TechSnapshotPayload.sendToOwner(m, e.owner(), server.getPlayerList().getPlayers()));
        }
    }

    /** Every online player gets a fresh snapshot. */
    public static void sendAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TechSnapshotPayload.send(server.getPlayerList().getPlayers());
        }
    }
}
