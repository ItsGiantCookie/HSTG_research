package io.github.itsgiantcookie.hstglabs.lab;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/** One face's view of a handler: inserting, extracting or both, as the face's port type allows. */
record FaceItemHandler(IItemHandler inner, boolean insert, boolean extract) implements IItemHandler {

    @Override
    public int getSlots() {
        return inner.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return insert ? inner.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return extract ? inner.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return insert && inner.isItemValid(slot, stack);
    }
}
