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
import io.github.itsgiantcookie.hstglabs.hs.HistoryStagesBridge;
import io.github.itsgiantcookie.hstglabs.research.BuiltInOwners;
import io.github.itsgiantcookie.hstglabs.research.LabsConfig;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import io.github.itsgiantcookie.hstglabs.command.HsLabsCommand;
import io.github.itsgiantcookie.hstglabs.packet.QueueEditPayload;
import io.github.itsgiantcookie.hstglabs.packet.SnapshotSync;
import io.github.itsgiantcookie.hstglabs.packet.TechSnapshotPayload;
import io.github.itsgiantcookie.hstglabs.compat.ftbteams.FtbTeamsCompat;
import io.github.itsgiantcookie.hstglabs.gametest.TestTeams;
import io.github.itsgiantcookie.hstglabs.lab.LabTypes;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(HstgLabs.MODID)
public final class HstgLabs {

    public static final String MODID = "hstg_labs";

    public HstgLabs(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, LabsConfig.SPEC);
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
        // WP-09: lab types as data (D17), and validation sees them
        LabTypes.register(() -> TechReloader.configDir().resolve("lab_types"), ApiRegistries.LAB_TYPE_SOURCES::values);
        TechReloader.setLabTypes(LabTypes::models);
        // WP-08: owners, the research manager, and History Stages through the hs bridge only
        boolean ftbTeams = ModList.get().isLoaded(FtbTeamsCompat.MOD_ID);
        BuiltInOwners.register(modBus, !ftbTeams);
        if (ftbTeams) {
            FtbTeamsCompat.register(modBus);
        }
        if (TestTeams.enabled()) {
            TestTeams.register(modBus);
        }
        ResearchManager.register(HistoryStagesBridge.INSTANCE);
        HistoryStagesBridge.listen(ResearchManager.stageListener());
        HsLabsCommand.register();
        // WP-10: research snapshots to clients, queue edits from clients
        modBus.addListener(TechSnapshotPayload::register);
        modBus.addListener(QueueEditPayload::register);
        SnapshotSync.register();
        if (DemoAddon.enabled()) {
            DemoAddon.register(modBus);
        }
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ApiRegistries::dispatch));
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
