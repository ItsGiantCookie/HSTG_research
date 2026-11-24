package de.germanrangers.hstglabs.tech;

import de.germanrangers.hstglabs.api.ingredient.IngredientType;
import de.germanrangers.hstglabs.api.lab.LabRequirementType;
import de.germanrangers.hstglabs.api.reward.RewardType;
import de.germanrangers.hstglabs.core.LabRequirement;
import de.germanrangers.hstglabs.core.TechCost;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A technology resolved against the registries and enabled by validation (docs/design.md §8.1).
 * Runtime only: the file is written back from {@link #definition()}, never from this.
 *
 * @param id           technology id
 * @param stage        HS stage id
 * @param cost         U, T and one unit's set, keyed by {@link Ingredient}
 * @param minTier      {@code labs.min_tier}
 * @param requirements {@code labs.requires} in core form, for the scheduler
 * @param rewards      rewards to apply on completion
 * @param definition   the file as parsed
 */
public record Technology(ResourceLocation id, String stage, TechCost<Ingredient> cost, int minTier, List<LabRequirement> requirements,
      List<Reward> rewards, TechDefinition definition) {

    public Technology {
        requirements = List.copyOf(requirements);
        rewards = List.copyOf(rewards);
    }

    /** One resolved ingredient; equal when type and value are equal, so it keys a {@link TechCost}. */
    public record Ingredient(ResourceLocation type, IngredientType<?, ?> ingredientType, Object value) {

        public Ingredient {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(ingredientType, "ingredientType");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ingredient other && type.equals(other.type) && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return 31 * type.hashCode() + value.hashCode();
        }

        @Override
        public String toString() {
            return type + " " + value;
        }
    }

    /** One resolved lab requirement entry as its api type sees it. */
    public record Requirement(ResourceLocation type, LabRequirementType<?> requirementType, Object value) {
    }

    /** One resolved reward entry. */
    public record Reward(ResourceLocation type, RewardType<?> rewardType, Object value) {
    }
}
