package io.github.itsgiantcookie.hstglabs.client;

import io.github.itsgiantcookie.hstglabs.mek.client.MekGui;
import io.github.itsgiantcookie.hstglabs.lab.ResearchLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.mek.MekanismCompat;
import io.github.itsgiantcookie.hstglabs.mek.config.PortType;
import io.github.itsgiantcookie.hstglabs.mek.config.SideConfig;
import io.github.itsgiantcookie.hstglabs.mek.config.TransmissionKind;
import io.github.itsgiantcookie.hstglabs.lab.ResearchLabMenu;
import io.github.itsgiantcookie.hstglabs.packet.CycleFacePayload;
import io.github.itsgiantcookie.hstglabs.packet.LabButtonPayload;
import io.github.itsgiantcookie.hstglabs.mek.UpgradeComponent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Research lab screen, laid out like Mekanism's GuiElectricMachine and drawn with Mekanism's own
 * GUI textures (mekanism:gui/...):
 * <ul>
 *   <li>input (64,17) above the energy slot (64,53) with the up arrow (68,38), output (116,35)</li>
 *   <li>bar progress (86,38), vertical power bar (164,16), data bar mirrored at (6,16)</li>
 *   <li>left: side-config tab (-26,6) opening GuiSideConfiguration, energy info tab (-26,137)</li>
 *   <li>right: upgrade tab (176,6) opening GuiUpgradeWindow</li>
 * </ul>
 * Layering follows GuiMekanism: windows are drawn from renderLabels well above slot items, the
 * carried item and tooltips are pushed above the windows so nothing clips through. Slots under an
 * open window are deactivated through the menu.
 */
public class ResearchLabScreen extends AbstractContainerScreen<ResearchLabMenu> {

    private static final ResourceLocation SLOT_NORMAL = MekanismCompat.gui("slot/normal.png");
    private static final ResourceLocation SLOT_INPUT = MekanismCompat.gui("slot/input.png");
    private static final ResourceLocation SLOT_OUTPUT = MekanismCompat.gui("slot/output.png");
    private static final ResourceLocation SLOT_POWER = MekanismCompat.gui("slot/power.png");
    private static final ResourceLocation OVERLAY_POWER = MekanismCompat.gui("slot/overlay_power.png");
    private static final ResourceLocation POWER = MekanismCompat.gui("bar/vertical_power.png");
    private static final ResourceLocation PROGRESS_BAR = MekanismCompat.gui("progress/bar.png");
    private static final ResourceLocation INSTALLING = MekanismCompat.gui("progress/installing.png");
    private static final ResourceLocation UNINSTALLING = MekanismCompat.gui("progress/uninstalling.png");
    private static final ResourceLocation UP_ARROW = MekanismCompat.gui("up_arrow.png");
    private static final ResourceLocation CONFIG_ICON = MekanismCompat.gui("configuration.png");
    private static final ResourceLocation UPGRADE_ICON = MekanismCompat.gui("upgrade.png");
    private static final ResourceLocation ENERGY_TAB = MekanismCompat.gui("tabs/energy_info_j.png");
    private static final ResourceLocation CLOSE = MekanismCompat.gui("button/close.png");
    private static final ResourceLocation CLEAR_SIDES = MekanismCompat.gui("button/clear_sides.png");
    private static final ResourceLocation AUTO_EJECT = MekanismCompat.gui("button/auto_eject.png");
    private static final ResourceLocation UPGRADE_SELECTION = MekanismCompat.gui("upgrade_selection.png");
    private static final ResourceLocation SCROLL_LIST = MekanismCompat.gui("scroll_list.png");

    // z layers (GuiMekanism: foreground at +400, windows above, tooltips above everything)
    private static final int WINDOW_Z = 400, CARRIED_Z = 500, TOOLTIP_Z = 700;

    // bars: GuiBar adds a 1 px frame around the 4x52 fill
    private static final int POWER_X = 164, DATA_X = 6, BAR_Y = 16, BAR_W = 6, BAR_H = 54, FILL_H = 52;
    private static final int ARROW_X = 68, ARROW_Y = 38;
    private static final int PROGRESS_X = 86, PROGRESS_Y = 38, PROGRESS_W = 25, PROGRESS_H = 9;
    // GuiInsetElement tabs: 26 tall holder, 18 px inner button at border 4, shifted 1 px toward the panel
    private static final int TAB_H = 26, TAB_INNER = 18;
    private static final int CONFIG_TAB_X = -26, CONFIG_TAB_Y = 6;
    private static final int UPGRADE_TAB_X = 176, UPGRADE_TAB_Y = 6;
    private static final int ENERGY_TAB_X = -26, ENERGY_TAB_Y = 137;
    private static final int CLOSE_X = 6, CLOSE_Y = 6, CLOSE_SIZE = 8;

    // GuiSideConfiguration window
    private static final int SIDE_W = 156, SIDE_H = 135, SIDE_X = (176 - SIDE_W) / 2, SIDE_Y = 15;
    private static final int EJECT_X = 136, EJECT_Y = 6, CLEAR_X = 136, CLEAR_Y = 95, SMALL_BUTTON = 14;
    private static final int SCREEN_X = 38, SCREEN_Y = 25, SCREEN_W = 80, SCREEN_H = 12;
    private static final int FACE_SIZE = 22;
    private static final Map<RelativeSide, int[]> FACE_POS = new EnumMap<>(RelativeSide.class);

    // GuiUpgradeWindow (positions relative to the window)
    private static final int UPG_X = ResearchLabMenu.UPGRADE_WINDOW_X, UPG_Y = ResearchLabMenu.UPGRADE_WINDOW_Y;
    private static final int UPG_W = ResearchLabMenu.UPGRADE_WINDOW_W, UPG_H = ResearchLabMenu.UPGRADE_WINDOW_H;
    private static final int LIST_X = 6, LIST_Y = 18, LIST_W = 108, LIST_H = 50, ELEMENT_H = 12;
    private static final int INFO_X = 114, INFO_Y = 18, INFO_W = 59, INFO_H = 50;
    private static final int REMOVE_X = 115, REMOVE_Y = 54, REMOVE_W = 56, REMOVE_H = 12;
    private static final int SUPPORTED_X = 6, SUPPORTED_Y = 68, SUPPORTED_W = 167, SUPPORTED_H = 14, ICON = 12;
    private static final int UPG_SLOT_X = 175, UPG_IN_Y = 18, UPG_OUT_Y = 72, INSTALL_X = 176, INSTALL_Y = 37, UNINSTALL_Y = 58;

    private static final int DATA_FILL = TransmissionKind.DATA_COLOR, DATA_FILL_BOTTOM = 0xFF2ABEA2;

    static {
        // Mekanism's positions in GuiSideConfiguration
        FACE_POS.put(RelativeSide.TOP, new int[]{67, 46});
        FACE_POS.put(RelativeSide.LEFT, new int[]{44, 69});
        FACE_POS.put(RelativeSide.FRONT, new int[]{67, 69});
        FACE_POS.put(RelativeSide.RIGHT, new int[]{90, 69});
        FACE_POS.put(RelativeSide.BACK, new int[]{44, 92});
        FACE_POS.put(RelativeSide.BOTTOM, new int[]{67, 92});
    }

    private enum Window { NONE, SIDE_CONFIG, UPGRADES }

    private Window window = Window.NONE;
    /** Selected transmission kind; Mekanism opens on the first one (items). */
    private TransmissionKind currentKind = TransmissionKind.ITEM;
    @Nullable
    private Upgrade selectedUpgrade;
    /** Blocks next to each face, shown inside the face buttons like Mekanism's SideDataButton. */
    private final Map<RelativeSide, ItemStack> neighbors = new EnumMap<>(RelativeSide.class);
    private int mouseX, mouseY;

    public ResearchLabScreen(ResearchLabMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        syncWindowState();
    }

    @Override
    public void removed() {
        menu.setWindowState(null, false);
        super.removed();
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        graphics.pose().pushPose();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, TOOLTIP_Z);
        renderTooltips(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        MekGui.panel(graphics, x, y, imageWidth, imageHeight);

        // window tabs; hidden while a window is open, the windows (and their type tabs) cover their spots
        if (window == Window.NONE) {
            drawTab(graphics, x + CONFIG_TAB_X, y + CONFIG_TAB_Y, true, 0xFFFFFFFF, CONFIG_ICON, hoverState(mouseX, mouseY, configTabButtonX(), configTabButtonY()));
            drawTab(graphics, x + UPGRADE_TAB_X, y + UPGRADE_TAB_Y, false, 0xFFFFFFFF, UPGRADE_ICON, hoverState(mouseX, mouseY, upgradeTabButtonX(), upgradeTabButtonY()));
        }
        graphics.blit(ENERGY_TAB, x + ENERGY_TAB_X, y + ENERGY_TAB_Y, 0, 0, 26, 26, 26, 26);

        ResearchLabBlockEntity lab = menu.getLab();
        for (Slot slot : menu.slots) {
            if (slot.index >= ResearchLabMenu.SLOT_UPGRADE_IN && slot.index <= ResearchLabMenu.SLOT_UPGRADE_OUT) {
                continue; // drawn by the upgrade window
            }
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            graphics.blit(slotFrame(lab, slot.index), sx, sy, 0, 0, 18, 18, 18, 18);
            if (slot.index == ResearchLabBlockEntity.SLOT_ENERGY) {
                graphics.blit(OVERLAY_POWER, sx, sy, 0, 0, 18, 18, 18, 18);
            }
        }

        graphics.blit(UP_ARROW, x + ARROW_X, y + ARROW_Y, 0, 0, 8, 10, 8, 10);

        // ProgressType.BAR: 25x9, fill row at v=9 with a 1 px inner offset
        graphics.blit(PROGRESS_BAR, x + PROGRESS_X, y + PROGRESS_Y, 0, 0, PROGRESS_W, PROGRESS_H, PROGRESS_W, 27);
        int progressPx = (int) (Math.min(1, menu.getProgress()) * (PROGRESS_W - 2));
        if (progressPx > 0) {
            graphics.blit(PROGRESS_BAR, x + PROGRESS_X + 1, y + PROGRESS_Y, 1, PROGRESS_H, progressPx, PROGRESS_H, PROGRESS_W, 27);
        }

        // GuiVerticalPowerBar
        MekGui.barFrame(graphics, x + POWER_X, y + BAR_Y, BAR_W, BAR_H);
        int energyPx = scaled(menu.getEnergy(), menu.getMaxEnergy());
        if (energyPx > 0) {
            graphics.blit(POWER, x + POWER_X + 1, y + BAR_Y + BAR_H - 1 - energyPx, 4, energyPx, 0, 0, 4, energyPx, 4, FILL_H);
        }

        // data bar: same component, filled with the data color
        MekGui.barFrame(graphics, x + DATA_X, y + BAR_Y, BAR_W, BAR_H);
        int dataPx = scaled(menu.getStoredData(), ResearchLabBlockEntity.MAX_DATA);
        if (dataPx > 0) {
            int left = x + DATA_X + 1, bottom = y + BAR_Y + BAR_H - 1;
            graphics.fill(left, bottom - dataPx, left + 4, bottom, DATA_FILL);
            if (dataPx == FILL_H) {
                graphics.fill(left, bottom - 1, left + 4, bottom, DATA_FILL_BOTTOM);
            }
        }
    }

    /** GuiMekanism.addSlots: a slot is framed in the color of the item port type that serves it, if any face uses it. */
    private static ResourceLocation slotFrame(@Nullable ResearchLabBlockEntity lab, int index) {
        return switch (index) {
            case ResearchLabBlockEntity.SLOT_INPUT -> lab != null && lab.isPortUsed(TransmissionKind.ITEM, PortType.INPUT) ? SLOT_INPUT : SLOT_NORMAL;
            case ResearchLabBlockEntity.SLOT_OUTPUT -> lab != null && lab.isPortUsed(TransmissionKind.ITEM, PortType.OUTPUT) ? SLOT_OUTPUT : SLOT_NORMAL;
            case ResearchLabBlockEntity.SLOT_ENERGY -> SLOT_POWER;
            default -> SLOT_NORMAL;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int ignoredX, int ignoredY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, MekGui.TEXT_TITLE, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MekGui.TEXT_TITLE, false);
        if (window != Window.NONE) {
            graphics.pose().pushPose();
            graphics.pose().translate(-leftPos, -topPos, WINDOW_Z);
            if (window == Window.SIDE_CONFIG) {
                renderSideConfig(graphics, mouseX, mouseY);
            } else {
                renderUpgrades(graphics, mouseX, mouseY);
            }
            graphics.pose().popPose();
        }
        // Like GuiMekanism: leak a z shift so the carried item (drawn after the labels) stays above the windows.
        graphics.pose().translate(0, 0, CARRIED_Z);
    }

    // --- side configuration window ---

    private void renderSideConfig(GuiGraphics graphics, int mouseX, int mouseY) {
        int wx = leftPos + SIDE_X, wy = topPos + SIDE_Y;
        windowFrame(graphics, wx, wy, SIDE_W, SIDE_H, mouseX, mouseY);

        // GuiConfigTypeTab: one per kind at (-26, 2 + 28 i); the selected kind's tab is hidden
        TransmissionKind[] kinds = TransmissionKind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != currentKind) {
                int tx = wx - 26, ty = wy + 2 + 28 * i;
                drawTab(graphics, tx, ty, true, kinds[i].tabColor(), kinds[i].icon(), hoverState(mouseX, mouseY, tx + 5, ty + 4));
            }
        }

        Component heading = Component.translatable("hstg_labs.gui.config_type", currentKind.displayName());
        drawCentered(graphics, heading, wx + 12, wx + EJECT_X - 2, wy + 5, MekGui.TEXT_TITLE);

        // auto-eject button + eject state on the inner screen
        boolean canToggle = SideConfig.canToggleEject(currentKind);
        int ejectState = !canToggle ? MekGui.BUTTON_DISABLED : hoverState(mouseX, mouseY, wx + EJECT_X, wy + EJECT_Y, SMALL_BUTTON);
        MekGui.button(graphics, wx + EJECT_X, wy + EJECT_Y, SMALL_BUTTON, SMALL_BUTTON, ejectState);
        graphics.blit(AUTO_EJECT, wx + EJECT_X, wy + EJECT_Y, 0, 0, SMALL_BUTTON, SMALL_BUTTON, SMALL_BUTTON, SMALL_BUTTON);
        MekGui.innerScreen(graphics, wx + SCREEN_X, wy + SCREEN_Y, SCREEN_W, SCREEN_H);
        drawClipped(graphics, ejectText(), wx + SCREEN_X + 3, wx + SCREEN_X + SCREEN_W - 3, wy + SCREEN_Y + 3, MekGui.TEXT_SCREEN);

        MekGui.button(graphics, wx + CLEAR_X, wy + CLEAR_Y, SMALL_BUTTON, SMALL_BUTTON, hoverState(mouseX, mouseY, wx + CLEAR_X, wy + CLEAR_Y, SMALL_BUTTON));
        graphics.blit(CLEAR_SIDES, wx + CLEAR_X, wy + CLEAR_Y, 0, 0, SMALL_BUTTON, SMALL_BUTTON, SMALL_BUTTON, SMALL_BUTTON);

        RelativeSide hovered = faceAt(mouseX, mouseY);
        ResearchLabBlockEntity lab = menu.getLab();
        for (Map.Entry<RelativeSide, int[]> entry : FACE_POS.entrySet()) {
            RelativeSide side = entry.getKey();
            int bx = wx + entry.getValue()[0], by = wy + entry.getValue()[1];
            PortType type = lab != null ? lab.getPort(currentKind, side) : PortType.NONE;
            MekGui.colorButton(graphics, bx, by, FACE_SIZE, FACE_SIZE, side == hovered ? MekGui.BUTTON_HOVERED : MekGui.BUTTON_NORMAL, type.color());
            ItemStack neighbor = neighbors.getOrDefault(side, ItemStack.EMPTY);
            if (!neighbor.isEmpty()) {
                graphics.renderItem(neighbor, bx + 3, by + 3);
            }
        }
        drawCentered(graphics, Component.translatable("hstg_labs.gui.side_config.sides"), wx + 4, wx + SIDE_W - 4, wy + 120, MekGui.TEXT_SUBHEADING);
    }

    private Component ejectText() {
        if (currentKind == TransmissionKind.DATA) {
            return Component.translatable("hstg_labs.gui.eject", Component.translatable("hstg_labs.gui.on"));
        } else if (!SideConfig.canToggleEject(currentKind)) {
            return Component.translatable("hstg_labs.gui.eject.none");
        }
        ResearchLabBlockEntity lab = menu.getLab();
        boolean on = lab != null && lab.isEjecting(currentKind);
        return Component.translatable("hstg_labs.gui.eject", Component.translatable(on ? "hstg_labs.gui.on" : "hstg_labs.gui.off"));
    }

    // --- upgrade window ---

    private void renderUpgrades(GuiGraphics graphics, int mouseX, int mouseY) {
        int wx = leftPos + UPG_X, wy = topPos + UPG_Y;
        windowFrame(graphics, wx, wy, UPG_W, UPG_H, mouseX, mouseY);
        drawCentered(graphics, Component.translatable("hstg_labs.gui.upgrades"), wx + 12, wx + UPG_W, wy + 5, MekGui.TEXT_TITLE);

        // GuiUpgradeScrollList: element holder, scroll bar, one 12 px row per installed type
        int lx = wx + LIST_X, ly = wy + LIST_Y;
        MekGui.elementHolder(graphics, lx, ly, LIST_W, LIST_H);
        int barX = lx + LIST_W - 6, barY = ly + 2, maxBar = LIST_H - 4;
        graphics.blit(SCROLL_LIST, barX - 1, barY - 1, 0, 0, 6, 1, 6, 6);
        graphics.blit(SCROLL_LIST, barX - 1, barY, 6, maxBar, 0, 1, 6, 1, 6, 6);
        graphics.blit(SCROLL_LIST, barX - 1, ly + maxBar + 2, 0, 0, 6, 1, 6, 6);
        graphics.blit(SCROLL_LIST, barX, barY, 0, 2, 4, 4, 6, 6);
        List<Upgrade> installed = installedUpgrades();
        if (selectedUpgrade != null && !installed.contains(selectedUpgrade)) {
            selectedUpgrade = null;
        }
        for (int i = 0; i < installed.size(); i++) {
            Upgrade upgrade = installed.get(i);
            int ey = ly + 1 + i * ELEMENT_H;
            int row = upgrade == selectedUpgrade ? 2 : isIn(mouseX, mouseY, lx + 1, ey, LIST_W - 8, ELEMENT_H) ? 0 : 1;
            int[] rgb = upgrade.getColor().getRgbCode();
            graphics.setColor(rgb[0] / 255F, rgb[1] / 255F, rgb[2] / 255F, 1F);
            graphics.blit(UPGRADE_SELECTION, lx + 1, ey, 0, row * ELEMENT_H, 100, ELEMENT_H, 100, 36);
            graphics.setColor(1F, 1F, 1F, 1F);
            renderScaledItem(graphics, UpgradeComponent.stackOf(upgrade, 1), lx + 3, ey + 2, 0.5F);
            drawClipped(graphics, upgradeName(upgrade), lx + 13, lx + LIST_W - 8, ey + 2, MekGui.TEXT_TITLE);
        }

        // right inner screen: selection info
        int ix = wx + INFO_X, iy = wy + INFO_Y;
        MekGui.innerScreen(graphics, ix, iy, INFO_W, INFO_H);
        if (selectedUpgrade != null) {
            int count = menu.getUpgrades(selectedUpgrade);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("hstg_labs.gui.upgrade.type", upgradeName(selectedUpgrade)));
            lines.add(Component.translatable("hstg_labs.gui.upgrades.amount", count, selectedUpgrade.getMax()));
            lines.add(Component.translatable("hstg_labs.gui.upgrades.effect", UpgradeComponent.effect(selectedUpgrade, count)));
            drawScaledLines(graphics, lines, ix + 2, iy + 2, INFO_W - 4, 0.6F);
        } else {
            drawScaledLines(graphics, List.of(Component.translatable("hstg_labs.gui.upgrades.no_selection")), ix + 2, iy + 2, INFO_W - 4, 0.8F);
        }
        int removeState = selectedUpgrade == null ? MekGui.BUTTON_DISABLED : hoverState(mouseX, mouseY, wx + REMOVE_X, wy + REMOVE_Y, REMOVE_W, REMOVE_H);
        MekGui.digitalButton(graphics, font, wx + REMOVE_X, wy + REMOVE_Y, REMOVE_W, REMOVE_H, removeState, Component.translatable("hstg_labs.gui.upgrade.uninstall"));

        // GuiSupportedUpgrades: every Mekanism upgrade at 0.75 scale, unsupported ones greyed out
        int sx = wx + SUPPORTED_X, sy = wy + SUPPORTED_Y;
        MekGui.elementHolder(graphics, sx, sy, SUPPORTED_W, SUPPORTED_H);
        Component supported = Component.translatable("hstg_labs.gui.upgrades.supported");
        graphics.drawString(font, supported, sx + 2, sy + 3, MekGui.TEXT_TITLE, false);
        int firstRowStart = supportedStart();
        Upgrade[] all = Upgrade.values();
        for (int i = 0; i < all.length; i++) {
            int px = sx + 1 + firstRowStart + i * ICON, py = sy + 1;
            renderScaledItem(graphics, UpgradeComponent.stackOf(all[i], 1), px, py, 0.75F);
            if (!UpgradeComponent.SUPPORTED.contains(all[i])) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(px, py, px + ICON, py + ICON, 0x80787878);
                graphics.pose().popPose();
            }
        }

        // install / uninstall progress and the two virtual slots
        int install = (int) (Math.min(1, menu.getUpgradeProgress()) * 14);
        graphics.blit(INSTALLING, wx + INSTALL_X, wy + INSTALL_Y, 0, 0, 10, 14, 30, 14);
        if (install > 0) {
            graphics.blit(INSTALLING, wx + INSTALL_X, wy + INSTALL_Y, 10, 0, 10, install, 30, 14);
        }
        graphics.blit(UNINSTALLING, wx + INSTALL_X, wy + UNINSTALL_Y, 0, 0, 12, 12, 36, 12);
        renderWindowSlot(graphics, ResearchLabMenu.SLOT_UPGRADE_IN, wx + UPG_SLOT_X, wy + UPG_IN_Y, mouseX, mouseY);
        renderWindowSlot(graphics, ResearchLabMenu.SLOT_UPGRADE_OUT, wx + UPG_SLOT_X, wy + UPG_OUT_Y, mouseX, mouseY);
    }

    /** GuiVirtualSlot: frame, item and hover highlight drawn inside the window (above the window panel). */
    private void renderWindowSlot(GuiGraphics graphics, int index, int frameX, int frameY, int mouseX, int mouseY) {
        graphics.blit(SLOT_NORMAL, frameX, frameY, 0, 0, 18, 18, 18, 18);
        ItemStack stack = menu.slots.get(index).getItem();
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, frameX + 1, frameY + 1);
            graphics.renderItemDecorations(font, stack, frameX + 1, frameY + 1);
        }
        if (isIn(mouseX, mouseY, frameX + 1, frameY + 1, 16, 16)) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.fill(frameX + 1, frameY + 1, frameX + 17, frameY + 17, 0x80FFFFFF);
            graphics.pose().popPose();
        }
    }

    private List<Upgrade> installedUpgrades() {
        List<Upgrade> list = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            if (menu.getUpgrades(upgrade) > 0) {
                list.add(upgrade);
            }
        }
        return list;
    }

    private static Component upgradeName(Upgrade upgrade) {
        return Component.translatable(upgrade.getTranslationKey());
    }

    private int supportedStart() {
        return Math.min(font.width(Component.translatable("hstg_labs.gui.upgrades.supported")) + 1, SUPPORTED_W - 2);
    }

    // --- shared window drawing ---

    /** GuiWindow: shadow, panel, close button. */
    private void windowFrame(GuiGraphics graphics, int wx, int wy, int w, int h, int mouseX, int mouseY) {
        MekGui.windowShadow(graphics, wx, wy, w, h);
        MekGui.panel(graphics, wx, wy, w, h);
        MekGui.button(graphics, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, hoverState(mouseX, mouseY, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE));
        graphics.blit(CLOSE, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, 0, 0, 12, 12, 12, 12);
    }

    /** GuiInsetElement: tinted side holder, 18 px button, 18 px icon. */
    private static void drawTab(GuiGraphics graphics, int x, int y, boolean left, int color, ResourceLocation icon, int state) {
        graphics.setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
        if (left) {
            MekGui.leftHolder(graphics, x, y, TAB_H);
        } else {
            MekGui.rightHolder(graphics, x, y, TAB_H);
        }
        graphics.setColor(1F, 1F, 1F, 1F);
        int bx = x + (left ? 5 : 3);
        MekGui.button(graphics, bx, y + 4, TAB_INNER, TAB_INNER, state);
        graphics.blit(icon, bx, y + 4, 0, 0, TAB_INNER, TAB_INNER, TAB_INNER, TAB_INNER);
    }

    private void renderScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, scale);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private void drawScaledLines(GuiGraphics graphics, List<Component> lines, int x, int y, int width, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        int line = 0;
        for (Component text : lines) {
            for (FormattedCharSequence part : font.split(text, (int) (width / scale))) {
                graphics.drawString(font, part, 0, line * 10, MekGui.TEXT_SCREEN, false);
                line++;
            }
        }
        graphics.pose().popPose();
    }

    private void drawCentered(GuiGraphics graphics, Component text, int left, int right, int y, int color) {
        int width = font.width(text);
        if (width <= right - left) {
            graphics.drawString(font, text, left + (right - left - width) / 2, y, color, false);
        } else {
            drawClipped(graphics, text, left, right, y, color);
        }
    }

    /** GUI-04: text that does not fit is cut at the element edge instead of running over it. */
    private void drawClipped(GuiGraphics graphics, Component text, int left, int right, int y, int color) {
        graphics.enableScissor(left, y - 1, right, y + font.lineHeight);
        graphics.drawString(font, text, left, y, color, false);
        graphics.disableScissor();
    }

    // --- tooltips ---

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (window == Window.SIDE_CONFIG && overWindow(mouseX, mouseY)) {
            sideConfigTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (window == Window.UPGRADES && overWindow(mouseX, mouseY)) {
            upgradeTooltip(graphics, mouseX, mouseY);
            return;
        }
        int x = leftPos, y = topPos;
        if (isIn(mouseX, mouseY, x + POWER_X, y + BAR_Y, BAR_W, BAR_H)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.energy",
                  formatted(menu.getEnergy()), formatted(menu.getMaxEnergy())), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, x + DATA_X, y + BAR_Y, BAR_W, BAR_H)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.data",
                  formatted(menu.getStoredData()), formatted(ResearchLabBlockEntity.MAX_DATA)), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_W, PROGRESS_H)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.progress", (int) (menu.getProgress() * 100)), mouseX, mouseY);
        } else if (window == Window.NONE && isIn(mouseX, mouseY, configTabButtonX(), configTabButtonY(), TAB_INNER, TAB_INNER)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.side_config"), mouseX, mouseY);
        } else if (window == Window.NONE && isIn(mouseX, mouseY, upgradeTabButtonX(), upgradeTabButtonY(), TAB_INNER, TAB_INNER)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.upgrades"), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, x + ENERGY_TAB_X, y + ENERGY_TAB_Y, 26, 26)) {
            graphics.renderComponentTooltip(font, List.of(
                  Component.translatable("hstg_labs.gui.energy.using", formatted(menu.getEnergyUsed())),
                  Component.translatable("hstg_labs.gui.energy.needed", formatted(menu.getMaxEnergy() - menu.getEnergy()))),
                  mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void sideConfigTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int wx = leftPos + SIDE_X, wy = topPos + SIDE_Y;
        TransmissionKind tabKind = typeTabAt(mouseX, mouseY);
        RelativeSide side = faceAt(mouseX, mouseY);
        ResearchLabBlockEntity lab = menu.getLab();
        if (tabKind != null) {
            graphics.renderTooltip(font, tabKind.displayName(), mouseX, mouseY);
        } else if (side != null && lab != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(side.getTranslationKey()));
            lines.add(lab.getPort(currentKind, side).displayName());
            ItemStack neighbor = neighbors.getOrDefault(side, ItemStack.EMPTY);
            if (!neighbor.isEmpty()) {
                lines.add(neighbor.getHoverName().copy().withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("hstg_labs.gui.side_config.hint").withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.close"), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, wx + EJECT_X, wy + EJECT_Y, SMALL_BUTTON, SMALL_BUTTON)) {
            graphics.renderTooltip(font, Component.translatable(SideConfig.canToggleEject(currentKind) ? "hstg_labs.gui.auto_eject"
                                                                                                     : "hstg_labs.gui.eject.none.tooltip"), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, wx + CLEAR_X, wy + CLEAR_Y, SMALL_BUTTON, SMALL_BUTTON)) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable("hstg_labs.gui.clear_sides"),
                  Component.translatable("hstg_labs.gui.clear_sides.hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }
    }

    private void upgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int wx = leftPos + UPG_X, wy = topPos + UPG_Y;
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        Upgrade listed = upgradeRowAt(mouseX, mouseY);
        if (listed != null) {
            graphics.renderTooltip(font, listed.getDescription(), mouseX, mouseY);
            return;
        }
        int sx = wx + SUPPORTED_X + 1 + supportedStart(), sy = wy + SUPPORTED_Y + 1;
        Upgrade[] all = Upgrade.values();
        for (int i = 0; i < all.length; i++) {
            if (isIn(mouseX, mouseY, sx + i * ICON, sy, ICON, ICON)) {
                Component name = Component.translatable("hstg_labs.gui.upgrade.type", upgradeName(all[i])).withStyle(ChatFormatting.YELLOW);
                Component first = UpgradeComponent.SUPPORTED.contains(all[i]) ? name
                                                                               : Component.translatable("hstg_labs.gui.upgrade.not_supported", name).withStyle(ChatFormatting.RED);
                graphics.renderComponentTooltip(font, List.of(first, all[i].getDescription()), mouseX, mouseY);
                return;
            }
        }
        if (isIn(mouseX, mouseY, wx + REMOVE_X, wy + REMOVE_Y, REMOVE_W, REMOVE_H)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.upgrade.uninstall.tooltip"), mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            graphics.renderTooltip(font, Component.translatable("hstg_labs.gui.close"), mouseX, mouseY);
        }
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT, right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (window == Window.SIDE_CONFIG && overWindow(mouseX, mouseY)) {
            clickSideConfig(mouseX, mouseY, left, right);
            return true; // the window swallows clicks
        }
        if (window == Window.UPGRADES && overWindow(mouseX, mouseY)) {
            if (isOverUpgradeSlot(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button); // virtual slots are real menu slots
            }
            clickUpgrades(mouseX, mouseY, left);
            return true;
        }
        if (window == Window.NONE && left) {
            if (isIn(mouseX, mouseY, configTabButtonX(), configTabButtonY(), TAB_INNER, TAB_INNER)) {
                openWindow(Window.SIDE_CONFIG);
                return true;
            } else if (isIn(mouseX, mouseY, upgradeTabButtonX(), upgradeTabButtonY(), TAB_INNER, TAB_INNER)) {
                openWindow(Window.UPGRADES);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clickSideConfig(double mouseX, double mouseY, boolean left, boolean right) {
        int wx = leftPos + SIDE_X, wy = topPos + SIDE_Y;
        TransmissionKind tabKind = typeTabAt(mouseX, mouseY);
        if (tabKind != null) {
            if (left) {
                currentKind = tabKind;
                playClick();
            }
        } else if (left && isIn(mouseX, mouseY, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            closeWindow();
        } else if (left && SideConfig.canToggleEject(currentKind) && isIn(mouseX, mouseY, wx + EJECT_X, wy + EJECT_Y, SMALL_BUTTON, SMALL_BUTTON)) {
            PacketDistributor.sendToServer(new LabButtonPayload(menu.getPos(), LabButtonPayload.Button.TOGGLE_EJECT, currentKind.ordinal(), false));
            playClick();
        } else if ((left || right) && isIn(mouseX, mouseY, wx + CLEAR_X, wy + CLEAR_Y, SMALL_BUTTON, SMALL_BUTTON)) {
            sendFace(Optional.empty(), left);
        } else {
            RelativeSide side = faceAt(mouseX, mouseY);
            if (side != null && (left || right)) {
                sendFace(Optional.of(side), left);
            }
        }
    }

    private void clickUpgrades(double mouseX, double mouseY, boolean left) {
        if (!left) {
            return;
        }
        int wx = leftPos + UPG_X, wy = topPos + UPG_Y;
        if (isIn(mouseX, mouseY, wx + CLOSE_X, wy + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            closeWindow();
        } else if (selectedUpgrade != null && isIn(mouseX, mouseY, wx + REMOVE_X, wy + REMOVE_Y, REMOVE_W, REMOVE_H)) {
            PacketDistributor.sendToServer(new LabButtonPayload(menu.getPos(), LabButtonPayload.Button.REMOVE_UPGRADE, selectedUpgrade.ordinal(),
                  Screen.hasShiftDown()));
            playClick();
        } else if (isIn(mouseX, mouseY, wx + LIST_X + 1, wy + LIST_Y + 1, LIST_W - 8, LIST_H - 2)) {
            // GuiScrollList.onClick: select the row under the mouse, clear when clicking empty space
            selectedUpgrade = upgradeRowAt(mouseX, mouseY);
        }
    }

    private void sendFace(Optional<RelativeSide> side, boolean forward) {
        PacketDistributor.sendToServer(new CycleFacePayload(menu.getPos(), currentKind, side, forward));
        playClick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // like Mekanism: escape closes the top window before the screen
        if (window != Window.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeWindow();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // tabs and windows reach past the panel; clicks on them are not "outside" (no item drop)
        return super.hasClickedOutside(mouseX, mouseY, left, top, button)
               && !isIn(mouseX, mouseY, left + CONFIG_TAB_X, top + CONFIG_TAB_Y, 26, TAB_H)
               && !isIn(mouseX, mouseY, left + UPGRADE_TAB_X, top + UPGRADE_TAB_Y, 26, TAB_H)
               && !isIn(mouseX, mouseY, left + ENERGY_TAB_X, top + ENERGY_TAB_Y, 26, 26)
               && !overWindow(mouseX, mouseY);
    }

    private void openWindow(Window target) {
        window = target;
        if (target == Window.SIDE_CONFIG) {
            refreshNeighbors();
        }
        syncWindowState();
        playClick();
    }

    private void closeWindow() {
        window = Window.NONE;
        syncWindowState();
        playClick();
    }

    /** Tell the menu which slots the open window covers and whether the upgrade slots exist. */
    private void syncWindowState() {
        int[] area = switch (window) {
            case SIDE_CONFIG -> new int[]{SIDE_X - 26, SIDE_Y, SIDE_W + 26, SIDE_H};
            case UPGRADES -> new int[]{UPG_X, UPG_Y, UPG_W, UPG_H};
            case NONE -> null;
        };
        menu.setWindowState(area, window == Window.UPGRADES);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    private void refreshNeighbors() {
        neighbors.clear();
        ResearchLabBlockEntity lab = menu.getLab();
        Level level = lab != null ? lab.getLevel() : null;
        if (level == null || minecraft == null || minecraft.player == null) {
            return;
        }
        for (RelativeSide side : RelativeSide.values()) {
            Direction dir = side.getDirection(lab.getFacing());
            BlockPos pos = lab.getBlockPos().relative(dir);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                BlockHitResult hit = new BlockHitResult(pos.getCenter().relative(dir.getOpposite(), 0.5), dir.getOpposite(), pos, false);
                neighbors.put(side, state.getCloneItemStack(hit, level, pos, minecraft.player));
            }
        }
    }

    // --- hit testing ---

    private int configTabButtonX() {
        return leftPos + CONFIG_TAB_X + 5;
    }

    private int configTabButtonY() {
        return topPos + CONFIG_TAB_Y + 4;
    }

    private int upgradeTabButtonX() {
        return leftPos + UPGRADE_TAB_X + 3;
    }

    private int upgradeTabButtonY() {
        return topPos + UPGRADE_TAB_Y + 4;
    }

    /** The open window, including the side-config type tabs hanging off its left edge. */
    private boolean overWindow(double mouseX, double mouseY) {
        return switch (window) {
            case SIDE_CONFIG -> isIn(mouseX, mouseY, leftPos + SIDE_X, topPos + SIDE_Y, SIDE_W, SIDE_H) || typeTabHolderAt(mouseX, mouseY);
            case UPGRADES -> isIn(mouseX, mouseY, leftPos + UPG_X, topPos + UPG_Y, UPG_W, UPG_H);
            case NONE -> false;
        };
    }

    private boolean isOverUpgradeSlot(double mouseX, double mouseY) {
        int fx = leftPos + UPG_X + UPG_SLOT_X;
        return isIn(mouseX, mouseY, fx, topPos + UPG_Y + UPG_IN_Y, 18, 18) || isIn(mouseX, mouseY, fx, topPos + UPG_Y + UPG_OUT_Y, 18, 18);
    }

    private boolean typeTabHolderAt(double mouseX, double mouseY) {
        TransmissionKind[] kinds = TransmissionKind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != currentKind && isIn(mouseX, mouseY, leftPos + SIDE_X - 26, topPos + SIDE_Y + 2 + 28 * i, 26, TAB_H)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private TransmissionKind typeTabAt(double mouseX, double mouseY) {
        if (window != Window.SIDE_CONFIG) {
            return null;
        }
        TransmissionKind[] kinds = TransmissionKind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != currentKind && isIn(mouseX, mouseY, leftPos + SIDE_X - 26 + 5, topPos + SIDE_Y + 2 + 28 * i + 4, TAB_INNER, TAB_INNER)) {
                return kinds[i];
            }
        }
        return null;
    }

    @Nullable
    private RelativeSide faceAt(double mouseX, double mouseY) {
        if (window != Window.SIDE_CONFIG) {
            return null;
        }
        for (Map.Entry<RelativeSide, int[]> entry : FACE_POS.entrySet()) {
            if (isIn(mouseX, mouseY, leftPos + SIDE_X + entry.getValue()[0], topPos + SIDE_Y + entry.getValue()[1], FACE_SIZE, FACE_SIZE)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private Upgrade upgradeRowAt(double mouseX, double mouseY) {
        if (window != Window.UPGRADES) {
            return null;
        }
        int lx = leftPos + UPG_X + LIST_X, ly = topPos + UPG_Y + LIST_Y;
        List<Upgrade> installed = installedUpgrades();
        for (int i = 0; i < installed.size() && i < (LIST_H - 2) / ELEMENT_H; i++) {
            if (isIn(mouseX, mouseY, lx + 1, ly + 1 + i * ELEMENT_H, LIST_W - 8, ELEMENT_H)) {
                return installed.get(i);
            }
        }
        return null;
    }

    private int hoverState(int mouseX, int mouseY, int x, int y) {
        return hoverState(mouseX, mouseY, x, y, TAB_INNER);
    }

    private int hoverState(int mouseX, int mouseY, int x, int y, int size) {
        return hoverState(mouseX, mouseY, x, y, size, size);
    }

    private static int hoverState(int mouseX, int mouseY, int x, int y, int w, int h) {
        return isIn(mouseX, mouseY, x, y, w, h) ? MekGui.BUTTON_HOVERED : MekGui.BUTTON_NORMAL;
    }

    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return MekGui.isIn(mx, my, x, y, w, h);
    }

    private static int scaled(long value, long max) {
        return max <= 0 ? 0 : (int) Math.min(FILL_H, value * FILL_H / max);
    }

    private static String formatted(long value) {
        return String.format("%,d", value);
    }
}
