package de.germanrangers.hstglabs;

import de.germanrangers.hstglabs.demo.DemoAddon;
import de.germanrangers.hstglabs.hs.HsVersion;
import de.germanrangers.hstglabs.packet.CycleFacePayload;
import de.germanrangers.hstglabs.packet.LabButtonPayload;
import de.germanrangers.hstglabs.research.ApiRegistries;
import de.germanrangers.hstglabs.research.BuiltInApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(HstgLabs.MODID)
public final class HstgLabs {

    public static final String MODID = "hstg_labs";

    public HstgLabs(IEventBus modBus) {
        Registration.register(modBus);
        modBus.addListener(ModCapabilities::register);
        modBus.addListener(CycleFacePayload::register);
        modBus.addListener(LabButtonPayload::register);
        modBus.addListener(HstgLabs::addToCreativeTabs);
        modBus.addListener((FMLCommonSetupEvent event) -> HsVersion.logAtStartup());
        // WP-06: our built-ins go through the public API like any addon; then the §9.2 registries
        // fire once, after parallel setup has returned, and freeze.
        BuiltInApi.register(modBus);
        if (DemoAddon.enabled()) {
            DemoAddon.register(modBus);
        }
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ApiRegistries::dispatch));
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
