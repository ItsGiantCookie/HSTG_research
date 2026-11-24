package io.github.itsgiantcookie.hstglabs.tech;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientStorage;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import io.github.itsgiantcookie.hstglabs.api.ingredient.RegisterIngredientTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.LabRequirementType;
import io.github.itsgiantcookie.hstglabs.api.lab.LabRole;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterLabRequirementTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.lab.ResearchSite;
import io.github.itsgiantcookie.hstglabs.api.lab.SiteMember;
import io.github.itsgiantcookie.hstglabs.api.reward.RegisterRewardTypesEvent;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardContext;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardType;
import io.github.itsgiantcookie.hstglabs.api.template.RegisterTemplateKindsEvent;
import io.github.itsgiantcookie.hstglabs.api.template.TemplateKind;
import io.github.itsgiantcookie.hstglabs.tech.template.BuiltInTemplates;
import com.mojang.serialization.Dynamic;
import io.github.itsgiantcookie.hstglabs.core.LabRequirement;
import io.github.itsgiantcookie.hstglabs.core.LabRequirements;
import io.github.itsgiantcookie.hstglabs.core.Member;
import io.github.itsgiantcookie.hstglabs.core.Role;
import io.github.itsgiantcookie.hstglabs.core.SiteModel;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

/**
 * The built-in ingredient, lab requirement and reward types (docs/design.md §5.6, §8.1, §8.2),
 * registered through the public events like any addon's (§9.6). The chemical ingredient type lives in
 * mek/ ({@code mek.ChemicalIngredientType}).
 */
public final class BuiltInTypes {

    public static final ResourceLocation ITEM = rl("item");
    public static final ResourceLocation TAG = rl("tag");
    public static final ResourceLocation PACK = rl("pack");
    public static final ResourceLocation LAB = rl("lab");
    public static final ResourceLocation NETWORK = rl("network");
    public static final ResourceLocation MIN_TIER = rl("min_tier");
    public static final ResourceLocation ALL = rl("all");
    public static final ResourceLocation ANY = rl("any");
    public static final ResourceLocation UNLOCK_STAGE = rl("unlock_stage");

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set by the hs package (WP-08); applies {@code unlock_stage}. Until then completions only log. */
    private static volatile Consumer<RewardContext> stageUnlocker;

    private BuiltInTypes() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterIngredientTypesEvent.class, e -> {
            e.register(ITEM, new ItemType());
            e.register(TAG, new TagType());
            e.register(PACK, new PackType());
        });
        modBus.addListener(RegisterLabRequirementTypesEvent.class, e -> {
            e.register(LAB, new CoreRequirementType<>(LAB_CODEC));
            e.register(NETWORK, new CoreRequirementType<>(MapCodec.unit(new LabRequirements.Network())));
            e.register(MIN_TIER, new CoreRequirementType<>(Codec.INT.fieldOf("tier").flatXmap(
                  t -> t >= 1 ? DataResult.success(new LabRequirements.MinTier(t)) : DataResult.error(() -> "tier must be at least 1, was " + t),
                  r -> DataResult.success(r.tier()))));
            e.register(ALL, new CoreRequirementType<>(group(LabRequirements.AllOf::new, LabRequirements.AllOf::of)));
            e.register(ANY, new CoreRequirementType<>(group(LabRequirements.AnyOf::new, LabRequirements.AnyOf::of)));
        });
        modBus.addListener(RegisterRewardTypesEvent.class, e -> e.register(UNLOCK_STAGE, new UnlockStageType()));
        modBus.addListener(RegisterTemplateKindsEvent.class, e -> {
            e.register(ResourceLocation.parse(BuiltInTemplates.TECHNOLOGY_KIND), new DefinitionKind("technology"));
            e.register(ResourceLocation.parse(BuiltInTemplates.PACK_KIND), new DefinitionKind("pack"));
        });
    }

    /**
     * Template kinds for our JSON definition files (docs/design.md §8.6). The definitions are read by
     * {@link TechJson} / {@link PackJson}, not by a codec, so the target codec passes the data through.
     */
    record DefinitionKind(String editorHint) implements TemplateKind<Dynamic<?>> {

        @Override
        public MapCodec<Dynamic<?>> targetCodec() {
            return MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH);
        }
    }

    /** Wires {@code unlock_stage} to History Stages; called once by the hs package. */
    public static void setStageUnlocker(Consumer<RewardContext> unlocker) {
        stageUnlocker = unlocker;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(TechJson.NAMESPACE, path);
    }

    // ---- ingredients ----

    /** {@code hstg_labs:item}: one exact item. */
    public record ItemIngredient(Item item) {
    }

    static final class ItemType implements IngredientType<ItemIngredient, ItemStack> {

        private static final MapCodec<ItemIngredient> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item")
              .xmap(ItemIngredient::new, ItemIngredient::item);
        private static final StreamCodec<RegistryFriendlyByteBuf, ItemIngredient> STREAM_CODEC =
              ByteBufCodecs.registry(Registries.ITEM).map(ItemIngredient::new, ItemIngredient::item);

        @Override
        public MapCodec<ItemIngredient> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ItemIngredient> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public IngredientStorage<ItemStack> storage() {
            return IngredientStorage.ITEM;
        }

        @Override
        public boolean matches(ItemIngredient ingredient, ItemStack stored) {
            return stored.is(ingredient.item());
        }

        @Override
        public Component describe(ItemIngredient ingredient) {
            return ingredient.item().getDescription();
        }
    }

    /** {@code hstg_labs:tag}: any item in an item tag. */
    public record TagIngredient(TagKey<Item> tag) {
    }

    static final class TagType implements IngredientType<TagIngredient, ItemStack> {

        private static final MapCodec<TagIngredient> CODEC = TagKey.codec(Registries.ITEM).fieldOf("tag").xmap(TagIngredient::new, TagIngredient::tag);
        private static final StreamCodec<RegistryFriendlyByteBuf, TagIngredient> STREAM_CODEC =
              ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(rl -> new TagIngredient(TagKey.create(Registries.ITEM, rl)),
                    t -> t.tag().location());

        @Override
        public MapCodec<TagIngredient> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TagIngredient> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public IngredientStorage<ItemStack> storage() {
            return IngredientStorage.ITEM;
        }

        @Override
        public boolean matches(TagIngredient ingredient, ItemStack stored) {
            return stored.is(ingredient.tag());
        }

        @Override
        public Component describe(TagIngredient ingredient) {
            return Component.literal("#" + ingredient.tag().location());
        }
    }

    /** {@code hstg_labs:pack}: our data-driven science pack with this pack id (§8.2). */
    public record PackIngredient(ResourceLocation pack) {
    }

    static final class PackType implements IngredientType<PackIngredient, ItemStack> {

        private static final MapCodec<PackIngredient> CODEC = ResourceLocation.CODEC.fieldOf("pack").xmap(PackIngredient::new, PackIngredient::pack);
        private static final StreamCodec<RegistryFriendlyByteBuf, PackIngredient> STREAM_CODEC =
              ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(PackIngredient::new, PackIngredient::pack);

        @Override
        public MapCodec<PackIngredient> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PackIngredient> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        public IngredientStorage<ItemStack> storage() {
            return IngredientStorage.ITEM;
        }

        @Override
        public boolean matches(PackIngredient ingredient, ItemStack stored) {
            return stored.is(Registration.SCIENCE_PACK.get()) && ingredient.pack().equals(stored.get(Registration.PACK_COMPONENT.get()));
        }

        @Override
        public Component describe(PackIngredient ingredient) {
            return Component.literal(PackLookup.get(ingredient.pack()).map(PackDefinition::name).orElse(ingredient.pack().toString()));
        }
    }

    // ---- lab requirements ----

    private static final Codec<Role> ROLE_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Role.valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown role '" + s + "', expected controller, consumer, worker or node");
        }
    }, r -> r.name().toLowerCase(Locale.ROOT));

    private static final MapCodec<LabRequirements.Lab> LAB_CODEC = RecordCodecBuilder.<LabFields>mapCodec(instance -> instance.group(
          ResourceLocation.CODEC.optionalFieldOf("lab_type").forGetter(LabFields::labType),
          ResourceLocation.CODEC.optionalFieldOf("lab_tag").forGetter(LabFields::labTag),
          Codec.INT.optionalFieldOf("count", 1).forGetter(LabFields::count),
          ROLE_CODEC.optionalFieldOf("role").forGetter(LabFields::role)
    ).apply(instance, LabFields::new)).flatXmap(LabFields::toLab, LabFields::fromLab);

    private record LabFields(Optional<ResourceLocation> labType, Optional<ResourceLocation> labTag, int count, Optional<Role> role) {

        DataResult<LabRequirements.Lab> toLab() {
            if (labType.isPresent() == labTag.isPresent()) {
                return DataResult.error(() -> "a hstg_labs:lab requirement names exactly one of lab_type or lab_tag");
            }
            if (count < 1) {
                return DataResult.error(() -> "count must be at least 1, was " + count);
            }
            return DataResult.success(new LabRequirements.Lab(labType.map(ResourceLocation::toString).orElse(null),
                  labTag.map(ResourceLocation::toString).orElse(null), count, role.orElse(null)));
        }

        static DataResult<LabFields> fromLab(LabRequirements.Lab lab) {
            return DataResult.success(new LabFields(Optional.ofNullable(lab.labType()).map(ResourceLocation::parse),
                  Optional.ofNullable(lab.labTag()).map(ResourceLocation::parse), lab.count(), Optional.ofNullable(lab.role())));
        }
    }

    /**
     * {@code hstg_labs:all} / {@code any}: {@code of} holds nested requirement entries. They are resolved
     * by the tech layer ({@link TechResolver}) against the same registry, so here they arrive already
     * in core form.
     */
    private static <G extends LabRequirement> MapCodec<G> group(Function<List<LabRequirement>, G> make, Function<G, List<LabRequirement>> of) {
        return TechResolver.NESTED_REQUIREMENTS.fieldOf("of").flatXmap(list -> {
            if (list.isEmpty()) {
                return DataResult.error(() -> "'of' needs at least one requirement");
            }
            return DataResult.success(make.apply(list));
        }, g -> DataResult.error(() -> "nested requirements are written back from the raw file, not encoded"));
    }

    /** A requirement type whose parsed value is a core requirement; tests a site through core. */
    static final class CoreRequirementType<T extends LabRequirement> implements LabRequirementType<T> {

        private final MapCodec<T> codec;

        CoreRequirementType(MapCodec<T> codec) {
            this.codec = codec;
        }

        @Override
        public MapCodec<T> codec() {
            return codec;
        }

        @Override
        public boolean test(T requirement, ResearchSite site, int effectiveTier) {
            return requirement.test(new LabRequirement.Context(toCore(site), effectiveTier));
        }

        @Override
        public Component describe(T requirement) {
            return Component.literal(requirement.describe());
        }
    }

    /** The api site view in core form. */
    public static SiteModel toCore(ResearchSite site) {
        List<Member> members = site.members().stream().map(BuiltInTypes::toCore).toList();
        return new SiteModel(site.isNetwork(), site.controllerTier(), members);
    }

    private static Member toCore(SiteMember member) {
        Set<Role> roles = member.roles().stream().map(BuiltInTypes::toCore).collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        Set<String> tags = member.labTags().stream().map(ResourceLocation::toString).collect(Collectors.toSet());
        return new Member(member.labType().toString(), tags, roles, member.active());
    }

    private static Role toCore(LabRole role) {
        return Role.valueOf(role.name());
    }

    // ---- rewards ----

    /** {@code hstg_labs:unlock_stage}: unlock the technology's HS stage (the default reward). */
    public enum UnlockStage {
        INSTANCE
    }

    static final class UnlockStageType implements RewardType<UnlockStage> {

        @Override
        public MapCodec<UnlockStage> codec() {
            return MapCodec.unit(UnlockStage.INSTANCE);
        }

        @Override
        public void apply(UnlockStage reward, RewardContext context) {
            Consumer<RewardContext> unlocker = stageUnlocker;
            if (unlocker == null) {
                LOGGER.warn("unlock_stage for {} skipped: the History Stages bridge is not wired yet (WP-08)", context.technology());
                return;
            }
            unlocker.accept(context);
        }
    }
}
