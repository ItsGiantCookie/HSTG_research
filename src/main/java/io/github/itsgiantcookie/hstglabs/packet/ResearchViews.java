package io.github.itsgiantcookie.hstglabs.packet;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What clients are told about research (docs/design.md §6.3): plain records the server fills and the
 * client only renders. The client never decides; every value here was computed on the server for that
 * player.
 */
public final class ResearchViews {

    private ResearchViews() {
    }

    /** A technology's state for one player (§4.1, D32). */
    public enum TechState {
        RESEARCHED,
        AVAILABLE,
        PREREQUISITES_MISSING,
        REQUIREMENTS_UNMET,
        BLOCKED,
        UNKNOWN_STAGE;

        static final StreamCodec<io.netty.buffer.ByteBuf, TechState> STREAM_CODEC =
              ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], TechState::ordinal);
    }

    /** One ingredient line as the screen shows it. */
    /** A lab tier label (D28): {@code name} is a translation key or text, {@code color} 0xRRGGBB. */
    public record TierView(int level, String name, int color, ItemStack icon) {

        public static final StreamCodec<RegistryFriendlyByteBuf, TierView> STREAM_CODEC = StreamCodec.composite(
              ByteBufCodecs.VAR_INT, TierView::level,
              ByteBufCodecs.STRING_UTF8, TierView::name,
              ByteBufCodecs.INT, TierView::color,
              ItemStack.OPTIONAL_STREAM_CODEC, TierView::icon,
              TierView::new);

        /** Shown name: translated when it is a key, else the text itself. */
        public Component label() {
            return Component.translatableWithFallback(name, name).withColor(color);
        }

        public static TierView fallback(int level) {
            return new TierView(level, "Tier " + level, 0xA0A0A0, ItemStack.EMPTY);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TierView t && t.level == level && t.name.equals(name) && t.color == color && ItemStack.matches(t.icon, icon);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(level, name, color);
        }
    }

    public record IngredientView(ItemStack icon, Component name, int amount) {

        static final StreamCodec<RegistryFriendlyByteBuf, IngredientView> STREAM_CODEC = StreamCodec.composite(
              ItemStack.OPTIONAL_STREAM_CODEC, IngredientView::icon,
              ComponentSerialization.STREAM_CODEC, IngredientView::name,
              ByteBufCodecs.VAR_INT, IngredientView::amount,
              IngredientView::new);
    }

    /**
     * One technology for one player.
     *
     * @param prerequisites technologies whose stages this one's stage requires
     * @param preview       up to 8 items its stage locks (§4.5 unlock preview)
     */
    public record TechView(ResourceLocation id, Component name, ItemStack icon, int units, int unitTime, int minTier,
          List<IngredientView> ingredients, TechState state, List<ResourceLocation> prerequisites, List<ItemStack> preview) {

        public TechView {
            ingredients = List.copyOf(ingredients);
            prerequisites = List.copyOf(prerequisites);
            preview = List.copyOf(preview);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, TechView> STREAM_CODEC = StreamCodec.of((buf, v) -> {
            ResourceLocation.STREAM_CODEC.encode(buf, v.id());
            ComponentSerialization.STREAM_CODEC.encode(buf, v.name());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.icon());
            buf.writeVarInt(v.units());
            buf.writeVarInt(v.unitTime());
            buf.writeVarInt(v.minTier());
            IngredientView.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.ingredients());
            TechState.STREAM_CODEC.encode(buf, v.state());
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.prerequisites());
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, v.preview());
        }, buf -> new TechView(ResourceLocation.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
              ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
              IngredientView.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), TechState.STREAM_CODEC.decode(buf),
              ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf)));
    }

    /** Progress of one technology for the owner. */
    public record ProgressView(ResourceLocation tech, int done, int units, int inFlight) {

        static final StreamCodec<RegistryFriendlyByteBuf, ProgressView> STREAM_CODEC = StreamCodec.composite(
              ResourceLocation.STREAM_CODEC, ProgressView::tech,
              ByteBufCodecs.VAR_INT, ProgressView::done,
              ByteBufCodecs.VAR_INT, ProgressView::units,
              ByteBufCodecs.VAR_INT, ProgressView::inFlight,
              ProgressView::new);
    }

    /** The owner's queue (§4.3) with progress, and BLOCKED technologies to re-add (D32). */
    public record QueueView(List<ResourceLocation> entries, int max, List<ProgressView> progress, List<ResourceLocation> blocked) {

        public QueueView {
            entries = List.copyOf(entries);
            progress = List.copyOf(progress);
            blocked = List.copyOf(blocked);
        }

        public static final QueueView EMPTY = new QueueView(List.of(), 0, List.of(), List.of());

        static final StreamCodec<RegistryFriendlyByteBuf, QueueView> STREAM_CODEC = StreamCodec.composite(
              ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), QueueView::entries,
              ByteBufCodecs.VAR_INT, QueueView::max,
              ProgressView.STREAM_CODEC.apply(ByteBufCodecs.list()), QueueView::progress,
              ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), QueueView::blocked,
              QueueView::new);

        public java.util.Optional<ProgressView> progressOf(ResourceLocation tech) {
            return progress.stream().filter(p -> p.tech().equals(tech)).findFirst();
        }
    }
}
