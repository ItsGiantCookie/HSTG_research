package io.github.itsgiantcookie.hstglabs.api.template;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.ApiStatus;

/**
 * A kind of thing a template can stamp out (docs/design.md §8.6, §9.2): technology, pack, lab type,
 * or an addon's own type. Registered through {@link RegisterTemplateKindsEvent}. Skeleton for WP-07a,
 * which adds the typed param schema and the resolver.
 *
 * @param <T> the definition a resolved template produces
 */
@ApiStatus.Experimental
public interface TemplateKind<T> {

    /** Codec of the definition a template of this kind resolves to. */
    MapCodec<T> targetCodec();

    /** Hint for the dev UI, e.g. which editor to open for instances. */
    String editorHint();
}
