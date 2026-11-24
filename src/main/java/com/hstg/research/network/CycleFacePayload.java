package com.hstg.research.network;

import com.hstg.research.HstgResearch;
import com.hstg.research.config.TransmissionKind;
import com.hstg.research.menu.ResearchLabMenu;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import mekanism.api.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * GUI side-config click: cycle one relative face for one transmission kind, or (side empty) all
 * faces of that kind, like Mekanism's "clear sides" button.
 */
public record CycleFacePayload(BlockPos pos, TransmissionKind kind, Optional<RelativeSide> side, boolean forward) implements CustomPacketPayload {

    public static final Type<CycleFacePayload> TYPE = new Type<>(HstgResearch.rl("cycle_face"));
    private static final StreamCodec<ByteBuf, TransmissionKind> KIND_CODEC =
          ByteBufCodecs.idMapper(i -> TransmissionKind.values()[Math.floorMod(i, TransmissionKind.values().length)], TransmissionKind::ordinal);
    public static final StreamCodec<ByteBuf, CycleFacePayload> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, CycleFacePayload::pos,
          KIND_CODEC, CycleFacePayload::kind,
          ByteBufCodecs.optional(RelativeSide.STREAM_CODEC), CycleFacePayload::side,
          ByteBufCodecs.BOOL, CycleFacePayload::forward,
          CycleFacePayload::new);

    @Override
    public Type<CycleFacePayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, CycleFacePayload::handle);
    }

    private static void handle(CycleFacePayload payload, IPayloadContext context) {
        // Only accept changes for the lab whose menu this player has open (validated server side).
        if (context.player().containerMenu instanceof ResearchLabMenu menu && menu.getPos().equals(payload.pos())
            && menu.stillValid(context.player()) && menu.getLab() != null) {
            if (payload.side().isPresent()) {
                menu.getLab().cycleFace(payload.kind(), payload.side().get(), payload.forward());
            } else {
                menu.getLab().cycleAllFaces(payload.kind(), payload.forward());
            }
        }
    }
}
