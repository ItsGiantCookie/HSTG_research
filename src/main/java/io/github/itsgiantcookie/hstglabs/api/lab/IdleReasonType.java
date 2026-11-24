package io.github.itsgiantcookie.hstglabs.api.lab;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Why a worker slot is idle, as screens and Jade show it (docs/design.md §4.4, §5.7, §9.2);
 * registered through {@link RegisterIdleReasonsEvent}. Built in: {@code hstg_labs:no_research},
 * {@code blocked}, {@code lab_tier}, {@code lab_requirements}, {@code no_power}, {@code cap_reached},
 * {@code missing_ingredients}.
 *
 * @param translationKey lang key; may take one argument, the reason's detail (the tier needed, the
 *                       missing requirement or ingredient)
 * @param icon           texture, e.g. {@code hstg_labs:textures/gui/idle/no_power.png}
 */
@ApiStatus.Experimental
public record IdleReasonType(String translationKey, ResourceLocation icon) {

    public IdleReasonType {
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(icon, "icon");
    }
}
