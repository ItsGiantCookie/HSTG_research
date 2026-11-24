package io.github.itsgiantcookie.hstglabs.tech;

import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import io.github.itsgiantcookie.hstglabs.api.lab.LabRequirementType;
import io.github.itsgiantcookie.hstglabs.api.reward.RewardType;
import io.github.itsgiantcookie.hstglabs.api.tech.TechSource;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The extension registries the tech layer resolves against, passed in so tech/ does not depend on
 * where they live (research/ApiRegistries in the mod, fixtures in tests).
 *
 * @param templateKinds whether a template kind id is registered (docs/design.md §8.6)
 */
public record TechTypes(Function<ResourceLocation, Optional<IngredientType<?, ?>>> ingredients,
      Function<ResourceLocation, Optional<LabRequirementType<?>>> requirements,
      Function<ResourceLocation, Optional<RewardType<?>>> rewards,
      Collection<TechSource> sources,
      Predicate<String> templateKinds) {
}
