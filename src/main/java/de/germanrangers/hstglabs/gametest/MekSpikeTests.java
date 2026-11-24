package de.germanrangers.hstglabs.gametest;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.ModCapabilities;
import de.germanrangers.hstglabs.lab.spike.SpikeApiLab;
import de.germanrangers.hstglabs.lab.spike.SpikeApiLabBlockEntity;
import de.germanrangers.hstglabs.mek.spike.SpikeMekLab;
import de.germanrangers.hstglabs.mek.spike.SpikeMekLabTile;
import java.util.function.ToIntFunction;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * WP-05a acceptance (THROWAWAY with the spike): both lab variants take packs and a chemical through
 * automation and draw energy per tick. Everything goes through capabilities fetched by name, the way a
 * pipe or another mod reaches them.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class MekSpikeTests {

    private static final long ENERGY_IN = 10_000L;
    private static final long CHEMICAL_IN = 100L;

    private MekSpikeTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mekanismBasedLabRunsAUnit(GameTestHelper helper) {
        runOneUnit(helper, SpikeMekLab.BLOCK.get(), be -> ((SpikeMekLabTile) be).units(), SpikeMekLabTile.ENERGY_PER_TICK,
              SpikeMekLabTile.BASE_TICKS, SpikeMekLabTile.CHEMICAL_PER_UNIT);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void apiBasedLabRunsAUnit(GameTestHelper helper) {
        runOneUnit(helper, SpikeApiLab.BLOCK.get(), be -> ((SpikeApiLabBlockEntity) be).units(), SpikeApiLabBlockEntity.ENERGY_PER_TICK,
              SpikeApiLabBlockEntity.BASE_TICKS, SpikeApiLabBlockEntity.CHEMICAL_PER_UNIT);
    }

    private static void runOneUnit(GameTestHelper helper, Block block, ToIntFunction<BlockEntity> units, long energyPerTick,
          int ticks, long chemicalPerUnit) {
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, block);
        BlockPos pos = helper.absolutePos(rel);
        var level = helper.getLevel();
        // as BlockItem#place does: the placed block takes its item's components (Mekanism's default faces live there)
        helper.getBlockEntity(rel).applyComponentsFromItemStack(new ItemStack(block));
        level.invalidateCapabilities(pos);

        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        IChemicalHandler chemicals = level.getCapability(SpikeApiLab.CHEMICAL, pos, Direction.UP);
        IStrictEnergyHandler energy = level.getCapability(ModCapabilities.STRICT_ENERGY, pos, Direction.UP);
        helper.assertTrue(items != null, "no item handler on the top face");
        helper.assertTrue(chemicals != null, "no chemical handler (mekanism:chemical_handler) on the top face");
        helper.assertTrue(energy != null, "no strict energy handler (mekanism:strict_energy_handler) on the top face");

        ItemStack rest = items.insertItem(0, new ItemStack(Items.PAPER, 2), false);
        helper.assertTrue(rest.isEmpty(), "packs refused: " + rest);
        Chemical oxygen = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"));
        ChemicalStack chemicalRest = chemicals.insertChemical(new ChemicalStack(oxygen, CHEMICAL_IN), Action.EXECUTE);
        helper.assertTrue(chemicalRest.isEmpty(), "chemical refused: " + chemicalRest.getAmount() + " mB");
        long energyRest = energy.insertEnergy(ENERGY_IN, Action.EXECUTE);
        helper.assertTrue(energyRest == 0, "energy refused: " + energyRest + " J");

        helper.succeedWhen(() -> {
            BlockEntity be = helper.getBlockEntity(rel);
            helper.assertTrue(units.applyAsInt(be) >= 1, "no unit finished yet");
            long chemicalLeft = chemicals.getChemicalInTank(0).getAmount();
            helper.assertTrue(chemicalLeft <= CHEMICAL_IN - chemicalPerUnit, "chemical not consumed: " + chemicalLeft);
            helper.assertTrue(items.getStackInSlot(0).getCount() <= 1, "pack not consumed");
            long energyLeft = energy.getEnergy(0);
            helper.assertTrue(energyLeft <= ENERGY_IN - energyPerTick * ticks, "energy not drawn per tick: " + energyLeft + " J left");
        });
    }
}
