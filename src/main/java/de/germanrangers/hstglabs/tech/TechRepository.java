package de.germanrangers.hstglabs.tech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.germanrangers.hstglabs.api.tech.TechSource;
import de.germanrangers.hstglabs.core.tech.LabTypeModel;
import de.germanrangers.hstglabs.core.tech.Problem;
import de.germanrangers.hstglabs.core.tech.StageModel;
import de.germanrangers.hstglabs.core.tech.TechModel;
import de.germanrangers.hstglabs.core.tech.TechValidator;
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
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * Loads technologies and packs (docs/design.md §8): {@code <config>/packs/**.json}, then
 * {@code <config>/technologies/**.json}, then every registered {@link TechSource} (D3: files first,
 * sources after; a later definition with the same id replaces an earlier one and says so). Each file
 * stands alone: a broken file disables only its own technology or pack, with an error naming the file
 * and the JSON path.
 */
public final class TechRepository {

    public static final String INVALID_FILE = "invalid_file";
    public static final String INVALID_DEFINITION = "invalid_definition";
    public static final String OVERRIDDEN = "overridden";

    private TechRepository() {
    }

    /**
     * Everything one load produced.
     *
     * @param technologies enabled technologies by id
     * @param definitions  every definition that parsed, enabled or not (what the dev UI edits and writes back)
     * @param packs        packs that parsed, by id
     * @param problems     errors and warnings, files and sources first, then validation
     */
    public record Snapshot(Map<ResourceLocation, Technology> technologies, Map<String, TechDefinition> definitions,
          Map<ResourceLocation, PackDefinition> packs, List<Problem> problems) {

        public Snapshot {
            technologies = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(technologies));
            definitions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
            packs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(packs));
            problems = List.copyOf(problems);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), List.of());
        }

        public long errors() {
            return problems.stream().filter(p -> p.severity() == Problem.Severity.ERROR).count();
        }
    }

    public static Snapshot load(Path configDir, TechTypes types, Function<String, Optional<StageModel>> stages, Collection<LabTypeModel> labTypes) {
        List<Problem> problems = new ArrayList<>();

        Map<ResourceLocation, PackDefinition> packs = new TreeMap<>();
        for (Map.Entry<String, JsonObject> file : readFolder(configDir.resolve("packs"), "packs", problems).entrySet()) {
            String path = file.getKey();
            Optional<String> id = TechJson.idFromPath(path.substring("packs/".length()));
            if (id.isEmpty()) {
                problems.add(Problem.error(path, INVALID_FILE, "file name must be lower case [a-z0-9_./-] and end in .json"));
                continue;
            }
            PackJson.Parsed parsed = PackJson.parse(id.get(), file.getValue());
            parsed.errors().forEach(e -> problems.add(Problem.error(path, INVALID_DEFINITION, e)));
            parsed.definition().ifPresent(p -> packs.put(ResourceLocation.parse(p.id()), p));
        }

        Map<String, TechDefinition> definitions = new LinkedHashMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> file : readFolder(configDir.resolve("technologies"), "technologies", problems).entrySet()) {
            String path = file.getKey();
            Optional<String> id = TechJson.idFromPath(path.substring("technologies/".length()));
            if (id.isEmpty()) {
                problems.add(Problem.error(path, INVALID_FILE, "file name must be lower case [a-z0-9_./-] and end in .json"));
                continue;
            }
            add(id.get(), path, file.getValue(), definitions, origin, problems);
        }
        for (TechSource source : types.sources()) {
            List<Map.Entry<ResourceLocation, JsonElement>> supplied = new ArrayList<>();
            try {
                source.supply((id, json) -> supplied.add(Map.entry(id, json)));
            } catch (RuntimeException e) {
                problems.add(Problem.error("tech source " + source.getClass().getName(), INVALID_FILE, "threw while supplying: " + e));
            }
            for (Map.Entry<ResourceLocation, JsonElement> entry : supplied) {
                String where = "tech source " + entry.getKey();
                if (!entry.getValue().isJsonObject()) {
                    problems.add(Problem.error(entry.getKey().toString(), INVALID_DEFINITION, where + ": definition must be a JSON object"));
                    continue;
                }
                add(entry.getKey().toString(), where, entry.getValue().getAsJsonObject(), definitions, origin, problems);
            }
        }

        Map<ResourceLocation, Technology> resolved = new LinkedHashMap<>();
        List<TechModel> models = new ArrayList<>();
        for (TechDefinition definition : definitions.values()) {
            TechResolver.Resolution resolution = TechResolver.resolve(definition, types, id -> packs.containsKey(id));
            String where = origin.get(definition.id());
            resolution.errors().forEach(e -> problems.add(Problem.error(definition.id(), INVALID_DEFINITION, where + ": " + e)));
            resolution.technology().ifPresent(t -> resolved.put(t.id(), t));
            resolution.model().ifPresent(models::add);
        }

        TechValidator.Result validation = TechValidator.validate(models, labTypes, stages);
        problems.addAll(validation.problems());
        Map<ResourceLocation, Technology> enabled = new LinkedHashMap<>();
        resolved.forEach((id, tech) -> {
            if (validation.isEnabled(id.toString())) {
                enabled.put(id, tech);
            }
        });
        return new Snapshot(enabled, definitions, packs, problems);
    }

    private static void add(String id, String where, JsonObject json, Map<String, TechDefinition> definitions, Map<String, String> origin,
          List<Problem> problems) {
        TechJson.Parsed parsed = TechJson.parse(id, json);
        parsed.errors().forEach(e -> problems.add(Problem.error(id, INVALID_DEFINITION, where + ": " + e)));
        parsed.definition().ifPresent(definition -> {
            String previous = origin.get(id);
            if (previous != null) {
                problems.add(Problem.warning(id, OVERRIDDEN, where + " replaces the definition from " + previous));
            }
            definitions.put(id, definition);
            origin.put(id, where);
        });
    }

    /** Every {@code .json} under {@code folder} by path relative to the config dir; unreadable files become errors. */
    private static Map<String, JsonObject> readFolder(Path folder, String name, List<Problem> problems) {
        Map<String, JsonObject> out = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(folder)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            problems.add(Problem.error(name + "/", INVALID_FILE, "cannot list the folder: " + e.getMessage()));
            return out;
        }
        for (Path file : files) {
            String path = name + "/" + folder.relativize(file).toString().replace('\\', '/');
            if (!path.endsWith(".json")) {
                continue;
            }
            try {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (json.isJsonObject()) {
                    out.put(path, json.getAsJsonObject());
                } else {
                    problems.add(Problem.error(path, INVALID_FILE, "must contain one JSON object"));
                }
            } catch (JsonParseException e) {
                problems.add(Problem.error(path, INVALID_FILE, "not valid JSON: " + rootMessage(e)));
            } catch (IOException e) {
                problems.add(Problem.error(path, INVALID_FILE, "cannot read: " + e.getMessage()));
            }
        }
        return out;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
