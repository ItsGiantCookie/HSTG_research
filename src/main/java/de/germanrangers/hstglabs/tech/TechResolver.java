package de.germanrangers.hstglabs.tech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import de.germanrangers.hstglabs.api.ingredient.IngredientType;
import de.germanrangers.hstglabs.api.lab.LabRequirementType;
import de.germanrangers.hstglabs.api.reward.RewardType;
import de.germanrangers.hstglabs.core.LabRequirement;
import de.germanrangers.hstglabs.core.LabRequirements;
import de.germanrangers.hstglabs.core.TechCost;
import de.germanrangers.hstglabs.core.tech.TechModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Resolves a parsed {@link TechDefinition} against the extension registries (docs/design.md §8.1,
 * §8.3): every typed entry is decoded by its registered type's codec. Unknown types, items, packs or
 * bad fields become errors with the entry's JSON path; any error leaves the technology unresolved,
 * which disables only that technology.
 */
public final class TechResolver {

    /** The registries nested {@code all} / {@code any} entries resolve against, for the current resolve call. */
    private static final ThreadLocal<TechTypes> CURRENT = new ThreadLocal<>();

    /** {@code of} of {@code hstg_labs:all} / {@code any}: nested requirement entries, resolved through the registry. */
    static final Codec<List<LabRequirement>> NESTED_REQUIREMENTS = Codec.PASSTHROUGH.listOf().flatXmap(TechResolver::nested,
          list -> DataResult.error(() -> "nested requirements are written back from the raw file, not encoded"));

    private TechResolver() {
    }

    /** Outcome: the technology if every entry resolved, the errors otherwise, and the validation model. */
    public record Resolution(Optional<Technology> technology, List<String> errors, Optional<TechModel> model) {

        public Resolution {
            errors = List.copyOf(errors);
        }
    }

    public static Resolution resolve(TechDefinition definition, TechTypes types, Predicate<ResourceLocation> packExists) {
        List<String> errors = new ArrayList<>();
        ResourceLocation id = ResourceLocation.tryParse(definition.id());
        if (id == null) {
            return new Resolution(Optional.empty(), List.of("id: '" + definition.id() + "' is not a valid id"), Optional.empty());
        }
        TechTypes previous = CURRENT.get();
        CURRENT.set(types);
        try {
            List<TechCost.Ingredient<Technology.Ingredient>> ingredients = new ArrayList<>();
            for (int i = 0; i < definition.ingredients().size(); i++) {
                TypedEntry entry = definition.ingredients().get(i);
                String at = "cost.ingredients[" + i + "]";
                ResourceLocation type = ResourceLocation.parse(entry.type());
                Optional<IngredientType<?, ?>> ingredientType = types.ingredients().apply(type);
                if (ingredientType.isEmpty()) {
                    errors.add(at + ".type: unknown ingredient type '" + type + "'");
                    continue;
                }
                decode(ingredientType.get().codec(), entry.fields()).ifSuccess(value -> {
                    if (value instanceof BuiltInTypes.PackIngredient pack && !packExists.test(pack.pack())) {
                        errors.add(at + ".pack: unknown pack '" + pack.pack() + "' (no config/hstg_labs/packs file defines it)");
                    } else {
                        ingredients.add(new TechCost.Ingredient<>(new Technology.Ingredient(type, ingredientType.get(), value), entry.amount()));
                    }
                }).ifError(e -> errors.add(at + ": " + e.message()));
            }

            List<LabRequirement> requirements = new ArrayList<>();
            for (int i = 0; i < definition.requires().size(); i++) {
                requirement(definition.requires().get(i), "labs.requires[" + i + "]", types).ifSuccess(requirements::add)
                      .ifError(e -> errors.add(e.message()));
            }

            List<Technology.Reward> rewards = new ArrayList<>();
            for (int i = 0; i < definition.rewards().size(); i++) {
                TypedEntry entry = definition.rewards().get(i);
                String at = "rewards[" + i + "]";
                ResourceLocation type = ResourceLocation.parse(entry.type());
                Optional<RewardType<?>> rewardType = types.rewards().apply(type);
                if (rewardType.isEmpty()) {
                    errors.add(at + ".type: unknown reward type '" + type + "'");
                    continue;
                }
                decode(rewardType.get().codec(), entry.fields()).ifSuccess(value -> rewards.add(new Technology.Reward(type, rewardType.get(), value)))
                      .ifError(e -> errors.add(at + ": " + e.message()));
            }

            TechCost<Technology.Ingredient> cost = null;
            if (errors.isEmpty()) {
                try {
                    cost = new TechCost<>(definition.units(), definition.unitTime(), ingredients);
                } catch (IllegalArgumentException e) {
                    errors.add("cost: " + e.getMessage());
                }
            }
            if (!errors.isEmpty()) {
                return new Resolution(Optional.empty(), errors, Optional.empty());
            }
            Technology technology = new Technology(id, definition.stage(), cost, definition.minTier(), requirements, rewards, definition);
            return new Resolution(Optional.of(technology), List.of(), Optional.of(model(technology)));
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** What validation sees: stage, tier and every lab type, tag and network need, nested ones included. */
    static TechModel model(Technology technology) {
        Set<String> labTypes = new HashSet<>();
        Set<String> labTags = new HashSet<>();
        boolean[] network = {false};
        technology.requirements().forEach(r -> walk(r, labTypes, labTags, network));
        return new TechModel(technology.id().toString(), technology.stage(), technology.minTier(), labTypes, labTags, network[0]);
    }

    private static void walk(LabRequirement requirement, Set<String> labTypes, Set<String> labTags, boolean[] network) {
        switch (requirement) {
            case LabRequirements.Lab lab -> {
                if (lab.labType() != null) {
                    labTypes.add(lab.labType());
                } else {
                    labTags.add(lab.labTag());
                }
            }
            case LabRequirements.Network n -> network[0] = true;
            case LabRequirements.AllOf all -> all.of().forEach(r -> walk(r, labTypes, labTags, network));
            case LabRequirements.AnyOf any -> any.of().forEach(r -> walk(r, labTypes, labTags, network));
            default -> {
            }
        }
    }

    private static DataResult<LabRequirement> requirement(TypedEntry entry, String at, TechTypes types) {
        ResourceLocation type = ResourceLocation.parse(entry.type());
        Optional<LabRequirementType<?>> requirementType = types.requirements().apply(type);
        if (requirementType.isEmpty()) {
            return DataResult.error(() -> at + ".type: unknown lab requirement type '" + type + "'");
        }
        DataResult<?> decoded = decode(requirementType.get().codec(), entry.fields());
        if (decoded.error().isPresent()) {
            return DataResult.error(() -> at + ": " + decoded.error().get().message());
        }
        Object value = decoded.getOrThrow();
        if (!(value instanceof LabRequirement core)) {
            // fail closed: an addon condition the scheduler cannot evaluate yet must not be skipped
            return DataResult.error(() -> at + ".type: lab requirement type '" + type
                  + "' is registered, but addon requirement types reach the scheduler only from WP-09");
        }
        return DataResult.success(core);
    }

    private static DataResult<List<LabRequirement>> nested(List<Dynamic<?>> entries) {
        TechTypes types = CURRENT.get();
        if (types == null) {
            return DataResult.error(() -> "nested lab requirements can only be read while resolving a technology");
        }
        List<LabRequirement> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            JsonElement json = entries.get(i).convert(JsonOps.INSTANCE).getValue();
            String at = "of[" + i + "]";
            if (!json.isJsonObject() || !json.getAsJsonObject().has("type") || !json.getAsJsonObject().get("type").isJsonPrimitive()
                  || ResourceLocation.tryParse(json.getAsJsonObject().get("type").getAsString()) == null) {
                return DataResult.error(() -> at + ": must be an object with a namespaced type");
            }
            JsonObject object = json.getAsJsonObject();
            DataResult<LabRequirement> one = requirement(new TypedEntry(object.get("type").getAsString(), 0, object), at, types);
            if (one.error().isPresent()) {
                return DataResult.error(() -> one.error().get().message());
            }
            out.add(one.getOrThrow());
        }
        return DataResult.success(out);
    }

    private static <T> DataResult<T> decode(MapCodec<T> codec, JsonObject fields) {
        return codec.codec().parse(JsonOps.INSTANCE, fields);
    }
}
