package de.germanrangers.hstglabs.gametest;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.Registration;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The mod's creative tab lists every item registered in the hstg_labs namespace. */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeTabTests {

    private CreativeTabTests() {
    }

    @GameTest(template = "empty")
    public static void creativeTabListsEveryItem(GameTestHelper helper) {
        CreativeModeTab tab = Registration.MAIN_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, false, helper.getLevel().registryAccess()));
        Set<Item> shown = tab.getDisplayItems().stream().map(ItemStack::getItem).collect(Collectors.toSet());
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(HstgLabs.MODID)) {
                helper.assertTrue(shown.contains(item), "not in the creative tab: " + BuiltInRegistries.ITEM.getKey(item));
            }
        }
        helper.assertTrue(!shown.isEmpty(), "creative tab is empty");
        helper.succeed();
    }
}
