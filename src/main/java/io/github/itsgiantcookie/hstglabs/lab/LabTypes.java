package io.github.itsgiantcookie.hstglabs.lab;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.itsgiantcookie.hstglabs.api.lab.LabTypeSource;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import io.github.itsgiantcookie.hstglabs.tech.TechJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Loaded lab types (docs/design.md §5.5, D17). A reload listener reads
 * {@code data/<ns>/hstg_labs/lab_type/<id>.json}; registered {@link LabTypeSource}s come after the data
 * files, then {@code config/hstg_labs/lab_types/} overrides single fields ({@code <id>.json} for
 * {@code hstg_labs:<id>}, {@code <ns>/<id>.json} for other namespaces). {@code /reload} applies changes.
 * Server side; the snapshot is replaced as a whole.
 */
public final class LabTypes {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Everything one load produced. */
    public record Snapshot(Map<ResourceLocation, LabTypeDefinition> types, List<Problem> problems) {

        public Snapshot {
            types = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(types));
            problems = List.copyOf(problems);
        }
    }

    private static volatile Snapshot current = new Snapshot(Map.of(), List.of());

    private LabTypes() {
    }

    public static Snapshot current() {
        return current;
    }

    public static Optional<LabTypeDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(current.types().get(id));
    }

    /**
     * Registers the reload listener. {@code overridesDir} is {@code config/hstg_labs/lab_types};
     * {@code sources} the registered lab type sources.
     */
    public static void register(Supplier<Path> overridesDir, Supplier<Collection<LabTypeSource>> sources) {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener(new SimpleJsonResourceReloadListener(new Gson(), "hstg_labs/lab_type") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                current = load(files, sources.get(), overridesDir.get());
                current.problems().forEach(p -> LOGGER.warn("[lab types] {}", p));
                LOGGER.info("Loaded {} lab types", current.types().size());
            }
        }));
    }

    /**
     * The loaded lab types as validation sees them (§8.3). A lab block's item id is its lab type id
     * (§5.5); {@code lockedBy} are the HS stages locking that item, named when they are technology stages.
     */
    public static List<io.github.itsgiantcookie.hstglabs.core.tech.LabTypeModel> models(Collection<String> technologyStages) {
        return current.types().values().stream().map(d -> new io.github.itsgiantcookie.hstglabs.core.tech.LabTypeModel(d.id(), d.tier(), d.tags(),
              d.roles().stream().map(r -> io.github.itsgiantcookie.hstglabs.core.Role.valueOf(r.toUpperCase(java.util.Locale.ROOT)))
                    .collect(java.util.stream.Collectors.toSet()),
              io.github.itsgiantcookie.hstglabs.hs.HsStageCatalog.stagesLockingItem(d.id(), technologyStages))).toList();
    }

    /** Merges data files, sources and overrides, then parses each lab type on its own. */
    public static Snapshot load(Map<ResourceLocation, JsonElement> files, Collection<LabTypeSource> sources, Path overridesDir) {
        List<Problem> problems = new ArrayList<>();
        Map<ResourceLocation, JsonObject> raw = new TreeMap<>();
        files.forEach((id, json) -> {
            if (json.isJsonObject()) {
                raw.put(id, json.getAsJsonObject());
            } else {
                problems.add(Problem.error(id.toString(), "invalid_file", "lab type must be a JSON object"));
            }
        });
        for (LabTypeSource source : sources) {
            try {
                source.supply((id, json) -> {
                    if (json.isJsonObject()) {
                        raw.put(id, json.getAsJsonObject());
                    } else {
                        problems.add(Problem.error(id.toString(), "invalid_file", "lab type from a source must be a JSON object"));
                    }
                });
            } catch (RuntimeException ex) {
                problems.add(Problem.error("lab type source " + source.getClass().getName(), "invalid_file", "threw while supplying: " + ex));
            }
        }
        overrides(overridesDir, problems).forEach((id, override) -> {
            JsonObject base = raw.get(id);
            if (base == null) {
                problems.add(Problem.warning(id.toString(), "unknown_override", "override for a lab type that is not loaded"));
            } else {
                raw.put(id, LabTypeJson.override(base, override));
            }
        });
        Map<ResourceLocation, LabTypeDefinition> types = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            LabTypeJson.Parsed parsed = LabTypeJson.parse(id.toString(), json);
            parsed.errors().forEach(err -> problems.add(Problem.error(id.toString(), "invalid_lab_type", err)));
            parsed.definition().ifPresent(d -> types.put(id, d));
        });
        return new Snapshot(types, problems);
    }

    private static Map<ResourceLocation, JsonObject> overrides(Path dir, List<Problem> problems) {
        Map<ResourceLocation, JsonObject> out = new TreeMap<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            problems.add(Problem.error("lab_types/", "invalid_file", "cannot list overrides: " + e.getMessage()));
            return out;
        }
        for (Path file : files) {
            String rel = dir.relativize(file).toString().replace('\\', '/');
            String path = rel.substring(0, rel.length() - ".json".length());
            int slash = path.indexOf('/');
            String id = slash > 0 ? path.substring(0, slash) + ":" + path.substring(slash + 1) : TechJson.NAMESPACE + ":" + path;
            ResourceLocation rl = TechJson.isId(id) ? ResourceLocation.tryParse(id) : null;
            if (rl == null) {
                problems.add(Problem.error("lab_types/" + rel, "invalid_file", "file name must be lower case [a-z0-9_./-]"));
                continue;
            }
            try {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (json.isJsonObject()) {
                    out.put(rl, json.getAsJsonObject());
                } else {
                    problems.add(Problem.error("lab_types/" + rel, "invalid_file", "must contain one JSON object"));
                }
            } catch (IOException | JsonParseException e) {
                problems.add(Problem.error("lab_types/" + rel, "invalid_file", "not readable JSON: " + e.getMessage()));
            }
        }
        return out;
    }
}
