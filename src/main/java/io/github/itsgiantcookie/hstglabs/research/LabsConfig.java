package io.github.itsgiantcookie.hstglabs.research;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code hstg_labs-server.toml} (docs/design.md §8.4). WP-08 adds the research keys; the rest of the
 * table follows with the features that read them.
 */
public final class LabsConfig {

    public enum QueuePermission {
        ANYONE,
        OPS
    }

    public enum OnRelock {
        RESET,
        KEEP
    }

    public enum OwnerModel {
        TEAM,
        SERVER
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue QUEUE_MAX;
    public static final ModConfigSpec.EnumValue<QueuePermission> QUEUE_PERMISSION;
    public static final ModConfigSpec.EnumValue<OnRelock> ON_RELOCK;
    public static final ModConfigSpec.EnumValue<OwnerModel> OWNER_MODEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        QUEUE_MAX = b.comment("Length of each research queue (§4.3).").defineInRange("queue_max", 7, 1, 64);
        QUEUE_PERMISSION = b.comment("Who may edit a queue: ANYONE in the owning team, or OPS only.")
              .defineEnum("queue_permission", QueuePermission.ANYONE);
        ON_RELOCK = b.comment("When a researched stage is relocked: RESET its progress, or KEEP it so queueing it again re-unlocks it (D5).")
              .defineEnum("on_relock", OnRelock.RESET);
        OWNER_MODEL = b.comment("Who research belongs to: TEAM (FTB Teams team, or a team of one) or one SERVER-wide owner (D2).")
              .defineEnum("owner_model", OwnerModel.TEAM);
        SPEC = b.build();
    }

    private LabsConfig() {
    }

    /** Values while no server config is loaded yet (tests before world load): the defaults. */
    static int queueMax() {
        return SPEC.isLoaded() ? QUEUE_MAX.get() : QUEUE_MAX.getDefault();
    }

    static OnRelock onRelock() {
        return SPEC.isLoaded() ? ON_RELOCK.get() : ON_RELOCK.getDefault();
    }

    static OwnerModel ownerModel() {
        return SPEC.isLoaded() ? OWNER_MODEL.get() : OWNER_MODEL.getDefault();
    }
}
