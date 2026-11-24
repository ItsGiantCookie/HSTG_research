package io.github.itsgiantcookie.hstglabs.research;

import com.mojang.logging.LogUtils;
import io.github.itsgiantcookie.hstglabs.api.tech.TechDefinitionsReloadedEvent;
import io.github.itsgiantcookie.hstglabs.core.tech.LabTypeModel;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import io.github.itsgiantcookie.hstglabs.hs.HsStageCatalog;
import io.github.itsgiantcookie.hstglabs.packet.PackSyncPayload;
import io.github.itsgiantcookie.hstglabs.tech.PackLookup;
import io.github.itsgiantcookie.hstglabs.tech.TechRepository;
import io.github.itsgiantcookie.hstglabs.tech.TechTypes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * When technologies and packs load (docs/design.md §6.2, §8): at server start, after every
 * {@code /reload}, and when History Stages reloads its stage definitions. Logs every problem, syncs
 * packs to clients (also on login) and posts {@link TechDefinitionsReloadedEvent}. Server thread.
 */
public final class TechReloader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile TechRepository.Snapshot current = TechRepository.Snapshot.empty();
    private static volatile MinecraftServer server;
    private static volatile int lastLoadTick = -1;

    private TechReloader() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> {
            server = e.getServer();
            if (e.getServer().getTickCount() != lastLoadTick) {
                // the start-up datapack sync may already have loaded this tick
                reload(e.getServer(), "server start");
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            server = null;
            lastLoadTick = -1;
            current = TechRepository.Snapshot.empty();
            PackLookup.setServer(java.util.Map.of());
        });
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, e -> {
            MinecraftServer running = e.getPlayerList().getServer();
            if (e.getPlayer() == null && running.getTickCount() != lastLoadTick) {
                // a /reload finished: load again, then everyone gets the new packs (skipped if this tick already loaded, e.g. at start)
                reload(running, "/reload");
            }
            PackSyncPayload payload = PackSyncPayload.of(current.packs().values());
            e.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        });
        HsStageCatalog.onDefinitionsChanged(() -> {
            MinecraftServer running = server;
            if (running != null && running.isSameThread()) {
                reload(running, "History Stages definitions changed");
                PackSyncPayload payload = PackSyncPayload.of(current.packs().values());
                for (ServerPlayer player : running.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        });
    }

    public static TechRepository.Snapshot current() {
        return current;
    }

    /** {@code config/hstg_labs} (D4). */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("hstg_labs");
    }

    /** The live registries, for the repository. */
    public static TechTypes types() {
        return new TechTypes(ApiRegistries.INGREDIENT_TYPES::get, ApiRegistries.LAB_REQUIREMENT_TYPES::get, ApiRegistries.REWARD_TYPES::get,
              ApiRegistries.TECH_SOURCES.values());
    }

    /** Lab types for validation. Empty until the lab type loader exists (WP-09). */
    public static List<LabTypeModel> labTypes() {
        return List.of();
    }

    public static TechRepository.Snapshot reload(MinecraftServer server, String cause) {
        Path dir = configDir();
        try {
            Files.createDirectories(dir.resolve("technologies"));
            Files.createDirectories(dir.resolve("packs"));
        } catch (IOException e) {
            LOGGER.error("cannot create {}: {}", dir, e.getMessage());
        }
        TechRepository.Snapshot snapshot = TechRepository.load(dir, types(), HsStageCatalog::find, labTypes());
        current = snapshot;
        lastLoadTick = server.getTickCount();
        PackLookup.setServer(snapshot.packs());
        for (Problem problem : snapshot.problems()) {
            if (problem.severity() == Problem.Severity.ERROR) {
                LOGGER.error("[technologies] {}", problem);
            } else {
                LOGGER.warn("[technologies] {}", problem);
            }
        }
        LOGGER.info("Loaded {} technologies ({} definitions, {} errors) and {} science packs from {} ({})", snapshot.technologies().size(),
              snapshot.definitions().size(), snapshot.errors(), snapshot.packs().size(), dir, cause);
        NeoForge.EVENT_BUS.post(new TechDefinitionsReloadedEvent(snapshot.technologies().size(), labTypes().size()));
        return snapshot;
    }
}
