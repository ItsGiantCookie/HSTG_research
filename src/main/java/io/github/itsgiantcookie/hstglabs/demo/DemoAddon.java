package io.github.itsgiantcookie.hstglabs.demo;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.itsgiantcookie.hstglabs.api.HSLabsAPI;
import io.github.itsgiantcookie.hstglabs.api.event.ResearchEvent;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientStorage;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import io.github.itsgiantcookie.hstglabs.api.ingredient.RegisterIngredientTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.IdleReasonType;
import io.github.itsgiantcookie.hstglabs.api.lab.LabRequirementType;
import io.github.itsgiantcookie.hstglabs.api.lab.LabStats;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterIdleReasonsEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterLabRequirementTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterLabStatsProvidersEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterLabTypeSourcesEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.ResearchSite;
import io.github.itsgiantcookie.hstglabs.api.modifier.Modifier;
import io.github.itsgiantcookie.hstglabs.api.modifier.ModifierContext;
import io.github.itsgiantcookie.hstglabs.api.modifier.ModifierScope;
import io.github.itsgiantcookie.hstglabs.api.modifier.ModifierTarget;
import io.github.itsgiantcookie.hstglabs.api.modifier.RegisterModifiersEvent;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerType;
import io.github.itsgiantcookie.hstglabs.api.owner.RegisterOwnerTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.reward.RegisterRewardTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardContext;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardType;
import io.github.itsgiantcookie.hstglabs.api.tech.RegisterTechSourcesEvent;
import io.github.itsgiantcookie.hstglabs.api.template.RegisterTemplateKindsEvent;
import io.github.itsgiantcookie.hstglabs.api.template.TemplateKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * The in-repo example addon (docs/design.md §9.1): registers one of every extension point using
 * only {@code api}, so whatever it needs, a real addon gets too (a guard test keeps it api-only).
 * Loaded only when {@code -Dhstglabs.demo=true}, which the dev and GameTest runs set.
 */
public final class DemoAddon {

    public static final String PROPERTY = "hstglabs.demo";

    public static final ResourceLocation INGREDIENT = id("item");
    public static final ResourceLocation REWARD = id("message");
    public static final ResourceLocation OWNER = id("solo");
    public static final ResourceLocation TECH_SOURCE = id("techs");
    public static final ResourceLocation REQUIREMENT = id("min_members");
    public static final ResourceLocation LAB_TYPE_SOURCE = id("lab_types");
    /** A vanilla block entity as "another mod's machine". */
    public static final ResourceLocation STATS_PROVIDER = ResourceLocation.withDefaultNamespace("furnace");
    public static final ResourceLocation MODIFIER = id("site_speed");
    public static final ResourceLocation IDLE_REASON = id("no_mana");
    public static final ResourceLocation TEMPLATE_KIND = id("message_template");

    private static final Logger LOGGER = LogUtils.getLogger();

    private DemoAddon() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterIngredientTypesEvent.class, e -> e.register(INGREDIENT, new DemoItemType()));
        modBus.addListener(RegisterRewardTypesEvent.class, e -> e.register(REWARD, new DemoMessageReward()));
        modBus.addListener(RegisterOwnerTypesEvent.class, e -> e.register(OWNER, new DemoSoloOwner()));
        modBus.addListener(RegisterTechSourcesEvent.class, e -> e.register(TECH_SOURCE, sink -> sink.accept(id("tech"), demoTech())));
        modBus.addListener(RegisterLabRequirementTypesEvent.class, e -> e.register(REQUIREMENT, new DemoMinMembers()));
        modBus.addListener(RegisterLabTypeSourcesEvent.class, e -> e.register(LAB_TYPE_SOURCE, sink -> sink.accept(id("lab"), demoLabType())));
        modBus.addListener(RegisterLabStatsProvidersEvent.class,
              e -> e.register(STATS_PROVIDER, be -> () -> new LabStats(1.0, 1, 1, Set.of(), 0L)));
        modBus.addListener(RegisterModifiersEvent.class, e -> e.register(MODIFIER, new DemoSiteSpeed()));
        modBus.addListener(RegisterIdleReasonsEvent.class,
              e -> e.register(IDLE_REASON, new IdleReasonType("hstg_labs.demo.idle.no_mana", id("textures/gui/idle/no_mana.png"))));
        modBus.addListener(RegisterTemplateKindsEvent.class, e -> e.register(TEMPLATE_KIND, new DemoMessageTemplate()));
        NeoForge.EVENT_BUS.addListener(ResearchEvent.Completed.class,
              e -> LOGGER.info("[demo] {} completed {}", e.owner(), e.technology()));
        if (FMLEnvironment.dist.isClient()) {
            DemoClient.register(modBus);
        }
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HSLabsAPI.MOD_ID, "demo_" + path);
    }

    /** A complete definition in the file format (docs/design.md §8.1); its stage only exists in a pack that defines it. */
    private static JsonObject demoTech() {
        return com.google.gson.JsonParser.parseString("""
              { "stage": "hstg_labs_demo_stage",
                "cost": { "units": 10, "unit_time": 200,
                          "ingredients": [ { "type": "hstg_labs:demo_item", "item": "minecraft:paper", "amount": 1 } ] },
                "rewards": [ { "type": "hstg_labs:demo_message", "text": "The demo technology is done." } ] }
              """).getAsJsonObject();
    }

    /** A worker lab type in the data format (docs/design.md §5.5), carrying the demo's own lab tag. */
    private static JsonObject demoLabType() {
        return com.google.gson.JsonParser.parseString("""
              { "roles": ["worker", "node"], "tier": 1, "speed": 1.5, "tags": ["hstg_labs:demo_tag"] }
              """).getAsJsonObject();
    }

    /** Ingredient type: one exact item. */
    record DemoItem(Item item) {
    }

    static final class DemoItemType implements IngredientType<DemoItem, ItemStack> {

        private static final MapCodec<DemoItem> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").xmap(DemoItem::new, DemoItem::item);
        private static final StreamCodec<RegistryFriendlyByteBuf, DemoItem> STREAM_CODEC =
              ByteBufCodecs.registry(Registries.ITEM).map(DemoItem::new, DemoItem::item);

        @Override
        public MapCodec<DemoItem> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DemoItem> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public IngredientStorage<ItemStack> storage() {
            return IngredientStorage.ITEM;
        }

        @Override
        public boolean matches(DemoItem ingredient, ItemStack stored) {
            return stored.is(ingredient.item());
        }

        @Override
        public Component describe(DemoItem ingredient) {
            return ingredient.item().getDescription();
        }
    }

    /** Reward type: a chat line to every online member. */
    record DemoMessage(String text) {
    }

    static final class DemoMessageReward implements RewardType<DemoMessage> {

        static final MapCodec<DemoMessage> CODEC = Codec.STRING.fieldOf("text").xmap(DemoMessage::new, DemoMessage::text);

        @Override
        public MapCodec<DemoMessage> codec() {
            return CODEC;
        }

        @Override
        public void apply(DemoMessage reward, RewardContext context) {
            context.onlineMembers().forEach(player -> player.sendSystemMessage(Component.literal(reward.text())));
        }
    }

    /** Owner type: every player researches alone. */
    static final class DemoSoloOwner implements OwnerType {

        @Override
        public Optional<OwnerId> ownerOf(ServerPlayer player) {
            return Optional.of(new OwnerId(OWNER, player.getStringUUID()));
        }

        @Override
        public Collection<UUID> members(OwnerId owner, MinecraftServer server) {
            return List.of(UUID.fromString(owner.key()));
        }

        @Override
        public boolean individualStages() {
            return true;
        }
    }

    /** Lab requirement type: at least {@code count} active members on the site. */
    record MinMembers(int count) {
    }

    static final class DemoMinMembers implements LabRequirementType<MinMembers> {

        private static final MapCodec<MinMembers> CODEC = Codec.INT.fieldOf("count").xmap(MinMembers::new, MinMembers::count);

        @Override
        public MapCodec<MinMembers> codec() {
            return CODEC;
        }

        @Override
        public boolean test(MinMembers requirement, ResearchSite site, int effectiveTier) {
            return site.activeMembers().count() >= requirement.count();
        }

        @Override
        public Component describe(MinMembers requirement) {
            return Component.literal("at least " + requirement.count() + " active lab members");
        }
    }

    /** Modifier: +10 % speed on the demo's own technologies only, so a dev world's real research keeps its numbers. */
    static final class DemoSiteSpeed implements Modifier {

        @Override
        public ModifierTarget target() {
            return ModifierTarget.SPEED;
        }

        @Override
        public ModifierScope scope() {
            return ModifierScope.SITE;
        }

        @Override
        public double modify(double value, ModifierContext context) {
            return context.technology().filter(t -> t.getPath().startsWith("demo_")).isPresent() ? value * 1.1 : value;
        }
    }

    /** Template kind: stamps out chat-message rewards. */
    static final class DemoMessageTemplate implements TemplateKind<DemoMessage> {

        @Override
        public MapCodec<DemoMessage> targetCodec() {
            return DemoMessageReward.CODEC;
        }

        @Override
        public String editorHint() {
            return "reward";
        }
    }
}
