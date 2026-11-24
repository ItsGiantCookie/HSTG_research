package de.germanrangers.hstglabs.api.client;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/**
 * Edits one lab requirement type's entries in the dev UI's requirement picker (docs/design.md §5.6,
 * §9.2). Registered per lab requirement type id through {@link RegisterRequirementEditorsEvent}.
 * Skeleton for WP-13, which adds the editing widgets. Client thread.
 *
 * @param <T> the requirement type's parsed entry
 */
@ApiStatus.Experimental
public interface RequirementEditor<T> {

    /** One-line summary shown in the requirement list. */
    Component summary(T requirement);
}
