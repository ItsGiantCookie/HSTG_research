package io.github.itsgiantcookie.hstglabs.mek.client;

import io.github.itsgiantcookie.hstglabs.client.ClientResearch;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload.SlotView;
import io.github.itsgiantcookie.hstglabs.packet.QueueEditPayload;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.IngredientView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.ProgressView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.QueueView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechState;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.scroll.GuiTextScrollList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * The basic lab screen (docs/design.md §4.4, WP-10) on Mekanism's own GUI classes (D22 hybrid):
 * energy bar, unit progress, an inner screen with the current research or the selected entry, the
 * owner's queue (move up / down, remove) and the technologies they can add. It only draws the
 * server's {@link LabStatusPayload} and research snapshot and sends {@link QueueEditPayload}s.
 */
public class LabScreen extends GuiMekanism<BasicLabMenu> {

    private static final int RIGHT_X = 136, RIGHT_W = 114;
    private static final List<TechState> AVAILABLE_ORDER = List.of(TechState.AVAILABLE, TechState.BLOCKED, TechState.REQUIREMENTS_UNMET,
          TechState.PREREQUISITES_MISSING, TechState.UNKNOWN_STAGE);

    private GuiTextScrollList queueList;
    private GuiTextScrollList availableList;
    private MekanismButton up, down, remove, add;
    private List<ResourceLocation> queueIds = List.of();
    private List<ResourceLocation> availableIds = List.of();
    private int shownRevision = -1;
    private int lastQueueSelection = -1, lastAvailableSelection = -1;

    public LabScreen(BasicLabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        dynamicSlots = true;
        imageWidth = BasicLabMenu.WIDTH;
        imageHeight = BasicLabMenu.HEIGHT;
        inventoryLabelX = BasicLabMenu.INVENTORY_X - 1;
        inventoryLabelY = BasicLabMenu.INVENTORY_Y - 11;
    }

    private Optional<LabStatusPayload> status() {
        return ClientResearch.lab(menu.containerId);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, new GuiBar.IBarInfoHandler() {
            @Override
            public Component getTooltip() {
                return status().map(s -> Component.translatable("screen.hstg_labs.lab.energy", joules(s.energy()), joules(s.capacity()),
                      joules(s.usage()))).orElse(null);
            }

            @Override
            public double getLevel() {
                return status().filter(s -> s.capacity() > 0).map(s -> (double) s.energy() / s.capacity()).orElse(0D);
            }
        }, 6, 17, 52));
        addRenderableWidget(new GuiProgress(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return firstRunning().map(SlotView::unitProgress).orElse(0D);
            }

            @Override
            public boolean isActive() {
                return firstRunning().isPresent();
            }
        }, ProgressType.LARGE_RIGHT, this, 64, 63));
        addRenderableWidget(new GuiInnerScreen(this, 24, 78, 108, 62, this::screenLines).padding(4).spacing(1));

        queueList = addRenderableWidget(new GuiTextScrollList(this, RIGHT_X, 16, RIGHT_W, 42));
        up = addRenderableWidget(new MekanismButton(this, RIGHT_X, 60, 20, 12, Component.literal("▲"), (e, x, y) -> moveSelected(-1)));
        down = addRenderableWidget(new MekanismButton(this, RIGHT_X + 22, 60, 20, 12, Component.literal("▼"), (e, x, y) -> moveSelected(1)));
        remove = addRenderableWidget(new MekanismButton(this, RIGHT_X + 44, 60, RIGHT_W - 44, 12,
              Component.translatable("screen.hstg_labs.lab.remove"), (e, x, y) -> edit(QueueEditPayload.Op.REMOVE, selectedQueued(), 0)));
        availableList = addRenderableWidget(new GuiTextScrollList(this, RIGHT_X, 86, RIGHT_W, 52));
        add = addRenderableWidget(new MekanismButton(this, RIGHT_X, 140, RIGHT_W, 12, Component.translatable("screen.hstg_labs.lab.add"),
              (e, x, y) -> edit(QueueEditPayload.Op.ADD, selectedAvailable(), 0)));
        shownRevision = -1;
        refreshLists();
        updateButtons();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshLists();
        // one selection at a time: picking in one list clears the other
        int q = queueList.getSelection(), a = availableList.getSelection();
        if (q != -1 && q != lastQueueSelection) {
            availableList.clearSelection();
            a = -1;
        } else if (a != -1 && a != lastAvailableSelection) {
            queueList.clearSelection();
            q = -1;
        }
        lastQueueSelection = q;
        lastAvailableSelection = a;
        updateButtons();
    }

    private void refreshLists() {
        if (shownRevision == ClientResearch.revision()) {
            return;
        }
        shownRevision = ClientResearch.revision();
        QueueView queue = ClientResearch.queue();
        queueIds = queue.entries();
        queueList.setText(queueIds.stream().map(id -> {
            String name = ClientResearch.technology(id).map(t -> t.name().getString()).orElse(id.toString());
            return queue.progressOf(id).map(p -> name + " " + p.done() + "/" + p.units()).orElse(name);
        }).collect(Collectors.toCollection(ArrayList::new)));
        List<TechView> candidates = ClientResearch.technologies().stream()
              .filter(t -> t.state() != TechState.RESEARCHED && !queueIds.contains(t.id()))
              .sorted(Comparator.comparingInt((TechView t) -> AVAILABLE_ORDER.indexOf(t.state())).thenComparing(t -> t.name().getString()))
              .toList();
        availableIds = candidates.stream().map(TechView::id).toList();
        availableList.setText(candidates.stream().map(t -> switch (t.state()) {
            case AVAILABLE -> t.name().getString();
            case BLOCKED -> ChatFormatting.RED + t.name().getString();
            default -> ChatFormatting.GRAY + t.name().getString();
        }).collect(Collectors.toCollection(ArrayList::new)));
    }

    private void updateButtons() {
        boolean own = status().map(LabStatusPayload::ownedByViewer).orElse(false);
        int q = queueList.getSelection();
        up.active = own && q > 0 && q < queueIds.size();
        down.active = own && q >= 0 && q < queueIds.size() - 1;
        remove.active = own && q >= 0 && q < queueIds.size();
        add.active = own && selectedAvailable() != null
              && ClientResearch.technology(selectedAvailable()).map(t -> t.state() == TechState.AVAILABLE || t.state() == TechState.BLOCKED).orElse(false);
    }

    private ResourceLocation selectedQueued() {
        int q = queueList.getSelection();
        return q >= 0 && q < queueIds.size() ? queueIds.get(q) : null;
    }

    private ResourceLocation selectedAvailable() {
        int a = availableList.getSelection();
        return a >= 0 && a < availableIds.size() ? availableIds.get(a) : null;
    }

    private boolean moveSelected(int delta) {
        ResourceLocation id = selectedQueued();
        if (id == null) {
            return false;
        }
        edit(QueueEditPayload.Op.MOVE, id, queueIds.indexOf(id) + delta);
        queueList.clearSelection();
        return true;
    }

    private boolean edit(QueueEditPayload.Op op, ResourceLocation id, int index) {
        if (id == null) {
            return false;
        }
        PacketDistributor.sendToServer(new QueueEditPayload(op, id, index));
        if (op != QueueEditPayload.Op.MOVE) {
            queueList.clearSelection();
            availableList.clearSelection();
        }
        return true;
    }

    private Optional<SlotView> firstRunning() {
        return status().flatMap(s -> s.slots().stream().filter(v -> v.technology().isPresent()).findFirst());
    }

    /** The selected entry's details, else what the lab runs or why it is idle. */
    private List<Component> screenLines() {
        List<Component> lines = new ArrayList<>();
        ResourceLocation selected = selectedQueued() != null ? selectedQueued() : selectedAvailable();
        if (selected != null) {
            ClientResearch.technology(selected).ifPresent(t -> details(t, lines));
            return lines;
        }
        Optional<LabStatusPayload> status = status();
        if (status.isEmpty()) {
            return lines;
        }
        LabStatusPayload s = status.get();
        lines.add(Component.translatable("screen.hstg_labs.lab.tier", s.tier(), String.format("%.2f", s.speed())));
        if (!s.ownedByViewer()) {
            lines.add(Component.translatable("screen.hstg_labs.lab.other_owner").withStyle(ChatFormatting.YELLOW));
        }
        Optional<SlotView> running = firstRunning();
        if (running.isPresent()) {
            ResourceLocation tech = running.get().technology().orElseThrow();
            Optional<TechView> view = ClientResearch.technology(tech);
            lines.add(view.map(TechView::name).orElse(Component.literal(tech.toString())));
            Optional<ProgressView> p = ClientResearch.queue().progressOf(tech);
            p.ifPresent(pv -> lines.add(Component.translatable("screen.hstg_labs.lab.units", pv.done(), pv.units())));
            long workers = Math.max(1, s.slots().size());
            long remaining = p.map(pv -> Math.max(0, pv.units() - pv.done() - pv.inFlight())).orElse(0);
            long eta = running.get().ticksLeft() + remaining * running.get().ticksTotal() / workers;
            lines.add(Component.translatable("screen.hstg_labs.lab.eta", time(eta)));
        } else {
            s.slots().stream().filter(v -> !v.reason().isEmpty()).findFirst()
                  .ifPresentOrElse(v -> lines.add(idle(v)), () -> lines.add(Component.translatable("screen.hstg_labs.lab.idle")));
        }
        return lines;
    }

    private static void details(TechView t, List<Component> lines) {
        lines.add(t.name());
        lines.add(Component.translatable("screen.hstg_labs.state." + t.state().name().toLowerCase(java.util.Locale.ROOT)));
        lines.add(Component.translatable("screen.hstg_labs.lab.cost", t.units(), time(t.unitTime()), t.minTier()));
        for (IngredientView i : t.ingredients()) {
            lines.add(Component.literal(i.amount() + "× ").append(i.name()));
        }
        if (t.state() == TechState.PREREQUISITES_MISSING && !t.prerequisites().isEmpty()) {
            MutableComponent needs = Component.translatable("screen.hstg_labs.lab.needs");
            for (ResourceLocation pre : t.prerequisites()) {
                needs.append(" ").append(ClientResearch.technology(pre).map(TechView::name).orElse(Component.literal(pre.toString())));
            }
            lines.add(needs);
        }
    }

    /** {@code idle.<namespace>.<path>} with the detail as argument; addon reasons bring their own key. */
    private static Component idle(SlotView v) {
        ResourceLocation id = ResourceLocation.tryParse(v.reason());
        String key = id == null ? "idle.hstg_labs.unknown" : "idle." + id.getNamespace() + "." + id.getPath();
        return Component.translatableWithFallback(key, v.reason() + (v.detail().isEmpty() ? "" : " (" + v.detail() + ")"), v.detail())
              .withStyle(ChatFormatting.GOLD);
    }

    static String joules(long j) {
        if (j < 1_000) {
            return j + " J";
        }
        String[] units = {"kJ", "MJ", "GJ", "TJ", "PJ"};
        double v = j;
        int u = -1;
        while (v >= 1_000 && u < units.length - 1) {
            v /= 1_000;
            u++;
        }
        return String.format(java.util.Locale.ROOT, "%.2f %s", v, units[u]);
    }

    static String time(long ticks) {
        long s = Math.ceilDiv(ticks, 20);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, titleTextColor(), false);
        QueueView queue = ClientResearch.queue();
        g.drawString(font, Component.translatable("screen.hstg_labs.lab.queue", queue.entries().size(), queue.max()), RIGHT_X, 6, titleTextColor(),
              false);
        g.drawString(font, Component.translatable("screen.hstg_labs.lab.available"), RIGHT_X, 76, titleTextColor(), false);
        renderInventoryText(g);
        super.drawForegroundText(g, mouseX, mouseY);
    }
}
