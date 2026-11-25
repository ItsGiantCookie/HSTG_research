package io.github.itsgiantcookie.hstglabs.mek.client;

import io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu;
import io.github.itsgiantcookie.hstglabs.mek.UpgradeComponent;
import mekanism.api.Upgrade;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The basic lab's upgrades: a Mekanism {@link GuiWindow} (D33) holding the speed and energy upgrade
 * slots with their amount and effect. Unlike Mekanism's upgrade window it does not move: its slots are
 * plain menu slots at fixed positions ({@link BasicLabMenu#UPGRADES_X}), active only while it is open,
 * because Mekanism's movable virtual slots live in its common code.
 */
public class LabUpgradeWindow extends GuiWindow {

    private static final Upgrade[] UPGRADES = {Upgrade.SPEED, Upgrade.ENERGY};
    private final BasicLabMenu menu;
    private final Runnable onClose;

    public LabUpgradeWindow(IGuiWrapper gui, BasicLabMenu menu, Runnable onClose) {
        super(gui, BasicLabMenu.UPGRADE_WINDOW_X, BasicLabMenu.UPGRADE_WINDOW_Y, BasicLabMenu.UPGRADE_WINDOW_W, BasicLabMenu.UPGRADE_WINDOW_H,
              SelectedWindowData.UNSPECIFIED);
        this.menu = menu;
        this.onClose = onClose;
        // clicks no child takes fall through to the screen, which hands the two slots to vanilla handling
        interactionStrategy = InteractionStrategy.ALL;
        for (int i = 0; i < UPGRADES.length; i++) {
            addChild(new GuiSlot(SlotType.NORMAL, gui, BasicLabMenu.UPGRADES_X - 1, BasicLabMenu.UPGRADES_Y - 1 + i * BasicLabMenu.UPGRADES_STEP));
        }
        menu.setUpgradesShown(true);
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        // fixed: the slots inside cannot follow the window
    }

    @Override
    public void close() {
        super.close();
        menu.setUpgradesShown(false);
        onClose.run();
    }

    @Override
    public void renderForeground(GuiGraphics g, int mouseX, int mouseY) {
        super.renderForeground(g, mouseX, mouseY);
        drawTitleText(g, Component.translatable("hstg_labs.gui.upgrades"), 5);
        for (int i = 0; i < UPGRADES.length; i++) {
            Slot slot = menu.slots.get(menu.itemSlots() + i);
            ItemStack stack = slot.getItem();
            int x = slot.x, y = slot.y;
            gui().renderItemWithOverlay(g, stack, x, y, 1F, null);
            if (mouseX >= getGuiLeft() + x && mouseX < getGuiLeft() + x + 16 && mouseY >= getGuiTop() + y && mouseY < getGuiTop() + y + 16) {
                g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            }
            int count = stack.getCount();
            Component name = Component.translatable("hstg_labs.gui.upgrade.type", Component.translatable(UPGRADES[i].getTranslationKey()));
            drawScrollingString(g, name, x + 22, y - 1, mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT, titleTextColor(),
                  BasicLabMenu.UPGRADE_WINDOW_W - (x - relativeX) - 28, 0, false);
            Component detail = Component.translatable("hstg_labs.gui.upgrades.amount", count, UPGRADES[i].getMax()).append("  ")
                  .append(Component.translatable("hstg_labs.gui.upgrades.effect", String.format(java.util.Locale.ROOT, "%.2f",
                        UpgradeComponent.effect(UPGRADES[i], count))));
            drawScrollingString(g, detail, x + 22, y + 9, mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT, subheadingTextColor(),
                  BasicLabMenu.UPGRADE_WINDOW_W - (x - relativeX) - 28, 0, false);
        }
    }

    /** True when the mouse is over one of this window's two slots (screen coordinates). */
    boolean isOverSlot(Slot slot, double mouseX, double mouseY) {
        int x = getGuiLeft() + slot.x, y = getGuiTop() + slot.y;
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }
}
