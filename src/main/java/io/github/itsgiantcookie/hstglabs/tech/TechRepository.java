package io.github.itsgiantcookie.hstglabs.tech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.itsgiantcookie.hstglabs.api.tech.TechSource;
import io.github.itsgiantcookie.hstglabs.core.tech.LabTypeModel;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import io.github.itsgiantcookie.hstglabs.core.tech.StageModel;
import io.github.itsgiantcookie.hstglabs.core.tech.TechModel;
import io.github.itsgiantcookie.hstglabs.core.tech.TechValidator;
import io.github.itsgiantcookie.hstglabs.tech.template.BuiltInTemplates;
import io.github.itsgiantcookie.hstglabs.tech.template.Template;
import io.github.itsgiantcookie.hstglabs.tech.template.TemplateEngine;
import io.github.itsgiantcookie.hstglabs.tech.template.TemplateJson;
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
 * Loads technologies and packs (docs/design.md §8): {@code <config>/templates/**.json} (§8.6, plus the
 * built-in templates), {@code <config>/packs/**.json}, then {@code <config>/technologies/**.json}, then
 * every registered {@link TechSource} (D3: files first,
 * sources after; a later definition with the same id replaces an earlier one and says so). Each file
 * stands alone: a broken file disables only its own technology or pack, with an error naming the file
 * and the JSON path.
 */
public final class TechRepository {

    public static final String INVALID_FILE = "invalid_file";
    public static final String INVALID_DEFINITION = "invalid_definition";
    public static final String OVERRIDDEN = "overridden";
    public static final String TEMPLATE_ERROR = "template_error";
    public static final String TEMPLATE_WARNING = "template_warning";

    private TechRepository() {
    }

    /**
     * Everything one load produced.
     *
     * @param technologies enabled technologies by id
     * @param definitions  every definition that parsed, enabled or not (what the dev UI edits and writes back)
     * @param packs        packs that parsed, by id
     * @param problems     errors and warnings, files and sources first, then validation
     * @param origins      for template instances (by technology or pack id): where each value came from (D27)
     */
    public record Snapshot(Map<ResourceLocation, Technology> technologies, Map<String, TechDefinition> definitions,
          Map<ResourceLocation, PackDefinition> packs, List<Problem> problems, Map<String, Map<String, TemplateEngine.Origin>> origins) {

        public Snapshot {
            technologies = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(technologies));
            definitions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
            packs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(packs));
            problems = List.copyOf(problems);
            origins = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(origins));
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), List.of(), Map.of());
        }

        public long errors() {
            return problems.stream().filter(p -> p.severity() == Problem.Severity.ERROR).count();
        }
    }

    public static Snapshot load(Path configDir, TechTypes types, Function<String, Optional<StageModel>> stages, Collection<LabTypeModel> labTypes) {
        List<Problem> problems = new ArrayList<>();
        Map<String, Map<String, TemplateEngine.Origin>> origins = new LinkedHashMap<>();

        Map<String, Template> templates = new LinkedHashMap<>(BuiltInTemplates.all());
        for (Map.Entry<String, JsonObject> file : readFolder(configDir.resolve("templates"), "templates", problems).entrySet()) {
            String path = file.getKey();
            Optional<String> id = TechJson.idFromPath(path.substring("templates/".length()));
            if (id.isEmpty()) {
                problems.add(Problem.error(path, INVALID_FILE, "file name must be lower case [a-z0-9_./-] and end in .json"));
                continue;
            }
            TemplateJson.Parsed parsed = TemplateJson.parse(id.get(), file.getValue(), types.templateKinds());
            parsed.errors().forEach(e -> problems.add(Problem.error(path, INVALID_DEFINITION, e)));
            parsed.template().ifPresent(t -> {
                if (templates.containsKey(t.id())) {
                    problems.add(Problem.warning(t.id(), OVERRIDDEN, path + " replaces the built-in template"));
                }
                templates.put(t.id(), t);
            });
        }

        Map<ResourceLocation, PackDefinition> packs = new TreeMap<>();
        TemplateEngine packTemplates = new TemplateEngine(templates, id -> false);
        for (Map.Entry<String, JsonObject> file : readFolder(configDir.resolve("packs"), "packs", problems).entrySet()) {
            String path = file.getKey();
            Optional<String> id = TechJson.idFromPath(path.substring("packs/".length()));
            if (id.isEmpty()) {
                problems.add(Problem.error(path, INVALID_FILE, "file name must be lower case [a-z0-9_./-] and end in .json"));
                continue;
            }
            Optional<JsonObject> json = expand(packTemplates, BuiltInTemplates.PACK_KIND, id.get(), path, file.getValue(), problems, origins);
            if (json.isEmpty()) {
                continue;
            }
            PackJson.Parsed parsed = PackJson.parse(id.get(), json.get());
            parsed.errors().forEach(e -> problems.add(Problem.error(path, INVALID_DEFINITION, e)));
            parsed.definition().ifPresent(p -> packs.put(ResourceLocation.parse(p.id()), p.withJson(file.getValue())));
        }

        TemplateEngine techTemplates = new TemplateEngine(templates, id -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            return rl != null && packs.containsKey(rl);
        });
        Map<String, TechDefinition> definitions = new LinkedHashMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> file : readFolder(configDir.resolve("technologies"), "technologies", problems).entrySet()) {
            String path = file.getKey();
            Optional<String> id = TechJson.idFromPath(path.substring("technologies/".length()));
            if (id.isEmpty()) {
                problems.add(Problem.error(path, INVALID_FILE, "file name must be lower case [a-z0-9_./-] and end in .json"));
                continue;
            }
            expand(techTemplates, BuiltInTemplates.TECHNOLOGY_KIND, id.get(), path, file.getValue(), problems, origins)
                  .ifPresent(json -> add(id.get(), path, json, file.getValue(), definitions, origin, problems));
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
                JsonObject sourceJson = entry.getValue().getAsJsonObject();
                expand(techTemplates, BuiltInTemplates.TECHNOLOGY_KIND, entry.getKey().toString(), where, sourceJson, problems, origins)
                      .ifPresent(json -> add(entry.getKey().toString(), where, json, sourceJson, definitions, origin, problems));
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
        return new Snapshot(enabled, definitions, packs, problems, origins);
    }

    /**
     * A template instance resolved to a plain definition (D27: the file keeps its link); a plain file
     * as is. Errors disable only this instance.
     */
    private static Optional<JsonObject> expand(TemplateEngine engine, String kind, String id, String where, JsonObject json,
          List<Problem> problems, Map<String, Map<String, TemplateEngine.Origin>> origins) {
        if (!TemplateEngine.isInstance(json)) {
            return Optional.of(json);
        }
        TemplateEngine.Resolution resolution = engine.resolve(kind, json);
        resolution.errors().forEach(e -> problems.add(Problem.error(id, TEMPLATE_ERROR, where + ": " + e)));
        resolution.warnings().forEach(w -> problems.add(Problem.warning(id, TEMPLATE_WARNING, where + ": " + w)));
        resolution.json().ifPresent(j -> origins.put(id, resolution.origins()));
        return resolution.json();
    }

    private static void add(String id, String where, JsonObject json, JsonObject file, Map<String, TechDefinition> definitions,
          Map<String, String> origin, List<Problem> problems) {
        TechJson.Parsed parsed = TechJson.parse(id, json);
        parsed.errors().forEach(e -> problems.add(Problem.error(id, INVALID_DEFINITION, where + ": " + e)));
        parsed.definition().map(d -> d.withJson(file)).ifPresent(definition -> {
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
