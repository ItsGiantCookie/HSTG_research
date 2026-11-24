package de.germanrangers.hstglabs.mek.config;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.mek.MekanismCompat;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * The side-config categories of the lab, like Mekanism's TransmissionType tabs. Each face has one
 * {@link PortType} per kind, so a face can e.g. take items and energy at the same time.
 */
public enum TransmissionKind implements StringRepresentable {
    // Mekanism SpecialColors.TAB_ITEM_CONFIG / TAB_ENERGY_CONFIG; data uses semantic.data.gui (teal)
    ITEM("item", 0xFFCFCFCF, MekanismCompat.gui("items.png"), "items_overlay",
          List.of(PortType.NONE, PortType.INPUT, PortType.OUTPUT, PortType.INPUT_OUTPUT, PortType.ENERGY)),
    ENERGY("energy", 0xFF59C15F, MekanismCompat.gui("energy.png"), "energy_overlay",
          List.of(PortType.NONE, PortType.INPUT)),
    DATA("data", 0xFF2FD3B4, HstgLabs.rl("textures/gui/data.png"), "data_overlay",
          List.of(PortType.NONE, PortType.OUTPUT));

    /** semantic.data.gui: data bar fill, data tab tint, data icon accents. */
    public static final int DATA_COLOR = 0xFF2FD3B4;

    private final String name;
    private final int tabColor;
    private final ResourceLocation icon;
    private final ResourceLocation overlay;
    private final List<PortType> supported;

    TransmissionKind(String name, int tabColor, ResourceLocation icon, String overlay, List<PortType> supported) {
        this.name = name;
        this.tabColor = tabColor;
        this.icon = icon;
        this.overlay = HstgLabs.rl("block/overlay/" + overlay);
        this.supported = supported;
    }

    public int tabColor() {
        return tabColor;
    }

    /** 18x18 GUI icon, drawn on the type tab like Mekanism's GuiConfigTypeTab. */
    public ResourceLocation icon() {
        return icon;
    }

    /** Block-atlas sprite for the in-world Configurator overlay. */
    public ResourceLocation overlay() {
        return overlay;
    }

    public List<PortType> supported() {
        return supported;
    }

    public PortType shift(PortType current, boolean forward) {
        int index = Math.max(0, supported.indexOf(current));
        int size = supported.size();
        return supported.get((index + (forward ? 1 : size - 1)) % size);
    }

    public Component displayName() {
        return Component.translatable("hstg_labs.transmission." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
