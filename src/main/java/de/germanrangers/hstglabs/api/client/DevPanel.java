package de.germanrangers.hstglabs.api.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/**
 * An extra panel in the in-game dev UI (docs/design.md §10, §9.2). Registered through
 * {@link RegisterDevPanelsEvent}. Skeleton for WP-13, which adds input handling and edit operations.
 * Client thread.
 */
@ApiStatus.Experimental
public interface DevPanel {

    /** Tab title. */
    Component title();

    /** Draws the panel into the given area. */
    void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY);
}
