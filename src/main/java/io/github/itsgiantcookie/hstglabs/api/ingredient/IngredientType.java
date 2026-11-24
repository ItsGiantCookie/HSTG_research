package io.github.itsgiantcookie.hstglabs.api.ingredient;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.ApiStatus;

/**
 * One kind of science-pack ingredient (docs/design.md §8.2, §9.2), referenced from technology files
 * as {@code "type": "<id>"}; registered through {@link RegisterIngredientTypesEvent}. Built in (WP-07):
 * {@code hstg_labs:item}, {@code hstg_labs:tag}, {@code hstg_labs:pack}, {@code hstg_labs:chemical}.
 *
 * @param <T> the parsed ingredient, e.g. a tag key
 * @param <S> the stack it matches, see {@link #storage()}
 */
@ApiStatus.Experimental
public interface IngredientType<T, S> {

    /** Fields of this type inside a technology's ingredient entry (next to {@code type} and {@code amount}). */
    MapCodec<T> codec();

    /** Network form, for snapshots sent to clients. */
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec();

    /** Which consumer storage holds it. */
    IngredientStorage<S> storage();

    /** Whether {@code stored} counts as this ingredient. */
    boolean matches(T ingredient, S stored);

    /** Name for screens and validation messages. */
    Component describe(T ingredient);
}
