package io.github.itsgiantcookie.hstglabs.packet;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.client.ClientResearch;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.IngredientView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.ProgressView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.QueueView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechState;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TierView;
import io.github.itsgiantcookie.hstglabs.lab.LabTiers;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.StageBridge;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.github.itsgiantcookie.hstglabs.tech.BuiltInTypes;
import io.github.itsgiantcookie.hstglabs.tech.SciencePackItem;
import io.github.itsgiantcookie.hstglabs.tech.Technology;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S → C (docs/design.md §6.3 {@code TechSnapshot}): every enabled technology as this player sees it
 * (name, icon, cost, prerequisites, state, unlock preview) and their owner's queue. Sent on login,
 * after definitions or stage changes, and after queue changes.
 */
public record TechSnapshotPayload(List<TechView> technologies, QueueView queue, List<TierView> tiers) implements CustomPacketPayload {

    public static final Type<TechSnapshotPayload> TYPE = new Type<>(HstgLabs.rl("tech_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
          TechView.STREAM_CODEC.apply(ByteBufCodecs.list()), TechSnapshotPayload::technologies,
          QueueView.STREAM_CODEC, TechSnapshotPayload::queue,
          TierView.STREAM_CODEC.apply(ByteBufCodecs.list()), TechSnapshotPayload::tiers,
          TechSnapshotPayload::new);
    private static final int PREVIEW = 8;

    public TechSnapshotPayload {
        technologies = List.copyOf(technologies);
        tiers = List.copyOf(tiers);
    }

    @Override
    public Type<TechSnapshotPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, (payload, context) -> ClientResearch.accept(payload));
    }

    /** Sends a fresh snapshot to each of these players. */
    public static void send(Collection<ServerPlayer> players) {
        ResearchManager.get().ifPresent(m -> players.forEach(p -> PacketDistributor.sendToPlayer(p, build(m, p))));
    }

    /** Sends a fresh snapshot to the owner's online members. */
    public static void sendToOwner(ResearchManager m, OwnerId owner, Collection<ServerPlayer> online) {
        List<ServerPlayer> members = online.stream().filter(p -> m.ownerOf(p).equals(owner)).toList();
        send(members);
    }

    /** What {@code player} is shown. Server thread. */
    public static TechSnapshotPayload build(ResearchManager m, ServerPlayer player) {
        OwnerId owner = m.ownerOf(player);
        ResearchBook<ResourceLocation> book = m.book(owner);
        Map<ResourceLocation, Technology> techs = TechReloader.current().technologies();
        Map<String, ResourceLocation> techByStage = techs.values().stream().collect(Collectors.toMap(Technology::stage, Technology::id, (a, b) -> a));
        List<TechView> views = new ArrayList<>();
        for (Technology t : techs.values()) {
            Optional<StageBridge.StageInfo> info = m.stageInfo(t.stage(), player);
            TechState state = info.map(i -> switch (i.queue()) {
                case OK -> book.isBlocked(t.id()) ? TechState.BLOCKED : TechState.AVAILABLE;
                case ALREADY_UNLOCKED -> TechState.RESEARCHED;
                case PREREQUISITES_MISSING -> TechState.PREREQUISITES_MISSING;
                case REQUIREMENTS_UNMET -> TechState.REQUIREMENTS_UNMET;
                case UNKNOWN_STAGE -> TechState.UNKNOWN_STAGE;
            }).orElse(TechState.UNKNOWN_STAGE);
            Component name = Component.literal(info.map(StageBridge.StageInfo::displayName).filter(s -> !s.isBlank()).orElse(t.id().toString()));
            ItemStack icon = info.map(StageBridge.StageInfo::icon).map(TechSnapshotPayload::item).filter(s -> !s.isEmpty())
                  .orElseGet(() -> t.cost().ingredients().isEmpty() ? ItemStack.EMPTY : iconOf(t.cost().ingredients().get(0).what()));
            List<IngredientView> ingredients = t.cost().ingredients().stream()
                  .map(i -> new IngredientView(iconOf(i.what()), describe(i.what()), i.amount())).toList();
            List<ResourceLocation> prerequisites = info.map(StageBridge.StageInfo::prerequisites).orElse(List.of()).stream()
                  .map(techByStage::get).filter(Objects::nonNull).toList();
            List<ItemStack> preview = info.map(StageBridge.StageInfo::items).orElse(List.of()).stream().limit(PREVIEW)
                  .map(TechSnapshotPayload::item).filter(s -> !s.isEmpty()).toList();
            views.add(new TechView(t.id(), name, icon, t.cost().units(), t.cost().unitTime(), t.minTier(), ingredients, state, prerequisites, preview));
        }
        List<ProgressView> progress = book.progressEntries().entrySet().stream()
              .map(e -> new ProgressView(e.getKey(), e.getValue().done(), e.getValue().units(), e.getValue().inFlight())).toList();
        QueueView queue = new QueueView(book.queue().entries(), book.queue().max(), progress, List.copyOf(book.blockedTechnologies()));
        List<TierView> tiers = LabTiers.current().all().stream()
              .map(t -> new TierView(t.level(), t.name(), t.color(), item(t.icon()))).toList();
        return new TechSnapshotPayload(views, queue, tiers);
    }

    private static ItemStack item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** An item to draw for an ingredient: the item, the first item of a tag, the pack; empty for other storage (chemicals draw their own way). */
    static ItemStack iconOf(Technology.Ingredient ingredient) {
        Object value = ingredient.value();
        if (value instanceof BuiltInTypes.ItemIngredient i) {
            return new ItemStack(i.item());
        }
        if (value instanceof BuiltInTypes.TagIngredient t) {
            return BuiltInRegistries.ITEM.getTag(t.tag()).flatMap(set -> set.stream().findFirst()).map(h -> new ItemStack(h.value()))
                  .orElse(ItemStack.EMPTY);
        }
        if (value instanceof BuiltInTypes.PackIngredient p) {
            return SciencePackItem.of(p.pack(), 1);
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Component describe(Technology.Ingredient ingredient) {
        return ((io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType) ingredient.ingredientType()).describe(ingredient.value());
    }

}
