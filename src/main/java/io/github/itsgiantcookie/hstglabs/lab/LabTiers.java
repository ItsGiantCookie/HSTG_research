package io.github.itsgiantcookie.hstglabs.lab;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.core.LabTierTable;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Lab tier labels (D28): {@code data/<ns>/hstg_labs/lab_tier/<level>.json} with {@code name} (a
 * translation key or text), {@code color} ({@code "#RRGGBB"} or an int) and an optional item
 * {@code icon}. Our own namespace loads first, so a pack's file for the same level replaces ours.
 * The defaults reuse Mekanism's Basic / Advanced / Elite / Ultimate names and colours.
 */
public final class LabTiers {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile LabTierTable current = LabTierTable.EMPTY;

    private LabTiers() {
    }

    public static LabTierTable current() {
        return current;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener(new SimpleJsonResourceReloadListener(new Gson(), "hstg_labs/lab_tier") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                List<Problem> problems = new ArrayList<>();
                current = load(files, problems);
                problems.forEach(p -> LOGGER.warn("[lab tiers] {}", p));
                current.problems().forEach(p -> LOGGER.warn("[lab tiers] {}", p));
                LOGGER.info("Loaded {} lab tiers", current.levels().size());
            }
        }));
    }

    /** Parses each file on its own; a broken file is reported and skipped. */
    public static LabTierTable load(Map<ResourceLocation, JsonElement> files, List<Problem> problems) {
        List<LabTierTable.Tier> tiers = new ArrayList<>();
        files.entrySet().stream()
              .sorted(Comparator.comparing((Map.Entry<ResourceLocation, JsonElement> f) -> !f.getKey().getNamespace().equals(HstgLabs.MODID))
                    .thenComparing(Map.Entry::getKey))
              .forEach(f -> {
                  String subject = "lab_tier " + f.getKey();
                  try {
                      int level = Integer.parseInt(f.getKey().getPath());
                      JsonObject json = f.getValue().getAsJsonObject();
                      String name = json.get("name").getAsString();
                      tiers.add(new LabTierTable.Tier(level, name, color(json.get("color")), json.has("icon") ? json.get("icon").getAsString() : ""));
                  } catch (NumberFormatException e) {
                      problems.add(Problem.error(subject, "invalid_tier_level", "the file name must be the tier level, e.g. 2.json"));
                  } catch (RuntimeException e) {
                      problems.add(Problem.error(subject, "invalid_tier", "needs a name and a color: " + e.getMessage()));
                  }
              });
        return LabTierTable.of(tiers);
    }

    private static int color(JsonElement e) {
        if (e == null) {
            return LabTierTable.FALLBACK_COLOR;
        }
        if (e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt();
        }
        String s = e.getAsString();
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
    }
}
