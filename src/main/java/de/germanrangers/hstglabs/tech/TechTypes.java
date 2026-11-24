package de.germanrangers.hstglabs.tech;

import de.germanrangers.hstglabs.api.ingredient.IngredientType;
import de.germanrangers.hstglabs.api.lab.LabRequirementType;
import de.germanrangers.hstglabs.api.reward.RewardType;
import de.germanrangers.hstglabs.api.tech.TechSource;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * The extension registries the tech layer resolves against, passed in so tech/ does not depend on
 * where they live (research/ApiRegistries in the mod, fixtures in tests).
 */
public record TechTypes(Function<ResourceLocation, Optional<IngredientType<?, ?>>> ingredients,
      Function<ResourceLocation, Optional<LabRequirementType<?>>> requirements,
      Function<ResourceLocation, Optional<RewardType<?>>> rewards,
      Collection<TechSource> sources) {
}
