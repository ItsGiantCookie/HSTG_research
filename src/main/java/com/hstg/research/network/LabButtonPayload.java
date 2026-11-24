package com.hstg.research.network;

import com.hstg.research.HstgResearch;
import com.hstg.research.block.ResearchLabBlockEntity;
import com.hstg.research.config.TransmissionKind;
import com.hstg.research.menu.ResearchLabMenu;
import com.hstg.research.upgrade.UpgradeComponent;
import io.netty.buffer.ByteBuf;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** GUI window buttons (Mekanism's PacketGuiInteract equivalents): auto-eject toggle, upgrade uninstall. */
public record LabButtonPayload(BlockPos pos, Button button, int value, boolean shift) implements CustomPacketPayload {

    public enum Button {
        /** value = TransmissionKind ordinal */
        TOGGLE_EJECT,
        /** value = Upgrade ordinal, shift = remove all */
        REMOVE_UPGRADE
    }

    public static final Type<LabButtonPayload> TYPE = new Type<>(HstgResearch.rl("lab_button"));
    private static final StreamCodec<ByteBuf, Button> BUTTON_CODEC =
          ByteBufCodecs.idMapper(i -> Button.values()[Math.floorMod(i, Button.values().length)], Button::ordinal);
    public static final StreamCodec<ByteBuf, LabButtonPayload> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, LabButtonPayload::pos,
          BUTTON_CODEC, LabButtonPayload::button,
          ByteBufCodecs.VAR_INT, LabButtonPayload::value,
          ByteBufCodecs.BOOL, LabButtonPayload::shift,
          LabButtonPayload::new);

    @Override
    public Type<LabButtonPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, LabButtonPayload::handle);
    }

    private static void handle(LabButtonPayload payload, IPayloadContext context) {
        if (!(context.player().containerMenu instanceof ResearchLabMenu menu) || !menu.getPos().equals(payload.pos())
            || !menu.stillValid(context.player()) || menu.getLab() == null) {
            return;
        }
        ResearchLabBlockEntity lab = menu.getLab();
        switch (payload.button()) {
            case TOGGLE_EJECT -> {
                if (payload.value() >= 0 && payload.value() < TransmissionKind.values().length) {
                    lab.toggleEject(TransmissionKind.values()[payload.value()]);
                }
            }
            case REMOVE_UPGRADE -> {
                if (payload.value() >= 0 && payload.value() < Upgrade.values().length) {
                    Upgrade upgrade = Upgrade.values()[payload.value()];
                    if (UpgradeComponent.SUPPORTED.contains(upgrade)) {
                        lab.removeUpgrade(upgrade, payload.shift());
                    }
                }
            }
        }
    }
}
