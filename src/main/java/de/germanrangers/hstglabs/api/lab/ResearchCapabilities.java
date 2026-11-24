package de.germanrangers.hstglabs.api.lab;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.ApiStatus;

/**
 * The four lab role capabilities (docs/design.md §5.1, §9.3). A block from any mod takes a role by
 * exposing the capability in {@code RegisterCapabilitiesEvent}; no class of ours needs extending.
 * Roles do not depend on the side, so the context is {@code Void}.
 */
@ApiStatus.Experimental
public final class ResearchCapabilities {

    public static final BlockCapability<ResearchController, Void> CONTROLLER =
          BlockCapability.createVoid(rl("controller"), ResearchController.class);
    public static final BlockCapability<IngredientConsumer, Void> CONSUMER =
          BlockCapability.createVoid(rl("consumer"), IngredientConsumer.class);
    public static final BlockCapability<ResearchWorker, Void> WORKER =
          BlockCapability.createVoid(rl("worker"), ResearchWorker.class);
    public static final BlockCapability<GridNode, Void> NODE =
          BlockCapability.createVoid(rl("node"), GridNode.class);

    private ResearchCapabilities() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("hstg_labs", path);
    }
}
