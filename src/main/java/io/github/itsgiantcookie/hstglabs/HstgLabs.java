package io.github.itsgiantcookie.hstglabs;

import io.github.itsgiantcookie.hstglabs.demo.DemoAddon;
import io.github.itsgiantcookie.hstglabs.hs.HsVersion;
import io.github.itsgiantcookie.hstglabs.packet.CycleFacePayload;
import io.github.itsgiantcookie.hstglabs.packet.LabButtonPayload;
import io.github.itsgiantcookie.hstglabs.research.ApiRegistries;
import io.github.itsgiantcookie.hstglabs.research.BuiltInApi;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.github.itsgiantcookie.hstglabs.mek.ChemicalIngredientType;
import io.github.itsgiantcookie.hstglabs.packet.PackSyncPayload;
import io.github.itsgiantcookie.hstglabs.tech.BuiltInTypes;
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
