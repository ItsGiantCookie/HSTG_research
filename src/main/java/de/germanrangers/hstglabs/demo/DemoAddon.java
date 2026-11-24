package de.germanrangers.hstglabs.demo;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import de.germanrangers.hstglabs.api.HSLabsAPI;
import de.germanrangers.hstglabs.api.event.ResearchEvent;
import de.germanrangers.hstglabs.api.ingredient.IngredientStorage;
import de.germanrangers.hstglabs.api.ingredient.IngredientType;
import de.germanrangers.hstglabs.api.ingredient.RegisterIngredientTypesEvent;
import de.germanrangers.hstglabs.api.lab.IdleReasonType;
import de.germanrangers.hstglabs.api.lab.LabRequirementType;
import de.germanrangers.hstglabs.api.lab.LabStats;
import de.germanrangers.hstglabs.api.lab.RegisterIdleReasonsEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabRequirementTypesEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabStatsProvidersEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabTypeSourcesEvent;
import de.germanrangers.hstglabs.api.lab.ResearchSite;
import de.germanrangers.hstglabs.api.modifier.Modifier;
import de.germanrangers.hstglabs.api.modifier.ModifierContext;
import de.germanrangers.hstglabs.api.modifier.ModifierScope;
import de.germanrangers.hstglabs.api.modifier.ModifierTarget;
import de.germanrangers.hstglabs.api.modifier.RegisterModifiersEvent;
import de.germanrangers.hstglabs.api.owner.OwnerId;
import de.germanrangers.hstglabs.api.owner.OwnerType;
import de.germanrangers.hstglabs.api.owner.RegisterOwnerTypesEvent;
import de.germanrangers.hstglabs.api.reward.RegisterRewardTypesEvent;
import de.germanrangers.hstglabs.api.reward.RewardContext;
import de.germanrangers.hstglabs.api.reward.RewardType;
import de.germanrangers.hstglabs.api.tech.RegisterTechSourcesEvent;
import de.germanrangers.hstglabs.api.template.RegisterTemplateKindsEvent;
import de.germanrangers.hstglabs.api.template.TemplateKind;
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

    private static JsonObject demoTech() {
        JsonObject tech = new JsonObject();
        tech.addProperty("stage", "demo_stage");
        return tech;
    }

    private static JsonObject demoLabType() {
        JsonObject lab = new JsonObject();
        lab.addProperty("tier", 1);
        return lab;
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

    /** Modifier: +10 % speed for every site. */
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
            return value * 1.1;
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
