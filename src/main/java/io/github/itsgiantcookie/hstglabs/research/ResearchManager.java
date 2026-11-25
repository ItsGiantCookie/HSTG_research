package io.github.itsgiantcookie.hstglabs.research;

import com.mojang.logging.LogUtils;
import io.github.itsgiantcookie.hstglabs.api.event.ResearchEvent;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerType;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardContext;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardType;
import io.github.itsgiantcookie.hstglabs.api.tech.TechDefinitionsReloadedEvent;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.core.ResearchProgress;
import io.github.itsgiantcookie.hstglabs.core.ResearchQueue.AddResult;
import io.github.itsgiantcookie.hstglabs.tech.BuiltInTypes;
import io.github.itsgiantcookie.hstglabs.tech.Technology;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The only writer of research progress (docs/design.md §6.1, §6.4). One per running server: owners
 * and their {@link ResearchBook}s in {@link ResearchSavedData}, technologies from {@link TechReloader},
 * stages through the {@link StageBridge}.
 *
 * <p>Lifecycle per technology (§4.1, D5, D7, D32): units are credited; when complete it is unlocked
 * through HS (its stage's scope decides global or one unlock per team member), then the other rewards
 * apply. A refused unlock marks it BLOCKED and takes it out of the queue. Stage events from HS keep the
 * books in step: unlocked elsewhere, relocked, lost for good. Server thread only.
 */
public final class ResearchManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PENDING_INTERVAL = 20;

    private static volatile StageBridge bridge;
    private static volatile ResearchManager instance;

    private final MinecraftServer server;
    private final Set<String> unlockingNow = new HashSet<>();

    private ResearchManager(MinecraftServer server) {
        this.server = server;
    }

    /** Wires the manager to the game; {@code stageBridge} is the hs package's implementation. */
    public static void register(StageBridge stageBridge) {
        bridge = Objects.requireNonNull(stageBridge, "stageBridge");
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> instance = new ResearchManager(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> instance = null);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            ResearchManager m = instance;
            if (m != null && e.getServer().getTickCount() % PENDING_INTERVAL == 0) {
                m.processAllPending();
            }
        });
        NeoForge.EVENT_BUS.addListener(TechDefinitionsReloadedEvent.class, e -> {
            ResearchManager m = instance;
            if (m != null) {
                m.reconcileCosts();
            }
        });
        BuiltInTypes.setStageUnlocker(context -> get().ifPresent(m -> TechReloader.current().technologies().values().stream()
              .filter(t -> t.id().equals(context.technology())).findFirst().ifPresent(t -> m.unlock(context.owner(), t))));
    }

    /** The {@link StageBridge.Listener} the hs package forwards stage events to. */
    public static StageBridge.Listener stageListener() {
        return new StageBridge.Listener() {
            @Override
            public void unlocked(String stage, Optional<UUID> player) {
                get().ifPresent(m -> m.unlocked(stage, player));
            }

            @Override
            public void locked(String stage, Optional<UUID> player) {
                get().ifPresent(m -> m.locked(stage, player));
            }

            @Override
            public void lost(String stage, Optional<UUID> player) {
                get().ifPresent(m -> m.lost(stage, player));
            }
        };
    }

    public static Optional<ResearchManager> get() {
        return Optional.ofNullable(instance);
    }

    // ---- owners ----

    /** The owner type {@code owner_model} selects. */
    public OwnerType activeOwnerType() {
        ResourceLocation id = LabsConfig.ownerModel() == LabsConfig.OwnerModel.SERVER ? BuiltInOwners.SERVER : BuiltInOwners.TEAM;
        return ApiRegistries.OWNER_TYPES.get(id).orElseThrow();
    }

    /** Who {@code player} researches for. */
    public OwnerId ownerOf(ServerPlayer player) {
        return activeOwnerType().ownerOf(player).orElse(BuiltInOwners.SERVER_OWNER);
    }

    public Collection<UUID> members(OwnerId owner) {
        return ApiRegistries.OWNER_TYPES.get(owner.type()).map(t -> t.members(owner, server)).orElse(List.of());
    }

    public ResearchBook<ResourceLocation> book(OwnerId owner) {
        return data().book(owner, LabsConfig.queueMax());
    }

    public Map<OwnerId, ResearchBook<ResourceLocation>> books() {
        return data().books();
    }

    private ResearchSavedData data() {
        return ResearchSavedData.get(server);
    }

    /** Admin and GameTest setup: forget everything about an owner (§14: each test starts clean). */
    public void forgetOwner(OwnerId owner) {
        if (books().remove(owner) != null) {
            data().setDirty();
        }
    }

    // ---- queue and progress ----

    /** Outcome of {@link #enqueue}. */
    public enum EnqueueResult {
        ADDED,
        DUPLICATE,
        FULL,
        UNKNOWN_TECHNOLOGY,
        ALREADY_RESEARCHED,
        VETOED,
        NO_PERMISSION,
        PREREQUISITES_MISSING,
        REQUIREMENTS_UNMET
    }

    /**
     * A player queues research for their owner (§4.3): {@code queue_permission}, then HS's graph must
     * say REACHABLE and the stage's other requirements must be met (CLAUDE.md: {@code unlockGlobal}
     * does not check dependency groups, so this is the gate), then {@link #enqueue}.
     */
    public EnqueueResult enqueueBy(ServerPlayer player, ResourceLocation tech) {
        if (!mayEditQueue(player)) {
            return EnqueueResult.NO_PERMISSION;
        }
        Technology technology = technology(tech).orElse(null);
        if (technology == null) {
            return EnqueueResult.UNKNOWN_TECHNOLOGY;
        }
        OwnerId owner = ownerOf(player);
        if (!book(owner).queue().contains(tech)) {
            switch (bridge.canQueue(technology.stage(), player)) {
                case UNKNOWN_STAGE -> {
                    return EnqueueResult.UNKNOWN_TECHNOLOGY;
                }
                case ALREADY_UNLOCKED -> {
                    return EnqueueResult.ALREADY_RESEARCHED;
                }
                case PREREQUISITES_MISSING -> {
                    return EnqueueResult.PREREQUISITES_MISSING;
                }
                case REQUIREMENTS_UNMET -> {
                    return EnqueueResult.REQUIREMENTS_UNMET;
                }
                case OK -> {
                }
            }
        }
        return enqueue(owner, tech);
    }

    /** {@code queue_permission} (§4.3): anyone in the owning team, or operators only. */
    public boolean mayEditQueue(ServerPlayer player) {
        return LabsConfig.queuePermission() == LabsConfig.QueuePermission.ANYONE || player.hasPermissions(2);
    }

    /** Moves a queued technology to {@code index} (clamped). */
    public boolean move(OwnerId owner, ResourceLocation tech, int index) {
        boolean moved = book(owner).queue().move(tech, index);
        if (moved) {
            data().setDirty();
            NeoForge.EVENT_BUS.post(new ResearchEvent.QueueChanged(owner, tech));
        }
        return moved;
    }

    /** What the screen shows about a stage for {@code player}. */
    public Optional<StageBridge.StageInfo> stageInfo(String stage, ServerPlayer player) {
        return bridge.info(stage, player);
    }

    /** Removes a technology from the owner's queue; running units still finish and count. */
    public boolean dequeue(OwnerId owner, ResourceLocation tech) {
        boolean removed = book(owner).queue().remove(tech);
        if (removed) {
            data().setDirty();
            NeoForge.EVENT_BUS.post(new ResearchEvent.QueueChanged(owner, tech));
        }
        return removed;
    }

    /**
     * Adds a technology to the owner's queue. Prerequisite and permission checks for players (§4.3:
     * REACHABLE + requirements, queue_permission) are the packet handler's (WP-10); this checks the
     * technology exists, is not researched, and that no {@link ResearchEvent.BeforeEnqueue} listener
     * refuses. Re-adding a BLOCKED technology retries its unlock (D32).
     */
    public EnqueueResult enqueue(OwnerId owner, ResourceLocation tech) {
        Technology technology = technology(tech).orElse(null);
        if (technology == null) {
            return EnqueueResult.UNKNOWN_TECHNOLOGY;
        }
        if (isResearched(owner, technology)) {
            return EnqueueResult.ALREADY_RESEARCHED;
        }
        if (NeoForge.EVENT_BUS.post(new ResearchEvent.BeforeEnqueue(owner, tech)).isCanceled()) {
            return EnqueueResult.VETOED;
        }
        ResearchBook<ResourceLocation> book = book(owner);
        book.progressFor(tech, technology.cost().units());
        AddResult added = book.enqueue(tech);
        if (added != AddResult.ADDED) {
            return added == AddResult.DUPLICATE ? EnqueueResult.DUPLICATE : EnqueueResult.FULL;
        }
        data().setDirty();
        NeoForge.EVENT_BUS.post(new ResearchEvent.QueueChanged(owner, tech));
        processPending(owner);
        return EnqueueResult.ADDED;
    }

    /** Credits up to {@code units} units directly (§9.5 {@code addProgress}, admin commands); returns how many counted. */
    public int addProgress(OwnerId owner, ResourceLocation tech, int units) {
        Technology technology = technology(tech).orElse(null);
        if (technology == null || units <= 0) {
            return 0;
        }
        ResearchBook<ResourceLocation> book = book(owner);
        boolean started = book.progress(tech).map(p -> p.done() == 0 && p.inFlight() == 0).orElse(true);
        int added = book.addUnits(tech, technology.cost().units(), units);
        if (added > 0) {
            data().setDirty();
            if (started) {
                NeoForge.EVENT_BUS.post(new ResearchEvent.Started(owner, tech));
            }
            ResearchProgress p = book.progress(tech).orElseThrow();
            NeoForge.EVENT_BUS.post(new ResearchEvent.UnitCompleted(owner, tech, p.done(), p.units()));
            processPending(owner);
        }
        return added;
    }

    /**
     * A lab finished a unit of {@code tech} for {@code owner} (the progress already counts it):
     * fires {@link ResearchEvent.UnitCompleted} and unlocks it if that was the last unit.
     */
    public void unitCredited(OwnerId owner, ResourceLocation tech) {
        ResearchBook<ResourceLocation> book = book(owner);
        book.unitCredited(tech);
        data().setDirty();
        book.progress(tech).ifPresent(p -> NeoForge.EVENT_BUS.post(new ResearchEvent.UnitCompleted(owner, tech, p.done(), p.units())));
        processPending(owner);
    }

    /** A lab claimed its first unit of {@code tech} for {@code owner}. */
    public void unitClaimed(OwnerId owner, ResourceLocation tech) {
        data().setDirty();
        book(owner).progress(tech).filter(p -> p.done() == 0 && p.inFlight() == 1)
              .ifPresent(p -> NeoForge.EVENT_BUS.post(new ResearchEvent.Started(owner, tech)));
    }

    /** Progress changed outside this class (a lab cancelled a unit): save it. */
    public void changed() {
        data().setDirty();
    }

    /** The enabled technology with this id. */
    public static Optional<Technology> technologyById(ResourceLocation id) {
        return technology(id);
    }

    /** Whether the owner already has the technology's stage (HS is the source of truth, §4.1). */
    public boolean isResearched(OwnerId owner, Technology technology) {
        String stage = technology.stage();
        Optional<StageBridge.Scope> scope = bridge.scopeOf(stage);
        if (scope.isEmpty()) {
            return false;
        }
        if (scope.get() == StageBridge.Scope.GLOBAL) {
            return bridge.isUnlockedGlobal(stage, server);
        }
        Collection<UUID> members = members(owner);
        return !members.isEmpty() && members.stream().allMatch(m -> bridge.isUnlockedIndividual(stage, m, server));
    }

    // ---- unlocking ----

    void processAllPending() {
        List.copyOf(books().keySet()).forEach(this::processPending);
    }

    /** Unlocks every complete technology of the owner; refused ones become BLOCKED (D32). */
    public void processPending(OwnerId owner) {
        ResearchBook<ResourceLocation> book = book(owner);
        for (ResourceLocation tech : book.readyToUnlock()) {
            Optional<Technology> technology = technology(tech);
            if (technology.isEmpty()) {
                continue; // removed from the definitions: kept pending, listed by /hslabs debug (D7)
            }
            boolean unlocks = technology.get().rewards().stream().anyMatch(r -> r.type().equals(BuiltInTypes.UNLOCK_STAGE));
            if (unlocks && !unlock(owner, technology.get())) {
                book.blocked(tech);
                data().setDirty();
                LOGGER.info("History Stages refused the unlock of {} for {}; marked BLOCKED and removed from the queue", tech, owner);
                NeoForge.EVENT_BUS.post(new ResearchEvent.Blocked(owner, tech));
                continue;
            }
            book.unlocked(tech);
            data().setDirty();
            applyRewards(owner, technology.get());
            NeoForge.EVENT_BUS.post(new ResearchEvent.Completed(owner, tech));
        }
    }

    /** HS unlock for the owner: one global unlock, or one per team member (online or offline). */
    boolean unlock(OwnerId owner, Technology technology) {
        String stage = technology.stage();
        Optional<StageBridge.Scope> scope = bridge.scopeOf(stage);
        if (scope.isEmpty()) {
            return false;
        }
        unlockingNow.add(stage);
        try {
            if (scope.get() == StageBridge.Scope.GLOBAL) {
                return bridge.isUnlockedGlobal(stage, server) || bridge.unlockGlobal(stage, server);
            }
            boolean any = false;
            boolean refused = false;
            for (UUID member : members(owner)) {
                if (bridge.isUnlockedIndividual(stage, member, server)) {
                    continue;
                }
                if (bridge.unlockIndividual(stage, member, server)) {
                    any = true;
                } else {
                    refused = true;
                }
            }
            return any || !refused;
        } finally {
            unlockingNow.remove(stage);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyRewards(OwnerId owner, Technology technology) {
        RewardContext context = new RewardContext() {
            @Override
            public MinecraftServer server() {
                return server;
            }

            @Override
            public OwnerId owner() {
                return owner;
            }

            @Override
            public ResourceLocation technology() {
                return technology.id();
            }

            @Override
            public Collection<ServerPlayer> onlineMembers() {
                return members(owner).stream().map(server.getPlayerList()::getPlayer).filter(Objects::nonNull).toList();
            }
        };
        for (Technology.Reward reward : technology.rewards()) {
            if (reward.type().equals(BuiltInTypes.UNLOCK_STAGE)) {
                continue; // done by unlock()
            }
            try {
                ((RewardType) reward.rewardType()).apply(reward.value(), context);
            } catch (RuntimeException e) {
                LOGGER.error("Reward {} of {} failed for {}", reward.type(), technology.id(), owner, e);
            }
        }
    }

    // ---- stage events (§4.1) ----

    void unlocked(String stage, Optional<UUID> player) {
        if (unlockingNow.contains(stage)) {
            return; // our own unlock
        }
        forEachAffected(stage, player, (book, tech) -> book.unlockedElsewhere(tech));
    }

    void locked(String stage, Optional<UUID> player) {
        boolean reset = LabsConfig.onRelock() == LabsConfig.OnRelock.RESET;
        forEachAffected(stage, player, (book, tech) -> book.relocked(tech, reset));
    }

    void lost(String stage, Optional<UUID> player) {
        forEachAffected(stage, player, (book, tech) -> book.lost(tech));
    }

    private void forEachAffected(String stage, Optional<UUID> player, java.util.function.BiConsumer<ResearchBook<ResourceLocation>, ResourceLocation> action) {
        List<ResourceLocation> techs = TechReloader.current().technologies().values().stream()
              .filter(t -> t.stage().equals(stage)).map(Technology::id).toList();
        if (techs.isEmpty()) {
            return;
        }
        books().forEach((owner, book) -> {
            if (player.isPresent() && !members(owner).contains(player.get())) {
                return;
            }
            techs.forEach(tech -> action.accept(book, tech));
        });
        data().setDirty();
    }

    /** After a definitions reload: new costs keep done units, and complete ones become pending (D7). */
    void reconcileCosts() {
        books().values().forEach(book -> List.copyOf(book.progressEntries().keySet()).forEach(tech ->
              technology(tech).ifPresent(t -> book.progressFor(tech, t.cost().units()))));
        data().setDirty();
    }

    private static Optional<Technology> technology(ResourceLocation id) {
        return Optional.ofNullable(TechReloader.current().technologies().get(id));
    }
}
