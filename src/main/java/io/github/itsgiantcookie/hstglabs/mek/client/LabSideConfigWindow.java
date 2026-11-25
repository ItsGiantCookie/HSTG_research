package io.github.itsgiantcookie.hstglabs.mek.client;

import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.mek.MekanismCompat;
import io.github.itsgiantcookie.hstglabs.mek.config.PortType;
import io.github.itsgiantcookie.hstglabs.mek.config.TransmissionKind;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload;
import io.github.itsgiantcookie.hstglabs.packet.SideConfigPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import mekanism.api.RelativeSide;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.button.BasicColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * The basic lab's side configuration, laid out like Mekanism's {@code GuiSideConfiguration} (156×135,
 * a type tab per kind on the left, six face buttons, "clear sides"). It is a real Mekanism
 * {@link GuiWindow} (D33); the faces it shows come from the server's {@link LabStatusPayload} and
 * clicks go out as {@link SideConfigPayload}s.
 */
public class LabSideConfigWindow extends GuiWindow {

    public static final int WIDTH = 156, HEIGHT = 135;
    private static final int FACE = 22;
    /** Mekanism's face button positions in GuiSideConfiguration. */
    private static final Map<RelativeSide, int[]> FACE_POS = new EnumMap<>(RelativeSide.class);

    static {
        FACE_POS.put(RelativeSide.TOP, new int[]{67, 46});
        FACE_POS.put(RelativeSide.LEFT, new int[]{44, 69});
        FACE_POS.put(RelativeSide.FRONT, new int[]{67, 69});
        FACE_POS.put(RelativeSide.RIGHT, new int[]{90, 69});
        FACE_POS.put(RelativeSide.BACK, new int[]{44, 92});
        FACE_POS.put(RelativeSide.BOTTOM, new int[]{67, 92});
    }

    private final Supplier<Optional<LabStatusPayload>> status;
    private final int containerId;
    private final List<KindTab> tabs = new ArrayList<>();
    private TransmissionKind kind = BasicLabBlockEntity.SIDE_KINDS.get(0);

    public LabSideConfigWindow(IGuiWrapper gui, int x, int y, int containerId, Supplier<Optional<LabStatusPayload>> status) {
        super(gui, x, y, WIDTH, HEIGHT, SelectedWindowData.UNSPECIFIED);
        this.status = status;
        this.containerId = containerId;
        interactionStrategy = InteractionStrategy.NONE;
        addChild(new GuiInnerScreen(gui, relativeX + 38, relativeY + 25, 80, 12,
              () -> List.of(Component.translatable("hstg_labs.gui.eject.none"))).clearFormat()
              .tooltip(() -> List.of(Component.translatable("hstg_labs.gui.eject.none.tooltip"))));
        List<TransmissionKind> kinds = BasicLabBlockEntity.SIDE_KINDS;
        for (int i = 0; i < kinds.size(); i++) {
            tabs.add(addChild(new KindTab(gui, kinds.get(i), relativeX - 26, relativeY + 2 + 28 * i)));
        }
        MekanismImageButton clear = addChild(new MekanismImageButton(gui, relativeX + 136, relativeY + 95, 14, 14, 14, 14,
              MekanismCompat.gui("button/clear_sides.png"), (e, mx, my) -> send(Optional.empty(), true), (e, mx, my) -> send(Optional.empty(), false)));
        clear.setTooltip(Tooltip.create(Component.translatable("hstg_labs.gui.clear_sides.hint")));
        for (Map.Entry<RelativeSide, int[]> face : FACE_POS.entrySet()) {
            RelativeSide side = face.getKey();
            addChild(new BasicColorButton(gui, relativeX + face.getValue()[0], relativeY + face.getValue()[1], FACE, () -> port(side).color(),
                  (e, mx, my) -> send(Optional.of(side), true), (e, mx, my) -> send(Optional.of(side), false)) {
                @Override
                public void updateTooltip(int mouseX, int mouseY) {
                    setTooltip(Tooltip.create(Component.literal(side.name().charAt(0) + side.name().substring(1).toLowerCase(java.util.Locale.ROOT))
                          .append(": ").append(port(side).displayName()).append("\n")
                          .append(Component.translatable("hstg_labs.gui.side_config.hint"))));
                }
            });
        }
        updateTabs();
    }

    private PortType port(RelativeSide side) {
        return status.get().map(s -> s.port(kind, side)).orElse(PortType.NONE);
    }

    private boolean send(Optional<RelativeSide> side, boolean forward) {
        boolean all = side.isEmpty() && Screen.hasShiftDown();
        for (TransmissionKind k : all ? BasicLabBlockEntity.SIDE_KINDS : List.of(kind)) {
            PacketDistributor.sendToServer(new SideConfigPayload(containerId, k, side, forward));
        }
        return true;
    }

    private void updateTabs() {
        tabs.forEach(t -> t.visible = t.kind != kind);
    }

    @Override
    public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderForeground(guiGraphics, mouseX, mouseY);
        drawTitleText(guiGraphics, Component.translatable("hstg_labs.gui.config_type", kind.displayName()), 5);
        drawScrollingString(guiGraphics, Component.translatable("hstg_labs.gui.side_config.sides"), relativeX, relativeY + 120,
              mekanism.client.render.IFancyFontRenderer.TextAlignment.CENTER, subheadingTextColor(), width, 4, false);
    }

    /** Like Mekanism's GuiConfigTypeTab: switches the kind the face buttons edit. */
    private final class KindTab extends GuiInsetElement<Void> {

        private final TransmissionKind kind;

        KindTab(IGuiWrapper gui, TransmissionKind kind, int x, int y) {
            super(kind.icon(), gui, null, x, y, 26, 18, true);
            this.kind = kind;
            setTooltip(Tooltip.create(kind.displayName()));
        }

        @Override
        protected void colorTab(@NotNull GuiGraphics guiGraphics) {
            int c = kind.tabColor();
            guiGraphics.setColor(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F, 1F);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            LabSideConfigWindow.this.kind = kind;
            updateTabs();
        }
    }

    /** For tests of the layout: the elements a click can reach. */
    List<GuiElement> elements() {
        return children();
    }
}
