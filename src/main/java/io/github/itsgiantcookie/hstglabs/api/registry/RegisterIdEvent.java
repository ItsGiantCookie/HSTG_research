package io.github.itsgiantcookie.hstglabs.api.registry;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Base of every mod-bus {@code Register…Event} (docs/design.md §9.2): add entries to one id-keyed
 * registry. Fired once during common setup (client setup for {@code api.client} events); the registry
 * freezes right after, and any later {@link #register} throws {@link IllegalStateException}.
 *
 * <p>Listen on your mod bus: {@code modBus.addListener(RegisterRewardTypesEvent.class, e -> e.register(id, type))}.
 *
 * @param <T> what this registry holds
 */
@ApiStatus.Experimental
public abstract class RegisterIdEvent<T> extends Event implements IModBusEvent {

    private final Registrar<T> registrar;

    /** Created by HSTG Research Labs only. */
    @ApiStatus.Internal
    protected RegisterIdEvent(Registrar<T> registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /**
     * Registers {@code value} under {@code id}. Use your own namespace.
     *
     * @throws IllegalArgumentException if {@code id} is already taken
     * @throws IllegalStateException    if the registry is already frozen
     */
    public void register(ResourceLocation id, T value) {
        registrar.register(id, value);
    }

    /** Where registrations go; supplied by the mod when it fires the event. */
    @ApiStatus.Internal
    @FunctionalInterface
    public interface Registrar<T> {

        void register(ResourceLocation id, T value);
    }
}
