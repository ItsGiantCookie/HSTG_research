package io.github.itsgiantcookie.hstglabs.api.client;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.ApiStatus;

/**
 * Draws one ingredient of a given ingredient type in lab screens, the tree and the dev UI
 * (docs/design.md §9.2). Registered per ingredient type id through
 * {@link RegisterIngredientRenderersEvent}. Client thread.
 *
 * @param <T> the ingredient type's parsed ingredient
 */
@ApiStatus.Experimental
public interface IngredientRenderer<T> {

    /** Draws a 16 × 16 icon at {@code x, y}. */
    void render(GuiGraphics graphics, T ingredient, int x, int y);
}
