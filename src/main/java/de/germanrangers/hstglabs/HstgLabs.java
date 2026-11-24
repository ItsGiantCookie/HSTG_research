package de.germanrangers.hstglabs;

import de.germanrangers.hstglabs.demo.DemoAddon;
import de.germanrangers.hstglabs.hs.HsVersion;
import de.germanrangers.hstglabs.packet.CycleFacePayload;
import de.germanrangers.hstglabs.packet.LabButtonPayload;
import de.germanrangers.hstglabs.research.ApiRegistries;
import de.germanrangers.hstglabs.research.BuiltInApi;
import de.germanrangers.hstglabs.research.TechReloader;
import de.germanrangers.hstglabs.mek.ChemicalIngredientType;
import de.germanrangers.hstglabs.packet.PackSyncPayload;
import de.germanrangers.hstglabs.tech.BuiltInTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(HstgLabs.MODID)
public final class HstgLabs {

    public static final String MODID = "hstg_labs";

    public HstgLabs(IEventBus modBus) {
        Registration.register(modBus);
        modBus.addListener(ModCapabilities::register);
        modBus.addListener(CycleFacePayload::register);
        modBus.addListener(LabButtonPayload::register);
        modBus.addListener((FMLCommonSetupEvent event) -> HsVersion.logAtStartup());
        // WP-06: our built-ins go through the public API like any addon; then the §9.2 registries
        // fire once, after parallel setup has returned, and freeze.
        BuiltInApi.register(modBus);
        // WP-07: built-in ingredient / requirement / reward types, the chemical type, and loading
        BuiltInTypes.register(modBus);
        ChemicalIngredientType.register(modBus);
        modBus.addListener(PackSyncPayload::register);
        TechReloader.register();
        if (DemoAddon.enabled()) {
            DemoAddon.register(modBus);
        }
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ApiRegistries::dispatch));
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
