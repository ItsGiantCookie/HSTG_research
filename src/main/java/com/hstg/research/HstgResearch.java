package com.hstg.research;

import com.hstg.research.capability.ModCapabilities;
import com.hstg.research.network.CycleFacePayload;
import com.hstg.research.network.LabButtonPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(HstgResearch.MODID)
public final class HstgResearch {

    public static final String MODID = "hstg_research";

    public HstgResearch(IEventBus modBus) {
        Registration.register(modBus);
        modBus.addListener(ModCapabilities::register);
        modBus.addListener(CycleFacePayload::register);
        modBus.addListener(LabButtonPayload::register);
        modBus.addListener(HstgResearch::addToCreativeTabs);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(Registration.RESEARCH_LAB_ITEM);
            event.accept(Registration.DATA_CONSUMER_ITEM);
            Registration.DATA_CABLE_ITEMS.values().forEach(event::accept);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(Registration.RESEARCH_SAMPLE);
            event.accept(Registration.EMPTY_SAMPLE_VIAL);
        }
    }
}
