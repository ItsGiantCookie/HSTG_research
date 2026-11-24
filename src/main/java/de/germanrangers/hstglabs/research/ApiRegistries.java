package de.germanrangers.hstglabs.research;

import de.germanrangers.hstglabs.api.ingredient.IngredientType;
import de.germanrangers.hstglabs.api.ingredient.RegisterIngredientTypesEvent;
import de.germanrangers.hstglabs.api.lab.IdleReasonType;
import de.germanrangers.hstglabs.api.lab.LabRequirementType;
import de.germanrangers.hstglabs.api.lab.LabStatsProvider;
import de.germanrangers.hstglabs.api.lab.LabTypeSource;
import de.germanrangers.hstglabs.api.lab.RegisterIdleReasonsEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabRequirementTypesEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabStatsProvidersEvent;
import de.germanrangers.hstglabs.api.lab.RegisterLabTypeSourcesEvent;
import de.germanrangers.hstglabs.api.modifier.Modifier;
import de.germanrangers.hstglabs.api.modifier.RegisterModifiersEvent;
import de.germanrangers.hstglabs.api.owner.OwnerType;
import de.germanrangers.hstglabs.api.owner.RegisterOwnerTypesEvent;
import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import de.germanrangers.hstglabs.api.reward.RegisterRewardTypesEvent;
import de.germanrangers.hstglabs.api.reward.RewardType;
import de.germanrangers.hstglabs.api.tech.RegisterTechSourcesEvent;
import de.germanrangers.hstglabs.api.tech.TechSource;
import de.germanrangers.hstglabs.api.template.RegisterTemplateKindsEvent;
import de.germanrangers.hstglabs.api.template.TemplateKind;
import de.germanrangers.hstglabs.core.FreezableRegistry;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoader;

/**
 * The common §9.2 extension registries (docs/design.md §6.1: research holds the frozen registries).
 * Each is filled by its {@code Register…Event} on the mod bus during common setup and frozen right
 * after. Client registries: {@code client.ClientApiRegistries}.
 */
public final class ApiRegistries {

    public static final FreezableRegistry<ResourceLocation, IngredientType<?, ?>> INGREDIENT_TYPES = new FreezableRegistry<>("ingredient types");
    public static final FreezableRegistry<ResourceLocation, RewardType<?>> REWARD_TYPES = new FreezableRegistry<>("reward types");
    public static final FreezableRegistry<ResourceLocation, OwnerType> OWNER_TYPES = new FreezableRegistry<>("owner types");
    public static final FreezableRegistry<ResourceLocation, TechSource> TECH_SOURCES = new FreezableRegistry<>("tech sources");
    public static final FreezableRegistry<ResourceLocation, LabRequirementType<?>> LAB_REQUIREMENT_TYPES =
          new FreezableRegistry<>("lab requirement types");
    public static final FreezableRegistry<ResourceLocation, LabTypeSource> LAB_TYPE_SOURCES = new FreezableRegistry<>("lab type sources");
    public static final FreezableRegistry<ResourceLocation, LabStatsProvider.Factory> LAB_STATS_PROVIDERS =
          new FreezableRegistry<>("lab stats providers");
    public static final FreezableRegistry<ResourceLocation, Modifier> MODIFIERS = new FreezableRegistry<>("modifiers");
    public static final FreezableRegistry<ResourceLocation, IdleReasonType> IDLE_REASONS = new FreezableRegistry<>("idle reasons");
    public static final FreezableRegistry<ResourceLocation, TemplateKind<?>> TEMPLATE_KINDS = new FreezableRegistry<>("template kinds");

    private static boolean dispatched;

    private ApiRegistries() {
    }

    /** One registry with the event that fills it. */
    public record Binding(FreezableRegistry<ResourceLocation, ?> registry, RegisterIdEvent<?> event) {
    }

    /** A fresh event per registry, bound to it; in registry order. */
    public static List<Binding> bindings() {
        return List.of(
              new Binding(INGREDIENT_TYPES, new RegisterIngredientTypesEvent(INGREDIENT_TYPES::register)),
              new Binding(REWARD_TYPES, new RegisterRewardTypesEvent(REWARD_TYPES::register)),
              new Binding(OWNER_TYPES, new RegisterOwnerTypesEvent(OWNER_TYPES::register)),
              new Binding(TECH_SOURCES, new RegisterTechSourcesEvent(TECH_SOURCES::register)),
              new Binding(LAB_REQUIREMENT_TYPES, new RegisterLabRequirementTypesEvent(LAB_REQUIREMENT_TYPES::register)),
              new Binding(LAB_TYPE_SOURCES, new RegisterLabTypeSourcesEvent(LAB_TYPE_SOURCES::register)),
              new Binding(LAB_STATS_PROVIDERS, new RegisterLabStatsProvidersEvent(LAB_STATS_PROVIDERS::register)),
              new Binding(MODIFIERS, new RegisterModifiersEvent(MODIFIERS::register)),
              new Binding(IDLE_REASONS, new RegisterIdleReasonsEvent(IDLE_REASONS::register)),
              new Binding(TEMPLATE_KINDS, new RegisterTemplateKindsEvent(TEMPLATE_KINDS::register)));
    }

    /**
     * Fires every event once, each mod's listeners in mod order, and freezes each registry right after
     * its event. Call from {@code FMLCommonSetupEvent.enqueueWork}, after parallel setup has returned.
     */
    public static void dispatch() {
        if (dispatched) {
            throw new IllegalStateException("API registries were already dispatched");
        }
        dispatched = true;
        for (Binding binding : bindings()) {
            ModLoader.postEventWrapContainerInModOrder(binding.event());
            binding.registry().freeze();
        }
    }
}
