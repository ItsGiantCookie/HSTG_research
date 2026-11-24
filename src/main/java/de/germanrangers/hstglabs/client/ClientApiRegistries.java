package de.germanrangers.hstglabs.client;

import de.germanrangers.hstglabs.api.client.DevPanel;
import de.germanrangers.hstglabs.api.client.IngredientRenderer;
import de.germanrangers.hstglabs.api.client.RegisterDevPanelsEvent;
import de.germanrangers.hstglabs.api.client.RegisterIngredientRenderersEvent;
import de.germanrangers.hstglabs.api.client.RegisterRequirementEditorsEvent;
import de.germanrangers.hstglabs.api.client.RegisterTechNodeDecoratorsEvent;
import de.germanrangers.hstglabs.api.client.RequirementEditor;
import de.germanrangers.hstglabs.api.client.TechNodeDecorator;
import de.germanrangers.hstglabs.core.FreezableRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoader;

/** The client §9.2 extension registries; filled during client setup, frozen after. Client only. */
public final class ClientApiRegistries {

    public static final FreezableRegistry<ResourceLocation, IngredientRenderer<?>> INGREDIENT_RENDERERS =
          new FreezableRegistry<>("ingredient renderers");
    public static final FreezableRegistry<ResourceLocation, TechNodeDecorator> TECH_NODE_DECORATORS =
          new FreezableRegistry<>("tech node decorators");
    public static final FreezableRegistry<ResourceLocation, DevPanel> DEV_PANELS = new FreezableRegistry<>("dev panels");
    public static final FreezableRegistry<ResourceLocation, RequirementEditor<?>> REQUIREMENT_EDITORS =
          new FreezableRegistry<>("requirement editors");

    private ClientApiRegistries() {
    }

    /** Call from {@code FMLClientSetupEvent.enqueueWork}. */
    public static void dispatch() {
        ModLoader.postEventWrapContainerInModOrder(new RegisterIngredientRenderersEvent(INGREDIENT_RENDERERS::register));
        INGREDIENT_RENDERERS.freeze();
        ModLoader.postEventWrapContainerInModOrder(new RegisterTechNodeDecoratorsEvent(TECH_NODE_DECORATORS::register));
        TECH_NODE_DECORATORS.freeze();
        ModLoader.postEventWrapContainerInModOrder(new RegisterDevPanelsEvent(DEV_PANELS::register));
        DEV_PANELS.freeze();
        ModLoader.postEventWrapContainerInModOrder(new RegisterRequirementEditorsEvent(REQUIREMENT_EDITORS::register));
        REQUIREMENT_EDITORS.freeze();
    }
}
