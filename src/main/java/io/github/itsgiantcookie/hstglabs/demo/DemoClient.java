package io.github.itsgiantcookie.hstglabs.demo;

import io.github.itsgiantcookie.hstglabs.api.client.DevPanel;
import io.github.itsgiantcookie.hstglabs.api.client.IngredientRenderer;
import io.github.itsgiantcookie.hstglabs.api.client.RegisterDevPanelsEvent;
import io.github.itsgiantcookie.hstglabs.api.client.RegisterIngredientRenderersEvent;
import io.github.itsgiantcookie.hstglabs.api.client.RegisterRequirementEditorsEvent;
import io.github.itsgiantcookie.hstglabs.api.client.RegisterTechNodeDecoratorsEvent;
import io.github.itsgiantcookie.hstglabs.api.client.RequirementEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;

/** Client half of the demo addon: one of every {@code api.client} extension point. Client only. */
final class DemoClient {

    private DemoClient() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterIngredientRenderersEvent.class, e -> e.register(DemoAddon.INGREDIENT,
              (IngredientRenderer<DemoAddon.DemoItem>) (graphics, ingredient, x, y) -> graphics.renderItem(new ItemStack(ingredient.item()), x, y)));
        modBus.addListener(RegisterTechNodeDecoratorsEvent.class, e -> e.register(DemoAddon.id("corner"),
              (graphics, technology, x, y, size) -> graphics.fill(x + size - 3, y, x + size, y + 3, 0xFF2FD3B4)));
        modBus.addListener(RegisterDevPanelsEvent.class, e -> e.register(DemoAddon.id("panel"), new DevPanel() {
            @Override
            public Component title() {
                return Component.literal("Demo");
            }

            @Override
            public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
                graphics.drawString(Minecraft.getInstance().font, "Demo addon panel", x + 4, y + 4, 0xFFFFFFFF);
            }
        }));
        modBus.addListener(RegisterRequirementEditorsEvent.class, e -> e.register(DemoAddon.REQUIREMENT,
              (RequirementEditor<DemoAddon.MinMembers>) requirement -> Component.literal("≥ " + requirement.count() + " members")));
    }
}
