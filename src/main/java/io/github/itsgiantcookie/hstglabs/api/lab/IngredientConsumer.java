package io.github.itsgiantcookie.hstglabs.api.lab;

import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientStorage;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link LabRole#CONSUMER} role (docs/design.md §5.1, §5.3, §9.3), exposed as
 * {@link ResearchCapabilities#CONSUMER}: holds ingredients for a site and gives up one set per unit.
 * The controller simulates every line first, then takes them for real (D20).
 */
@ApiStatus.Experimental
public interface IngredientConsumer {

    /** Whether this consumer may hold {@code ingredient} at all (its lab type's accepted types and tags). */
    <T, S> boolean accepts(IngredientType<T, S> type, T ingredient);

    /** How much matching {@code ingredient} it holds right now. */
    <T, S> long available(IngredientType<T, S> type, T ingredient);

    /**
     * Takes up to {@code amount} matching {@code ingredient} and returns the exact stacks taken, so a
     * cancelled unit can give the same stacks back. With {@code simulate} nothing changes.
     */
    <T, S> List<S> take(IngredientType<T, S> type, T ingredient, long amount, boolean simulate);

    /** Gives a previously taken stack back; returns what did not fit (empty if all fit). */
    <S> S giveBack(IngredientStorage<S> storage, S stack);
}
