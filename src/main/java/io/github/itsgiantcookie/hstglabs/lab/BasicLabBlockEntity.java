package io.github.itsgiantcookie.hstglabs.lab;

import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientStorage;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import io.github.itsgiantcookie.hstglabs.api.modifier.Modifier;
import io.github.itsgiantcookie.hstglabs.api.modifier.ModifierContext;
import io.github.itsgiantcookie.hstglabs.api.modifier.ModifierTarget;
import io.github.itsgiantcookie.hstglabs.api.lab.ResearchSite;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.core.Arbiter;
import io.github.itsgiantcookie.hstglabs.core.ConsumedSet;
import io.github.itsgiantcookie.hstglabs.core.IdleReason;
import io.github.itsgiantcookie.hstglabs.core.LabCycle;
import io.github.itsgiantcookie.hstglabs.core.PackSource;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.core.ResearchProgress;
import io.github.itsgiantcookie.hstglabs.core.SiteModel;
import io.github.itsgiantcookie.hstglabs.core.SiteScheduler;
import io.github.itsgiantcookie.hstglabs.core.TechSpec;
import io.github.itsgiantcookie.hstglabs.core.WorkerSlot;
import io.github.itsgiantcookie.hstglabs.research.ApiRegistries;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.github.itsgiantcookie.hstglabs.tech.Technology;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The basic lab (docs/design.md §5.2): controller, consumer and worker in one block, a research site
 * of one. Each server tick it runs {@link SiteScheduler} over its owner's queue: the head only (D18),
 * one unit per worker slot, a full ingredient set taken from its own slots at the start of a unit,
 * Joules drawn every running tick (D6, D23). Stats come from its lab type (§5.5) and Mekanism speed and
 * energy upgrade cards (§5.8). Units are saved with the block, so a restart resumes them.
 */
public class BasicLabBlockEntity extends BlockEntity {

    /** Mekanism's default FE conversion, 1 FE = 2.5 J (docs/mek-api-facts.md, GeneralConfig feConversionRate). */
    public static final double JOULES_PER_FE = 2.5;
    public static final ResourceLocation SPEED_UPGRADE = ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_speed");
    public static final ResourceLocation ENERGY_UPGRADE = ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_energy");
    public static final int MAX_UPGRADES = 8;
    private static final String SELF = "self";

    private final ResourceLocation typeId;
    private final ItemStackHandler items;
    private final ItemStackHandler upgrades = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            Optional<LabTypeDefinition> type = type();
            return slot == 0 ? id.equals(SPEED_UPGRADE) && type.map(t -> t.upgrades().contains("speed")).orElse(false)
                  : id.equals(ENERGY_UPGRADE) && type.map(t -> t.upgrades().contains("energy")).orElse(false);
        }

        @Override
        public int getSlotLimit(int slot) {
            return MAX_UPGRADES;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Charges the lab from a Mekanism energy item (battery, cube, tablet) or an FE item. */
    private final ItemStackHandler energyItem = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isEnergyItem(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final Arbiter<Technology.Ingredient> arbiter = new Arbiter<>();
    private final List<WorkerSlot<ResourceLocation, Technology.Ingredient>> slots = new ArrayList<>();
    /** Per worker slot: the exact stacks its running unit consumed, so a cancelled unit gives back the same items. */
    private final List<List<ItemStack>> ledgers = new ArrayList<>();
    /** Stacks taken this tick, assigned to the slots that started. */
    private final List<ItemStack> takenThisTick = new ArrayList<>();
    private final List<ItemStack> toDrop = new ArrayList<>();
    private final Source source = new Source();
    private int refundSlot = -1;
    private CompoundTag savedSlots;
    @Nullable
    private OwnerId owner;
    private long energy;
    private List<SiteScheduler.SlotStatus> lastStatus = List.of();

    public BasicLabBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.BASIC_LAB_BE.get(), pos, state);
        typeId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        int slotCount = LabTypes.get(typeId).map(LabTypeDefinition::itemSlots).filter(n -> n > 0).orElse(4);
        items = new ItemStackHandler(slotCount) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return isIngredient(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    // ---- accessors ----

    public Optional<LabTypeDefinition> type() {
        return LabTypes.get(typeId);
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public Optional<OwnerId> owner() {
        return Optional.ofNullable(owner);
    }

    public void setOwner(OwnerId owner) {
        this.owner = owner;
        setChanged();
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemStackHandler upgrades() {
        return upgrades;
    }

    public ItemStackHandler energyItem() {
        return energyItem;
    }

    public static boolean isEnergyItem(ItemStack stack) {
        return stack.getCapability(io.github.itsgiantcookie.hstglabs.ModCapabilities.STRICT_ENERGY_ITEM) != null
              || stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM) != null;
    }

    /** Moves energy from the item in the energy slot into the lab, Joules first, else FE at {@link #JOULES_PER_FE}. */
    void chargeFromItem() {
        ItemStack stack = energyItem.getStackInSlot(0);
        long room = energyCapacity() - energy;
        if (stack.isEmpty() || room <= 0) {
            return;
        }
        mekanism.api.energy.IStrictEnergyHandler joules = stack.getCapability(io.github.itsgiantcookie.hstglabs.ModCapabilities.STRICT_ENERGY_ITEM);
        if (joules != null) {
            long got = joules.extractEnergy(room, mekanism.api.Action.EXECUTE);
            if (got > 0) {
                energy += got;
                setChanged();
            }
            return;
        }
        net.neoforged.neoforge.energy.IEnergyStorage fe = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (fe != null) {
            int want = (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(room / JOULES_PER_FE));
            int got = fe.extractEnergy(want, false);
            if (got > 0) {
                energy = Math.min(energyCapacity(), energy + Math.round(got * JOULES_PER_FE));
                setChanged();
            }
        }
    }

    public long energy() {
        return energy;
    }

    /** Idle reason or state per worker slot after the last tick, for screens and Jade (§4.4). */
    public List<SiteScheduler.SlotStatus> status() {
        return lastStatus;
    }

    public List<WorkerSlot<ResourceLocation, Technology.Ingredient>> workerSlots() {
        return slots;
    }

    // ---- stats with upgrades and modifiers ----

    private int upgradeCount(int slot) {
        return Math.min(MAX_UPGRADES, upgrades.getStackInSlot(slot).getCount());
    }

    /** Speed after Mekanism speed cards (×10^(n/8)) and registered SPEED modifiers. */
    public double speed(@Nullable ResourceLocation technology) {
        double speed = type().map(LabTypeDefinition::speed).orElse(1.0) * Math.pow(10, upgradeCount(0) / 8.0);
        return modified(ModifierTarget.SPEED, speed, technology);
    }

    /** Joules per running slot per tick: Mekanism's 10^((2·speed − energy)/8) on the lab type's base. */
    public long energyPerTick() {
        double base = type().map(LabTypeDefinition::energyPerTick).orElse(0L);
        double perTick = base * Math.pow(10, (2.0 * upgradeCount(0) - upgradeCount(1)) / 8.0);
        return Math.round(modified(ModifierTarget.ENERGY, perTick, null));
    }

    /** Joule buffer: Mekanism's 10^(energy/8) on the lab type's base. */
    public long energyCapacity() {
        return Math.round(type().map(LabTypeDefinition::energyCapacity).orElse(0L) * Math.pow(10, upgradeCount(1) / 8.0));
    }

    private double modified(ModifierTarget target, double value, @Nullable ResourceLocation technology) {
        if (owner == null) {
            return value;
        }
        ModifierContext context = new ModifierContext() {
            @Override
            public OwnerId owner() {
                return owner;
            }

            @Override
            public Optional<ResourceLocation> technology() {
                return Optional.ofNullable(technology);
            }

            @Override
            public Optional<ResearchSite> site() {
                return Optional.empty();
            }

            @Override
            public Optional<BlockPos> worker() {
                return Optional.of(worldPosition);
            }
        };
        for (Modifier modifier : ApiRegistries.MODIFIERS.values()) {
            if (modifier.target() == target) {
                value = modifier.modify(value, context);
            }
        }
        return value;
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, BasicLabBlockEntity lab) {
        lab.tick();
    }

    void tick() {
        Optional<ResearchManager> manager = ResearchManager.get();
        Optional<LabTypeDefinition> type = type();
        if (manager.isEmpty() || type.isEmpty() || owner == null) {
            return;
        }
        ResearchManager m = manager.get();
        ResearchBook<ResourceLocation> book = m.book(owner);
        restoreSlots(book);
        ensureSlots(type.get().parallel());
        chargeFromItem();

        ResourceLocation head = book.queue().head().orElse(null);
        long perTick = energyPerTick();
        double speed = speed(head);
        for (int i = 0; i < slots.size(); i++) {
            WorkerSlot<ResourceLocation, Technology.Ingredient> slot = slots.get(i);
            slot.setTier(type.get().tier());
            slot.setSpeed(Math.max(1e-6, speed));
            slot.setPowered(energy >= perTick * (i + 1));
        }
        SiteModel site = SiteModel.basicLab(typeId.toString(), type.get().tags(), type.get().tier());
        takenThisTick.clear();

        SiteScheduler.SiteTick<ResourceLocation, Technology.Ingredient> result = SiteScheduler.tick(site, book.queue(),
              id -> ResearchManager.technologyById(id).map(t -> new TechSpec<>(t.cost(), t.minTier(), t.requirements(), book.isBlocked(id))).orElse(null),
              id -> progressOf(book, id), slots, arbiter, List.of(source));

        for (int slotIndex : result.creditedSlots()) {
            ledgers.get(slotIndex).clear(); // that set is used up
        }
        boolean running = false;
        for (int i = 0; i < slots.size(); i++) {
            SiteScheduler.SlotState state = result.slots().get(i).state();
            if (state == SiteScheduler.SlotState.STARTED) {
                assignTaken(i);
                slots.get(i).cycle().tech().ifPresent(t -> m.unitClaimed(owner, t));
            }
            if (state == SiteScheduler.SlotState.STARTED || state == SiteScheduler.SlotState.RUNNING) {
                energy = Math.max(0, energy - perTick);
                running = true;
            }
        }
        if (!takenThisTick.isEmpty()) {
            toDrop.addAll(takenThisTick); // never lost: anything not assigned to a slot is dropped
            takenThisTick.clear();
        }
        dropPending();
        result.credited().forEach(tech -> m.unitCredited(owner, tech));
        if (!result.slots().equals(lastStatus) || running || !result.credited().isEmpty()) {
            m.changed();
            setChanged();
        }
        lastStatus = result.slots();
        if (getBlockState().getValue(BasicLabBlock.ACTIVE) != running && level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(BasicLabBlock.ACTIVE, running), 3);
        }
    }

    @Nullable
    private static ResearchProgress progressOf(ResearchBook<ResourceLocation> book, ResourceLocation id) {
        return ResearchManager.technologyById(id).map(t -> book.progressFor(id, t.cost().units())).orElseGet(() -> book.progress(id).orElse(null));
    }

    private void ensureSlots(int parallel) {
        while (slots.size() < parallel) {
            slots.add(new WorkerSlot<>(1, 1.0));
            ledgers.add(new ArrayList<>());
        }
        // a lab type reload that lowers parallel removes idle slots only; running units finish first
        for (int i = slots.size() - 1; i >= parallel; i--) {
            if (!slots.get(i).cycle().isRunning()) {
                slots.remove(i);
                ledgers.remove(i);
            }
        }
    }

    /** The stacks taken this tick, in order, belong to the slots that started, in slot order. */
    private void assignTaken(int slotIndex) {
        long need = slots.get(slotIndex).cycle().consumed().map(set -> set.portions().stream().mapToLong(ConsumedSet.Portion::amount).sum()).orElse(0L);
        List<ItemStack> ledger = ledgers.get(slotIndex);
        while (need > 0 && !takenThisTick.isEmpty()) {
            ItemStack first = takenThisTick.get(0);
            if (first.getCount() <= need) {
                ledger.add(takenThisTick.remove(0));
                need -= first.getCount();
            } else {
                ledger.add(first.split((int) need));
                need = 0;
            }
        }
    }

    private void dropPending() {
        if (toDrop.isEmpty() || level == null) {
            return;
        }
        for (ItemStack stack : toDrop) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        toDrop.clear();
    }

    /** The block is gone: running units are cancelled (nothing credited), their packs and the contents drop. */
    void onBroken() {
        if (owner != null && level != null && !level.isClientSide) {
            ResearchManager.get().ifPresent(m -> {
                ResearchBook<ResourceLocation> book = m.book(owner);
                for (int i = 0; i < slots.size(); i++) {
                    LabCycle<ResourceLocation, Technology.Ingredient> cycle = slots.get(i).cycle();
                    if (cycle.isRunning()) {
                        ResearchProgress progress = progressOf(book, cycle.tech().orElseThrow());
                        refundSlot = i;
                        if (progress != null) {
                            cycle.cancel(progress, arbiter, List.of()); // no consumer left: everything spills
                        }
                        refundSlot = -1;
                        toDrop.addAll(ledgers.get(i));
                        ledgers.get(i).clear();
                    }
                }
                m.changed();
            });
        }
        for (int i = 0; i < items.getSlots(); i++) {
            toDrop.add(items.getStackInSlot(i).copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < upgrades.getSlots(); i++) {
            toDrop.add(upgrades.getStackInSlot(i).copy());
            upgrades.setStackInSlot(i, ItemStack.EMPTY);
        }
        toDrop.add(energyItem.getStackInSlot(0).copy());
        energyItem.setStackInSlot(0, ItemStack.EMPTY);
        toDrop.removeIf(ItemStack::isEmpty);
        dropPending();
    }

    // ---- ingredients ----

    /** Whether some enabled technology uses this item as an ingredient the lab accepts (§8.2). */
    public boolean isIngredient(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return TechReloader.current().technologies().values().stream()
              .flatMap(t -> t.cost().ingredients().stream()).anyMatch(i -> matches(i.what(), stack));
    }

    @SuppressWarnings("unchecked")
    private boolean matches(Technology.Ingredient ingredient, ItemStack stack) {
        IngredientType<?, ?> type = ingredient.ingredientType();
        if (type.storage() != IngredientStorage.ITEM || !accepts(ingredient)) {
            return false;
        }
        return ((IngredientType<Object, ItemStack>) type).matches(ingredient.value(), stack);
    }

    private boolean accepts(Technology.Ingredient ingredient) {
        return type().map(t -> t.accepts().contains(ingredient.type().toString())).orElse(false);
    }

    /** The lab's own slots as the arbiter sees them. */
    private final class Source implements PackSource<Technology.Ingredient> {

        @Override
        public String id() {
            return SELF;
        }

        @Override
        public long available(Technology.Ingredient what) {
            long total = 0;
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (matches(what, stack)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public long take(Technology.Ingredient what, long amount) {
            long taken = 0;
            for (int i = 0; i < items.getSlots() && taken < amount; i++) {
                if (matches(what, items.getStackInSlot(i))) {
                    ItemStack out = items.extractItem(i, (int) Math.min(Integer.MAX_VALUE, amount - taken), false);
                    taken += out.getCount();
                    takenThisTick.add(out);
                }
            }
            return taken;
        }

        @Override
        public long giveBack(Technology.Ingredient what, long amount) {
            long accepted = 0;
            long left = amount;
            while (left > 0) {
                ItemStack stack = popMatching(what, (int) Math.min(Integer.MAX_VALUE, left));
                if (stack.isEmpty()) {
                    break;
                }
                left -= stack.getCount();
                ItemStack rest = stack;
                for (int i = 0; i < items.getSlots() && !rest.isEmpty(); i++) {
                    rest = items.insertItem(i, rest, false);
                }
                accepted += stack.getCount() - rest.getCount();
                if (!rest.isEmpty()) {
                    toDrop.add(rest); // the core reports the same amount as spilled; these are the items
                }
            }
            return accepted;
        }

        /** The stacks a refund gives back: this tick's (a rolled-back take), the slot being cancelled, or any slot's. */
        private ItemStack popMatching(Technology.Ingredient what, int max) {
            List<List<ItemStack>> pools = new ArrayList<>();
            pools.add(takenThisTick);
            if (refundSlot >= 0) {
                pools.add(ledgers.get(refundSlot));
            }
            pools.addAll(ledgers);
            for (List<ItemStack> pool : pools) {
                for (int i = pool.size() - 1; i >= 0; i--) {
                    ItemStack s = pool.get(i);
                    if (matches(what, s)) {
                        return s.getCount() <= max ? pool.remove(i) : s.split(max);
                    }
                }
            }
            return ItemStack.EMPTY;
        }
    }

    // ---- energy ----

    private final IEnergyContainer energyContainer = new IEnergyContainer() {
        @Override
        public long getEnergy() {
            return energy;
        }

        @Override
        public void setEnergy(long value) {
            energy = Math.max(0, Math.min(getMaxEnergy(), value));
            setChanged();
        }

        @Override
        public long getMaxEnergy() {
            return energyCapacity();
        }

        @Override
        public void onContentsChanged() {
            setChanged();
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("energy", energy);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            energy = tag.getLong("energy");
        }
    };

    private final IMekanismStrictEnergyHandler strictEnergy = new IMekanismStrictEnergyHandler() {
        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            return List.of(energyContainer);
        }

        @Override
        public void onContentsChanged() {
            setChanged();
        }
    };

    /** FE in, at Mekanism's rate (D23: Joules inside, FE accepted). */
    private final IEnergyStorage feEnergy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            long room = energyCapacity() - energy;
            int fe = (int) Math.max(0, Math.min(maxReceive, Math.floor(room / JOULES_PER_FE)));
            if (!simulate && fe > 0) {
                energy += Math.round(fe * JOULES_PER_FE);
                setChanged();
            }
            return fe;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy / JOULES_PER_FE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energyCapacity() / JOULES_PER_FE);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public IMekanismStrictEnergyHandler strictEnergy() {
        return strictEnergy;
    }

    public IEnergyStorage feEnergy() {
        return feEnergy;
    }

    // ---- saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putString("owner_type", owner.type().toString());
            tag.putString("owner_key", owner.key());
        }
        tag.put("items", items.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.put("energy_item", energyItem.serializeNBT(registries));
        tag.putLong("energy", energy);
        ListTag saved = new ListTag();
        for (int i = 0; i < slots.size(); i++) {
            LabCycle<ResourceLocation, Technology.Ingredient> cycle = slots.get(i).cycle();
            if (!cycle.isRunning()) {
                continue;
            }
            ResourceLocation tech = cycle.tech().orElseThrow();
            CompoundTag s = new CompoundTag();
            s.putString("tech", tech.toString());
            s.putLong("ticks_left", cycle.ticksLeft());
            ListTag portions = new ListTag();
            Technology technology = ResearchManager.technologyById(tech).orElse(null);
            for (ConsumedSet.Portion<Technology.Ingredient> p : cycle.consumed().orElseThrow().portions()) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("ingredient", technology == null ? -1 : indexOf(technology, p.what()));
                pt.putLong("amount", p.amount());
                portions.add(pt);
            }
            s.put("portions", portions);
            ListTag ledger = new ListTag();
            ledgers.get(i).stream().filter(st -> !st.isEmpty()).forEach(st -> ledger.add(st.save(registries)));
            s.put("ledger", ledger);
            saved.add(s);
        }
        tag.put("slots", saved);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation ownerType = ResourceLocation.tryParse(tag.getString("owner_type"));
        owner = ownerType != null && !tag.getString("owner_key").isEmpty() ? new OwnerId(ownerType, tag.getString("owner_key")) : null;
        CompoundTag itemTag = tag.getCompound("items");
        int savedSize = itemTag.contains("Size") ? itemTag.getInt("Size") : items.getSlots();
        if (savedSize == items.getSlots()) {
            items.deserializeNBT(registries, itemTag);
        } else {
            // the lab type's slot count changed: keep every stack, dropping only what no longer fits
            ItemStackHandler old = new ItemStackHandler(savedSize);
            old.deserializeNBT(registries, itemTag);
            for (int i = 0; i < savedSize; i++) {
                ItemStack rest = old.getStackInSlot(i).copy();
                for (int j = 0; j < items.getSlots() && !rest.isEmpty(); j++) {
                    rest = items.insertItem(j, rest, false);
                }
                if (!rest.isEmpty()) {
                    toDrop.add(rest);
                }
            }
        }
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        if (tag.contains("energy_item")) {
            energyItem.deserializeNBT(registries, tag.getCompound("energy_item"));
        }
        energy = tag.getLong("energy");
        savedSlots = new CompoundTag();
        savedSlots.put("list", tag.getList("slots", Tag.TAG_COMPOUND).copy());
        registriesForLoad = registries;
    }

    private HolderLookup.Provider registriesForLoad;

    /** Rebuilds saved units on the first tick, once technologies are loaded. Unknown technologies give their packs back. */
    private void restoreSlots(ResearchBook<ResourceLocation> book) {
        if (savedSlots == null) {
            return;
        }
        ListTag list = savedSlots.getList("list", Tag.TAG_COMPOUND);
        savedSlots = null;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            ResourceLocation tech = ResourceLocation.tryParse(s.getString("tech"));
            List<ItemStack> ledger = new ArrayList<>();
            for (Tag t : s.getList("ledger", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registriesForLoad, t).ifPresent(ledger::add);
            }
            Technology technology = tech == null ? null : ResearchManager.technologyById(tech).orElse(null);
            List<ConsumedSet.Portion<Technology.Ingredient>> portions = new ArrayList<>();
            boolean ok = technology != null && s.getLong("ticks_left") >= 1;
            for (Tag t : s.getList("portions", Tag.TAG_COMPOUND)) {
                CompoundTag pt = (CompoundTag) t;
                int index = pt.getInt("ingredient");
                if (!ok || index < 0 || index >= technology.cost().ingredients().size()) {
                    ok = false;
                    break;
                }
                portions.add(new ConsumedSet.Portion<>(SELF, technology.cost().ingredients().get(index).what(), pt.getLong("amount")));
            }
            if (ok && !portions.isEmpty()) {
                slots.add(new WorkerSlot<>(1, 1.0, LabCycle.restore(tech, s.getLong("ticks_left"), new ConsumedSet<>(portions))));
                ledgers.add(ledger);
            } else {
                // the technology is gone or changed shape: cancel the reserved unit and give the packs back
                if (tech != null) {
                    book.progress(tech).filter(p -> p.inFlight() > 0).ifPresent(ResearchProgress::cancelUnit);
                }
                for (ItemStack st : ledger) {
                    ItemStack rest = st;
                    for (int j = 0; j < items.getSlots() && !rest.isEmpty(); j++) {
                        rest = items.insertItem(j, rest, false);
                    }
                    if (!rest.isEmpty()) {
                        toDrop.add(rest);
                    }
                }
            }
        }
        dropPending();
    }

    private static int indexOf(Technology technology, Technology.Ingredient what) {
        for (int i = 0; i < technology.cost().ingredients().size(); i++) {
            if (technology.cost().ingredients().get(i).what().equals(what)) {
                return i;
            }
        }
        return -1;
    }

    /** For tests and debug: the reason the first slot is idle, if it is. */
    public Optional<IdleReason> firstIdleReason() {
        return lastStatus.stream().map(SiteScheduler.SlotStatus::reason).filter(java.util.Objects::nonNull).findFirst();
    }
}
