package de.germanrangers.hstglabs.api.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Draws extra marks on a technology node in the tree and dev UI (docs/design.md §9.2), e.g. an addon's
 * badge. Registered through {@link RegisterTechNodeDecoratorsEvent}. Client thread.
 */
@ApiStatus.Experimental
public interface TechNodeDecorator {

    /** Called after the node at {@code x, y} with edge length {@code size} is drawn. */
    void decorate(GuiGraphics graphics, ResourceLocation technology, int x, int y, int size);
}
