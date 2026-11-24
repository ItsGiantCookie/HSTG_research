package de.germanrangers.hstglabs.lab;

import de.germanrangers.hstglabs.Registration;
import de.germanrangers.hstglabs.grid.IDataHandler;
import de.germanrangers.hstglabs.ModCapabilities;
import de.germanrangers.hstglabs.mek.MekanismCompat;
import de.germanrangers.hstglabs.mek.config.PortType;
import de.germanrangers.hstglabs.mek.config.SideConfig;
import de.germanrangers.hstglabs.mek.config.TransmissionKind;
import de.germanrangers.hstglabs.lab.ResearchLabMenu.LabData;
import de.germanrangers.hstglabs.mek.UpgradeComponent;
import java.util.EnumMap;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IConfigurable;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Research lab: consumes a research sample plus energy over time, produces data (pushed out of
 * Data faces into cables) and an empty sample vial. Slots: 0 input, 1 output, 2 energy item.
 */
public class ResearchLabBlockEntity extends BlockEntity implements IConfigCardAccess, MenuProvider {

    /** Capacity without energy upgrades (Mekanism scales it by M^(energy/8)). */
    public static final long BASE_MAX_ENERGY = 400_000L;
    public static final long MAX_DATA = 1_000L;
    /** Data pushed per Data face per tick. */
    public static final long DATA_OUTPUT_RATE = 100L;
    /** Joules drawn per tick from an energy item in the energy slot. */
    private static final long ITEM_CHARGE_RATE = 10_000L;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_ENERGY = 2;
    public static final int SLOT_COUNT = 3;
    /** Mekanism's TileComponentEjector runs every half second. */
    private static final int EJECT_DELAY = 10;

    private final SideConfig sideConfig = new SideConfig();
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> level == null || isValidInput(level, stack);
                case SLOT_ENERGY -> isEnergyItem(stack);
                default -> true;
            };
        }
    };
    /** One item view per port type (Mekanism InventorySlotInfo: which slots a face may insert/extract). */
    private final Map<PortType, IItemHandler> portItemHandlers = new EnumMap<>(PortType.class);
    private final IStrictEnergyHandler energyHandler = new EnergyBuffer();
    private final IDataHandler dataHandler = new DataOutput();
    private final RecipeManager.CachedCheck<SingleRecipeInput, ResearchRecipe> recipeCheck =
          RecipeManager.createCheck(Registration.RESEARCH_RECIPE_TYPE.get());
    private long energy;
    private long data;
    private int progress;
    private int duration;
    private long lastEnergyUsed;
    private int ejectDelay;
    private final UpgradeComponent upgrades = new UpgradeComponent(this::onUpgradesChanged);

    /** Server -> menu sync, see ResearchLabMenu.LabData for the layout. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < LabData.DATA) {
                return LabData.part(energy, index - LabData.ENERGY);
            } else if (index < LabData.PROGRESS) {
                return LabData.part(data, index - LabData.DATA);
            } else if (index == LabData.PROGRESS) {
                return duration <= 0 ? 0 : Math.min(LabData.PROGRESS_SCALE, progress * LabData.PROGRESS_SCALE / duration);
            } else if (index < LabData.MAX_ENERGY) {
                return LabData.part(lastEnergyUsed, index - LabData.ENERGY_USED);
            } else if (index < LabData.SPEED_UPGRADES) {
                return LabData.part(getMaxEnergy(), index - LabData.MAX_ENERGY);
            } else if (index == LabData.SPEED_UPGRADES) {
                return upgrades.get(Upgrade.SPEED);
            } else if (index == LabData.ENERGY_UPGRADES) {
                return upgrades.get(Upgrade.ENERGY);
            }
            return upgrades.getInstallTicks();
        }

        @Override
        public void set(int index, int value) {
            // server-authoritative; the client menu uses its own SimpleContainerData
        }

        @Override
        public int getCount() {
            return LabData.COUNT;
        }
    };

    public ResearchLabBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.RESEARCH_LAB_BE.get(), pos, state);
        for (PortType type : TransmissionKind.ITEM.supported()) {
            if (type != PortType.NONE) {
                portItemHandlers.put(type, new PortItemHandler(type));
            }
        }
    }

    // --- processing ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchLabBlockEntity lab) {
        lab.tickServer(level, state);
    }

    private void tickServer(Level level, BlockState state) {
        upgrades.tick();
        chargeFromItem();
        boolean active = false;
        lastEnergyUsed = 0;
        ResearchRecipe recipe = currentRecipe(level);
        if (recipe != null && canProcess(level, recipe)) {
            duration = upgrades.ticks(recipe.duration());
            long energyPerTick = upgrades.energyPerTick(recipe.energyPerTick());
            if (energy >= energyPerTick) {
                energy -= energyPerTick;
                lastEnergyUsed = energyPerTick;
                active = true;
                if (++progress >= duration) {
                    finish(level, recipe);
                }
                setChanged();
            }
        } else if (progress != 0 || duration != 0) {
            progress = 0;
            duration = 0;
            setChanged();
        }
        pushData(level);
        if (--ejectDelay <= 0) {
            ejectDelay = EJECT_DELAY;
            ejectItems(level);
        }
        if (state.getValue(ResearchLabBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(ResearchLabBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Nullable
    private ResearchRecipe currentRecipe(Level level) {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            return null;
        }
        return recipeCheck.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
    }

    private boolean canProcess(Level level, ResearchRecipe recipe) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(inventory.getStackInSlot(SLOT_INPUT)), level.registryAccess());
        boolean outputFits = result.isEmpty() || inventory.insertItem(SLOT_OUTPUT, result, true).isEmpty();
        return outputFits && data + recipe.data() <= MAX_DATA;
    }

    private void finish(Level level, ResearchRecipe recipe) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(inventory.getStackInSlot(SLOT_INPUT)), level.registryAccess());
        inventory.extractItem(SLOT_INPUT, 1, false);
        if (!result.isEmpty()) {
            inventory.insertItem(SLOT_OUTPUT, result, false);
        }
        data += recipe.data();
        progress = 0;
    }

    private void chargeFromItem() {
        long needed = getMaxEnergy() - energy;
        ItemStack stack = inventory.getStackInSlot(SLOT_ENERGY);
        if (needed <= 0 || stack.isEmpty()) {
            return;
        }
        IStrictEnergyHandler item = stack.getCapability(ModCapabilities.STRICT_ENERGY_ITEM);
        if (item != null) {
            long received = item.extractEnergy(Math.min(needed, ITEM_CHARGE_RATE), Action.EXECUTE);
            if (received > 0) {
                energy += received;
                setChanged();
            }
        }
    }

    /**
     * Auto-eject (Mekanism TileComponentEjector, items): when enabled, push the output slot into the
     * inventory next to every face set to Output or Input/Output.
     */
    private void ejectItems(Level level) {
        if (!sideConfig.isEjecting(TransmissionKind.ITEM)) {
            return;
        }
        for (Direction side : Direction.values()) {
            ItemStack stack = inventory.getStackInSlot(SLOT_OUTPUT);
            if (stack.isEmpty()) {
                return;
            }
            PortType type = getPort(TransmissionKind.ITEM, side);
            BlockPos target = worldPosition.relative(side);
            if ((type != PortType.OUTPUT && type != PortType.INPUT_OUTPUT) || !level.isLoaded(target)) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, side.getOpposite());
            if (handler != null) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
                int moved = stack.getCount() - remainder.getCount();
                if (moved > 0) {
                    inventory.extractItem(SLOT_OUTPUT, moved, false);
                }
            }
        }
    }

    /** Push stored data into whatever accepts it on each Data face (cables, consumers). */
    private void pushData(Level level) {
        for (Direction side : Direction.values()) {
            if (data <= 0) {
                return;
            }
            if (getPort(TransmissionKind.DATA, side) != PortType.OUTPUT) {
                continue;
            }
            IDataHandler target = level.getCapability(ModCapabilities.DATA, worldPosition.relative(side), side.getOpposite());
            if (target != null) {
                long offered = Math.min(data, DATA_OUTPUT_RATE);
                long sent = offered - target.insertData(offered, Action.EXECUTE);
                if (sent > 0) {
                    data -= sent;
                    setChanged();
                }
            }
        }
    }

    public static boolean isValidInput(Level level, ItemStack stack) {
        return level.getRecipeManager().getRecipeFor(Registration.RESEARCH_RECIPE_TYPE.get(), new SingleRecipeInput(stack), level).isPresent();
    }

    public static boolean isEnergyItem(ItemStack stack) {
        return stack.getCapability(ModCapabilities.STRICT_ENERGY_ITEM) != null;
    }

    // --- accessors ---

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public UpgradeComponent getUpgrades() {
        return upgrades;
    }

    public long getMaxEnergy() {
        return upgrades.maxEnergy(BASE_MAX_ENERGY);
    }

    /** GUI "Uninstall" button. Server side only. */
    public void removeUpgrade(Upgrade upgrade, boolean all) {
        upgrades.remove(upgrade, all);
    }

    private void onUpgradesChanged() {
        energy = Math.min(energy, getMaxEnergy()); // removing energy upgrades shrinks the buffer
        setChanged();
    }

    public boolean isEjecting(TransmissionKind kind) {
        return sideConfig.isEjecting(kind);
    }

    /** GUI auto-eject button. Server side only. */
    public void toggleEject(TransmissionKind kind) {
        sideConfig.toggleEject(kind);
        onSidesChanged();
    }

    public PortType getPort(TransmissionKind kind, Direction side) {
        return sideConfig.get(kind, relative(side));
    }

    public PortType getPort(TransmissionKind kind, RelativeSide side) {
        return sideConfig.get(kind, side);
    }

    /** Whether any face uses this port type (Mekanism colors a slot frame only when its type is in use). */
    public boolean isPortUsed(TransmissionKind kind, PortType type) {
        return sideConfig.isUsed(kind, type);
    }

    public Direction getFacing() {
        return getBlockState().getValue(ResearchLabBlock.FACING);
    }

    /** Shared by the Configurator and the GUI side-config buttons. Server side only. */
    public PortType cycleFace(TransmissionKind kind, RelativeSide side, boolean forward) {
        PortType type = sideConfig.cycle(kind, side, forward);
        onSidesChanged();
        return type;
    }

    /** GUI "clear sides" button. Server side only. */
    public void cycleAllFaces(TransmissionKind kind, boolean forward) {
        sideConfig.cycleAll(kind, forward);
        onSidesChanged();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new ResearchLabMenu(id, playerInv, worldPosition, menuData);
    }

    private RelativeSide relative(Direction side) {
        return RelativeSide.fromDirections(getFacing(), side);
    }

    // --- capability providers (null side = internal access) ---

    @Nullable
    public IStrictEnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side == null || getPort(TransmissionKind.ENERGY, side) == PortType.INPUT ? energyHandler : null;
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return inventory;
        }
        return portItemHandlers.get(getPort(TransmissionKind.ITEM, side));
    }

    @Nullable
    public IDataHandler getDataHandler(@Nullable Direction side) {
        return side == null || getPort(TransmissionKind.DATA, side) == PortType.OUTPUT ? dataHandler : null;
    }

    /**
     * Mekanism's Configurator calls this in its configurate modes: click shows, sneak-click cycles.
     * Items/energy modes edit those tabs; the other configurate modes (fluids, chemicals, heat) edit Data.
     */
    @Nullable
    public IConfigurable getConfigurable(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        return new IConfigurable() {
            @Override
            public InteractionResult onRightClick(Player player) {
                TransmissionKind kind = MekanismCompat.configuratorKind(player);
                RelativeSide relative = relative(side);
                player.displayClientMessage(Component.translatable("hstg_labs.configurator.view", kind.displayName(),
                      Component.translatable(relative.getTranslationKey()), sideConfig.get(kind, relative).displayName()), true);
                return InteractionResult.SUCCESS;
            }

            @Override
            public InteractionResult onSneakRightClick(Player player) {
                TransmissionKind kind = MekanismCompat.configuratorKind(player);
                RelativeSide relative = relative(side);
                PortType type = cycleFace(kind, relative, true);
                player.displayClientMessage(Component.translatable("hstg_labs.configurator.toggle", kind.displayName(),
                      Component.translatable(relative.getTranslationKey()), type.displayName()), true);
                return InteractionResult.SUCCESS;
            }
        };
    }

    private void onSidesChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
            // neighbors (data cables) re-check their connections
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Mekanism config card ---

    @Override
    public Block getConfigurationDataType() {
        return getBlockState().getBlock();
    }

    @Override
    public CompoundTag getConfigurationData(HolderLookup.Provider provider, Player player) {
        CompoundTag tag = new CompoundTag();
        tag.put("sides", sideConfig.save());
        return tag;
    }

    @Override
    public void setConfigurationData(HolderLookup.Provider provider, Player player, CompoundTag tag) {
        sideConfig.load(tag.getCompound("sides"));
    }

    @Override
    public void configurationDataSet() {
        onSidesChanged();
    }

    // --- persistence + client sync (clients need the side config for port overlays) ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putLong("energy", energy);
        tag.putLong("data", data);
        tag.putInt("progress", progress);
        tag.put("sides", sideConfig.save());
        tag.put("upgrades", upgrades.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) {
            CompoundTag inv = tag.getCompound("inventory");
            inv.putInt("Size", SLOT_COUNT); // older saves had 2 slots
            inventory.deserializeNBT(registries, inv);
        }
        if (tag.contains("upgrades")) {
            upgrades.load(tag.getCompound("upgrades"), registries);
        }
        energy = Math.min(tag.getLong("energy"), getMaxEnergy());
        data = Math.min(tag.getLong("data"), MAX_DATA);
        progress = tag.getInt("progress");
        if (tag.contains("sides")) {
            sideConfig.load(tag.getCompound("sides"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("sides", sideConfig.save());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- handlers ---

    /** Item faces, per port type: which slots the face may insert into / extract from. */
    private final class PortItemHandler implements IItemHandler {

        private final PortType type;

        private PortItemHandler(PortType type) {
            this.type = type;
        }

        private boolean canInsert(int slot) {
            return switch (type) {
                case INPUT, INPUT_OUTPUT -> slot == SLOT_INPUT;
                case ENERGY -> slot == SLOT_ENERGY;
                default -> false;
            };
        }

        private boolean canExtract(int slot) {
            return switch (type) {
                case OUTPUT, INPUT_OUTPUT -> slot == SLOT_OUTPUT;
                case ENERGY -> slot == SLOT_ENERGY;
                default -> false;
            };
        }

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return canInsert(slot) ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtract(slot) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert(slot) && inventory.isItemValid(slot, stack);
        }
    }

    /** Single Mekanism energy container; accepts power, never outputs it. */
    private final class EnergyBuffer implements IStrictEnergyHandler {

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public long getEnergy(int container) {
            return container == 0 ? energy : 0L;
        }

        @Override
        public void setEnergy(int container, long amount) {
            if (container == 0) {
                energy = Math.clamp(amount, 0L, ResearchLabBlockEntity.this.getMaxEnergy());
                setChanged();
            }
        }

        @Override
        public long getMaxEnergy(int container) {
            return container == 0 ? ResearchLabBlockEntity.this.getMaxEnergy() : 0L;
        }

        @Override
        public long getNeededEnergy(int container) {
            return container == 0 ? ResearchLabBlockEntity.this.getMaxEnergy() - energy : 0L;
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            if (container != 0 || amount <= 0) {
                return amount;
            }
            long accepted = Math.min(amount, ResearchLabBlockEntity.this.getMaxEnergy() - energy);
            if (accepted > 0 && action.execute()) {
                energy += accepted;
                setChanged();
            }
            return amount - accepted;
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            return 0L;
        }
    }

    /** Data faces are outputs: they can be drained but never filled from outside. */
    private final class DataOutput implements IDataHandler {

        @Override
        public long getStoredData() {
            return data;
        }

        @Override
        public long getDataCapacity() {
            return MAX_DATA;
        }

        @Override
        public long insertData(long amount, Action action) {
            return amount;
        }

        @Override
        public long extractData(long amount, Action action) {
            long removed = Math.min(Math.max(amount, 0L), data);
            if (removed > 0 && action.execute()) {
                data -= removed;
                setChanged();
            }
            return removed;
        }
    }
}
