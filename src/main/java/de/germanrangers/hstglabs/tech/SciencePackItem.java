package de.germanrangers.hstglabs.tech;

import de.germanrangers.hstglabs.Registration;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * {@code hstg_labs:science_pack} (docs/design.md §8.2): one item for every data-driven pack; the
 * data component {@code hstg_labs:pack} names which. New packs appear on reload without a restart.
 * Name and tint come from the pack file; a recipe outputs one as
 * {@code hstg_labs:science_pack[hstg_labs:pack="hstg_labs:logistic"]}.
 */
public class SciencePackItem extends Item implements Registration.CreativeTabStacks {

    public SciencePackItem(Properties properties) {
        super(properties);
    }

    /** A stack of the pack {@code pack}. */
    public static ItemStack of(ResourceLocation pack, int count) {
        ItemStack stack = new ItemStack(Registration.SCIENCE_PACK.get(), count);
        stack.set(Registration.PACK_COMPONENT.get(), pack);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation pack = stack.get(Registration.PACK_COMPONENT.get());
        if (pack == null) {
            return super.getName(stack);
        }
        return PackLookup.get(pack).<Component>map(p -> Component.literal(p.name()))
              .orElseGet(() -> Component.translatable("item.hstg_labs.science_pack.unknown", pack.toString()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation pack = stack.get(Registration.PACK_COMPONENT.get());
        if (pack != null) {
            PackLookup.get(pack).ifPresent(p -> tooltip.add(Component.translatable("item.hstg_labs.science_pack.tier", p.tier())));
            if (flag.isAdvanced()) {
                tooltip.add(Component.literal(pack.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** One stack per loaded pack; the bare item only while no pack is loaded. */
    @Override
    public List<ItemStack> creativeTabStacks() {
        List<ItemStack> stacks = PackLookup.all().stream().map(p -> of(ResourceLocation.parse(p.id()), 1)).toList();
        return stacks.isEmpty() ? List.of(new ItemStack(this)) : stacks;
    }
}
