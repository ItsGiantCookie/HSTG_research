package de.germanrangers.hstglabs.mek.spike.client;

import de.germanrangers.hstglabs.mek.spike.SpikeMekLabTile;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiChemicalGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * WP-05a spike, variant A screen (THROWAWAY): Mekanism's own GUI base. Side-config, upgrade,
 * redstone and security tabs come from the base classes; we add the gauge, the bar and the energy tab.
 */
public class SpikeMekLabScreen extends GuiConfigurableTile<SpikeMekLabTile, MekanismTileContainer<SpikeMekLabTile>> {

    public SpikeMekLabScreen(MekanismTileContainer<SpikeMekLabTile> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15));
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
        addRenderableWidget(new GuiChemicalGauge(tile::getChemicalTank, () -> tile.getChemicalTanks(null), GaugeType.STANDARD, this, 25, 13));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        renderInventoryText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
