package de.germanrangers.hstglabs.mek;

import de.germanrangers.hstglabs.mek.config.TransmissionKind;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Mekanism lookups by registry id, so only Mekanism's public API is compiled against. */
public final class MekanismCompat {

    private static final ResourceLocation CONFIGURATOR = ResourceLocation.fromNamespaceAndPath("mekanism", "configurator");
    /** Data component holding ItemConfigurator.ConfiguratorMode (an internal enum, read by name). */
    private static final ResourceLocation CONFIGURATOR_MODE = ResourceLocation.fromNamespaceAndPath("mekanism", "configurator_mode");

    private MekanismCompat() {
    }

    public static boolean isConfigurator(ItemStack stack) {
        return !stack.isEmpty() && stack.is(BuiltInRegistries.ITEM.get(CONFIGURATOR));
    }

    /** The held configurator (main hand first), or empty. */
    public static ItemStack heldConfigurator(Player player) {
        if (isConfigurator(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        return isConfigurator(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    /**
     * Which of our side-config tabs a configurator mode edits: "configurate items" and "configurate
     * energy" map 1:1; the remaining configurate modes (fluids, chemicals, heat) edit Data, which
     * Mekanism has no mode for. Null when the configurator is not in a configurate mode.
     */
    public static TransmissionKind configuratorKind(ItemStack stack) {
        if (!isConfigurator(stack)) {
            return null;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(CONFIGURATOR_MODE);
        // a configurator without the component is in its default mode (configurate items)
        Object mode = type == null ? null : stack.get(type);
        String name = mode instanceof Enum<?> e ? e.name() : "CONFIGURATE_ITEMS";
        return switch (name) {
            case "CONFIGURATE_ITEMS" -> TransmissionKind.ITEM;
            case "CONFIGURATE_ENERGY" -> TransmissionKind.ENERGY;
            case "CONFIGURATE_FLUIDS", "CONFIGURATE_CHEMICALS", "CONFIGURATE_HEAT" -> TransmissionKind.DATA;
            default -> null;
        };
    }

    public static TransmissionKind configuratorKind(Player player) {
        TransmissionKind kind = configuratorKind(heldConfigurator(player));
        return kind == null ? TransmissionKind.ITEM : kind;
    }

    /** Mekanism GUI texture (assets/mekanism/gui/...). Mekanism is a required dependency. */
    public static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", "gui/" + path);
    }
}
