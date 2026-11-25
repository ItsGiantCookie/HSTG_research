package io.github.itsgiantcookie.hstglabs.client;

import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.QueueView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TierView;
import io.github.itsgiantcookie.hstglabs.packet.TechSnapshotPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's copy of the last research snapshot (docs/design.md §6.1 {@code ClientResearchCache},
 * §6.3). Screens read it; nothing here decides anything.
 */
public final class ClientResearch {

    private static volatile List<TechView> technologies = List.of();
    private static volatile QueueView queue = QueueView.EMPTY;
    private static volatile int revision;

    private ClientResearch() {
    }

    public static void accept(TechSnapshotPayload payload) {
        technologies = payload.technologies();
        queue = payload.queue();
        tiers = payload.tiers();
        revision++;
    }

    public static List<TechView> technologies() {
        return technologies;
    }

    public static Optional<TechView> technology(ResourceLocation id) {
        return technologies.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public static QueueView queue() {
        return queue;
    }

    /** Changes with every snapshot, so screens know when to rebuild their lists. */
    public static int revision() {
        return revision;
    }

    public static void clear() {
        technologies = List.of();
        queue = QueueView.EMPTY;
        lab = null;
        revision++;
    }

    private static volatile LabStatusPayload lab;
    private static volatile List<TierView> tiers = List.of();

    /** The tier label for {@code level}, "Tier N" when the server has none (D28). */
    public static TierView tier(int level) {
        return tiers.stream().filter(t -> t.level() == level).findFirst().orElseGet(() -> TierView.fallback(level));
    }

    public static void acceptLab(LabStatusPayload payload) {
        lab = payload;
    }

    /** The last status of the lab whose menu has this container id. */
    public static Optional<LabStatusPayload> lab(int containerId) {
        LabStatusPayload l = lab;
        return l != null && l.containerId() == containerId ? Optional.of(l) : Optional.empty();
    }
}
