package io.github.itsgiantcookie.hstglabs.packet;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu;
import io.github.itsgiantcookie.hstglabs.mek.config.TransmissionKind;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import mekanism.api.RelativeSide;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * C → S, from the lab's side configuration window: cycle one face of one kind, or every face of that
 * kind when {@code side} is empty (Mekanism's "clear sides"). Only applies to the lab whose menu the
 * player has open.
 */
public record SideConfigPayload(int containerId, TransmissionKind kind, Optional<RelativeSide> side, boolean forward)
      implements CustomPacketPayload {

    public static final Type<SideConfigPayload> TYPE = new Type<>(HstgLabs.rl("side_config"));
    private static final StreamCodec<ByteBuf, TransmissionKind> KIND_CODEC = ByteBufCodecs.idMapper(
          i -> TransmissionKind.values()[Math.floorMod(i, TransmissionKind.values().length)], TransmissionKind::ordinal);
    public static final StreamCodec<ByteBuf, SideConfigPayload> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.VAR_INT, SideConfigPayload::containerId,
          KIND_CODEC, SideConfigPayload::kind,
          ByteBufCodecs.optional(RelativeSide.STREAM_CODEC), SideConfigPayload::side,
          ByteBufCodecs.BOOL, SideConfigPayload::forward,
          SideConfigPayload::new);

    @Override
    public Type<SideConfigPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, (payload, context) -> apply(context.player(), payload));
    }

    /** Server thread. False when the player has no matching lab menu open or the kind is not configurable. */
    public static boolean apply(Player player, SideConfigPayload p) {
        if (!(player.containerMenu instanceof BasicLabMenu menu) || menu.containerId != p.containerId() || !menu.stillValid(player)
            || menu.lab().isEmpty() || !BasicLabBlockEntity.SIDE_KINDS.contains(p.kind())) {
            return false;
        }
        menu.lab().get().cycleFace(p.kind(), p.side().orElse(null), p.forward());
        return true;
    }
}
