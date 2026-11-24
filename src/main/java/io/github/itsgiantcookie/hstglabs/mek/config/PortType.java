package io.github.itsgiantcookie.hstglabs.mek.config;

import mekanism.api.text.EnumColor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** What a face does for one transmission type. Mirrors Mekanism's DataType names and colors. */
public enum PortType implements StringRepresentable {
    NONE("none", EnumColor.GRAY),
    INPUT("input", EnumColor.DARK_RED),
    OUTPUT("output", EnumColor.DARK_BLUE),
    INPUT_OUTPUT("input_output", EnumColor.PURPLE),
    ENERGY("energy", EnumColor.DARK_GREEN);

    private final String name;
    private final EnumColor color;

    PortType(String name, EnumColor color) {
        this.name = name;
        this.color = color;
    }

    public EnumColor color() {
        return color;
    }

    public int argb(float alpha) {
        int[] rgb = color.getRgbCode();
        return ((int) (alpha * 255) << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    public Component displayName() {
        return Component.translatable("hstg_labs.port." + name).withStyle(style -> style.withColor(color.getColor()));
    }

    public static PortType byName(String name) {
        for (PortType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
