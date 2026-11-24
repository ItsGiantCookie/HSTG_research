package de.germanrangers.hstglabs.api.ingredient;

import java.util.Objects;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * What kind of stack a consumer holds an ingredient in (docs/design.md §8.2, §9.2). Not an enum: an
 * addon with its own storage (mana, source) creates its own instance with its own id and stack class.
 *
 * @param id        storage kind id
 * @param stackType class of the stacks held, e.g. {@link ItemStack}
 * @param <S>       stack type
 */
@ApiStatus.Experimental
public record IngredientStorage<S>(ResourceLocation id, Class<S> stackType) {

    /** Item slots. */
    public static final IngredientStorage<ItemStack> ITEM = new IngredientStorage<>(rl("item"), ItemStack.class);
    /** Fluid tanks. */
    public static final IngredientStorage<FluidStack> FLUID = new IngredientStorage<>(rl("fluid"), FluidStack.class);
    /** Mekanism chemical tanks (§5.8). */
    public static final IngredientStorage<ChemicalStack> CHEMICAL = new IngredientStorage<>(rl("chemical"), ChemicalStack.class);

    public IngredientStorage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stackType, "stackType");
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("hstg_labs", path);
    }
}
