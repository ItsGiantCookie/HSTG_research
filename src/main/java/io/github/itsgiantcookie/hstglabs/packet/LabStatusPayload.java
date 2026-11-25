package io.github.itsgiantcookie.hstglabs.packet;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.client.ClientResearch;
import io.github.itsgiantcookie.hstglabs.core.IdleReason;
import io.github.itsgiantcookie.hstglabs.core.LabCycle;
import io.github.itsgiantcookie.hstglabs.core.SiteScheduler;
import io.github.itsgiantcookie.hstglabs.core.WorkerSlot;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.tech.Technology;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * S → C (docs/design.md §6.3 {@code LabStatus}): what an open lab screen shows besides its slots:
 * energy, tier, whether the lab researches for the viewer's owner, and per worker slot the unit it
 * runs or the reason it is idle (§4.4). Sent by the open menu every few ticks.
 *
 * @param ownedByViewer false when the lab belongs to another owner; the screen then hides queue editing
 */
public record LabStatusPayload(int containerId, long energy, long capacity, long usage, int tier, double speed, boolean ownedByViewer,
      List<SlotView> slots) implements CustomPacketPayload {

    public static final Type<LabStatusPayload> TYPE = new Type<>(HstgLabs.rl("lab_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LabStatusPayload> STREAM_CODEC = StreamCodec.of(LabStatusPayload::write,
          LabStatusPayload::read);

    public LabStatusPayload {
        slots = List.copyOf(slots);
    }

    /**
     * One worker slot.
     *
     * @param technology the unit it runs, if any
     * @param ticksLeft  of that unit
     * @param ticksTotal of that unit at the current speed (never below {@code ticksLeft})
     * @param state      {@link SiteScheduler.SlotState} name, empty before the first tick
     * @param reason     idle reason id ({@code hstg_labs:no_power}, an addon's id, …), empty when running
     * @param detail     idle reason detail (needed tier, missing ingredient, …)
     */
    public record SlotView(Optional<ResourceLocation> technology, long ticksLeft, long ticksTotal, String state, String reason, String detail) {

        static final StreamCodec<RegistryFriendlyByteBuf, SlotView> STREAM_CODEC = StreamCodec.composite(
              ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), SlotView::technology,
              ByteBufCodecs.VAR_LONG, SlotView::ticksLeft,
              ByteBufCodecs.VAR_LONG, SlotView::ticksTotal,
              ByteBufCodecs.STRING_UTF8, SlotView::state,
              ByteBufCodecs.STRING_UTF8, SlotView::reason,
              ByteBufCodecs.STRING_UTF8, SlotView::detail,
              SlotView::new);

        /** 0..1 through the running unit. */
        public double unitProgress() {
            return technology.isEmpty() || ticksTotal <= 0 ? 0 : 1D - (double) ticksLeft / ticksTotal;
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, LabStatusPayload p) {
        buf.writeVarInt(p.containerId);
        buf.writeVarLong(p.energy);
        buf.writeVarLong(p.capacity);
        buf.writeVarLong(p.usage);
        buf.writeVarInt(p.tier);
        buf.writeDouble(p.speed);
        buf.writeBoolean(p.ownedByViewer);
        SlotView.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.slots);
    }

    private static LabStatusPayload read(RegistryFriendlyByteBuf buf) {
        return new LabStatusPayload(buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readDouble(),
              buf.readBoolean(), SlotView.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
    }

    @Override
    public Type<LabStatusPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, (payload, context) -> ClientResearch.acceptLab(payload));
    }

    /** What {@code viewer} sees of {@code lab}. Server thread. */
    public static LabStatusPayload of(int containerId, BasicLabBlockEntity lab, ServerPlayer viewer) {
        boolean own = ResearchManager.get().map(m -> lab.owner().map(o -> o.equals(m.ownerOf(viewer))).orElse(false)).orElse(false);
        List<WorkerSlot<ResourceLocation, Technology.Ingredient>> workers = lab.workerSlots();
        List<SiteScheduler.SlotStatus> status = lab.status();
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            LabCycle<ResourceLocation, Technology.Ingredient> cycle = workers.get(i).cycle();
            Optional<ResourceLocation> tech = cycle.isRunning() ? cycle.tech() : Optional.empty();
            long left = tech.isPresent() ? cycle.ticksLeft() : 0;
            long total = tech.flatMap(ResearchManager::technologyById).map(t -> t.cost().unitTicks(lab.speed(t.id()))).orElse(0L);
            SiteScheduler.SlotStatus s = i < status.size() ? status.get(i) : null;
            IdleReason reason = s == null ? null : s.reason();
            slots.add(new SlotView(tech, left, Math.max(left, total), s == null ? "" : s.state().name(), reason == null ? "" : reason.id(),
                  reason == null ? "" : reason.detail()));
        }
        int tier = lab.type().map(t -> t.tier()).orElse(0);
        return new LabStatusPayload(containerId, lab.energy(), lab.energyCapacity(), lab.energyPerTick(), tier, lab.speed(null), own, slots);
    }
}
