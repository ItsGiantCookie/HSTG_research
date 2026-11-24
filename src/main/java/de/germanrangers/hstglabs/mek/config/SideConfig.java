package de.germanrangers.hstglabs.mek.config;

import java.util.EnumMap;
import java.util.EnumSet;
import mekanism.api.RelativeSide;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-face port configuration per transmission kind, stored relative to the machine's facing
 * (Mekanism's RelativeSide), like Mekanism's TileComponentConfig.
 */
public final class SideConfig {

    private final EnumMap<TransmissionKind, EnumMap<RelativeSide, PortType>> config = new EnumMap<>(TransmissionKind.class);
    /** Mekanism's per-type auto-eject toggle (off by default). Only items can toggle it. */
    private final EnumSet<TransmissionKind> ejecting = EnumSet.noneOf(TransmissionKind.class);

    public SideConfig() {
        for (TransmissionKind kind : TransmissionKind.values()) {
            EnumMap<RelativeSide, PortType> faces = new EnumMap<>(RelativeSide.class);
            for (RelativeSide side : RelativeSide.values()) {
                faces.put(side, PortType.NONE);
            }
            config.put(kind, faces);
        }
        config.get(TransmissionKind.ITEM).put(RelativeSide.LEFT, PortType.INPUT);
        config.get(TransmissionKind.ITEM).put(RelativeSide.BOTTOM, PortType.OUTPUT);
        config.get(TransmissionKind.ENERGY).put(RelativeSide.BACK, PortType.INPUT);
        config.get(TransmissionKind.DATA).put(RelativeSide.RIGHT, PortType.OUTPUT);
    }

    /** Items eject only when toggled on; data is always pushed out; energy never ejects. */
    public boolean isEjecting(TransmissionKind kind) {
        return kind == TransmissionKind.DATA || ejecting.contains(kind);
    }

    public static boolean canToggleEject(TransmissionKind kind) {
        return kind == TransmissionKind.ITEM;
    }

    public void toggleEject(TransmissionKind kind) {
        if (canToggleEject(kind) && !ejecting.remove(kind)) {
            ejecting.add(kind);
        }
    }

    public PortType get(TransmissionKind kind, RelativeSide side) {
        return config.get(kind).get(side);
    }

    public boolean isUsed(TransmissionKind kind, PortType type) {
        return config.get(kind).containsValue(type);
    }

    public PortType cycle(TransmissionKind kind, RelativeSide side, boolean forward) {
        PortType next = kind.shift(get(kind, side), forward);
        config.get(kind).put(side, next);
        return next;
    }

    /**
     * Mekanism's "clear sides" button: if every face has the same type, shift them all together;
     * otherwise reset them all to none.
     */
    public void cycleAll(TransmissionKind kind, boolean forward) {
        EnumMap<RelativeSide, PortType> faces = config.get(kind);
        PortType common = null;
        boolean same = true;
        for (PortType type : faces.values()) {
            if (common == null) {
                common = type;
            } else if (common != type) {
                same = false;
                break;
            }
        }
        PortType target = same && common != null ? kind.shift(common, forward) : PortType.NONE;
        faces.replaceAll((side, type) -> target);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        config.forEach((kind, faces) -> {
            CompoundTag kindTag = new CompoundTag();
            faces.forEach((side, type) -> kindTag.putString(side.getSerializedName(), type.getSerializedName()));
            tag.put(kind.getSerializedName(), kindTag);
        });
        CompoundTag eject = new CompoundTag();
        ejecting.forEach(kind -> eject.putBoolean(kind.getSerializedName(), true));
        tag.put("eject", eject);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains(RelativeSide.FRONT.getSerializedName(), CompoundTag.TAG_STRING)) {
            loadLegacy(tag);
            return;
        }
        ejecting.clear();
        CompoundTag eject = tag.getCompound("eject");
        for (TransmissionKind kind : TransmissionKind.values()) {
            if (canToggleEject(kind) && eject.getBoolean(kind.getSerializedName())) {
                ejecting.add(kind);
            }
        }
        for (TransmissionKind kind : TransmissionKind.values()) {
            if (!tag.contains(kind.getSerializedName(), CompoundTag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag kindTag = tag.getCompound(kind.getSerializedName());
            EnumMap<RelativeSide, PortType> faces = config.get(kind);
            for (RelativeSide side : RelativeSide.values()) {
                if (kindTag.contains(side.getSerializedName())) {
                    PortType type = PortType.byName(kindTag.getString(side.getSerializedName()));
                    faces.put(side, kind.supported().contains(type) ? type : PortType.NONE);
                }
            }
        }
    }

    /** Pre-split saves stored one face type per side ("item", "energy", "data"). */
    private void loadLegacy(CompoundTag tag) {
        config.values().forEach(faces -> faces.replaceAll((side, type) -> PortType.NONE));
        for (RelativeSide side : RelativeSide.values()) {
            switch (tag.getString(side.getSerializedName())) {
                case "item" -> config.get(TransmissionKind.ITEM).put(side, PortType.INPUT_OUTPUT);
                case "energy" -> config.get(TransmissionKind.ENERGY).put(side, PortType.INPUT);
                case "data" -> config.get(TransmissionKind.DATA).put(side, PortType.OUTPUT);
                default -> {
                }
            }
        }
    }
}
