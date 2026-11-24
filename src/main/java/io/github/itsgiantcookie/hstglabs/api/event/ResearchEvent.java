package io.github.itsgiantcookie.hstglabs.api.event;

import io.github.itsgiantcookie.hstglabs.api.lab.ResearchSite;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Research lifecycle on the game bus (docs/design.md §9.4). Server thread. KubeJS events are
 * forwarded from these (§11).
 */
@ApiStatus.Experimental
public abstract class ResearchEvent extends Event {

    private final OwnerId owner;
    private final ResourceLocation technology;

    protected ResearchEvent(OwnerId owner, ResourceLocation technology) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.technology = Objects.requireNonNull(technology, "technology");
    }

    public OwnerId owner() {
        return owner;
    }

    public ResourceLocation technology() {
        return technology;
    }

    /** Cancellable: a player, script or API call is about to add {@link #technology()} to the queue. */
    @ApiStatus.Experimental
    public static final class BeforeEnqueue extends ResearchEvent implements ICancellableEvent {

        @ApiStatus.Internal
        public BeforeEnqueue(OwnerId owner, ResourceLocation technology) {
            super(owner, technology);
        }
    }

    /** Any queue edit; {@link #technology()} is the entry that changed. */
    @ApiStatus.Experimental
    public static final class QueueChanged extends ResearchEvent {

        @ApiStatus.Internal
        public QueueChanged(OwnerId owner, ResourceLocation technology) {
            super(owner, technology);
        }
    }

    /** The first unit of a technology was claimed. */
    @ApiStatus.Experimental
    public static final class Started extends ResearchEvent {

        @ApiStatus.Internal
        public Started(OwnerId owner, ResourceLocation technology) {
            super(owner, technology);
        }
    }

    /** Cancellable: a worker is about to consume a set (gate by dimension, weather, power). */
    @ApiStatus.Experimental
    public static final class BeforeUnitClaim extends ResearchEvent implements ICancellableEvent {

        private final ResearchSite site;

        @ApiStatus.Internal
        public BeforeUnitClaim(OwnerId owner, ResourceLocation technology, ResearchSite site) {
            super(owner, technology);
            this.site = Objects.requireNonNull(site, "site");
        }

        public ResearchSite site() {
            return site;
        }
    }

    /** A unit was credited. */
    @ApiStatus.Experimental
    public static final class UnitCompleted extends ResearchEvent {

        private final int done;
        private final int units;

        @ApiStatus.Internal
        public UnitCompleted(OwnerId owner, ResourceLocation technology, int done, int units) {
            super(owner, technology);
            this.done = done;
            this.units = units;
        }

        /** Units done after this one. */
        public int done() {
            return done;
        }

        /** U. */
        public int units() {
            return units;
        }
    }

    /** Rewards applied and HS confirmed the unlock. */
    @ApiStatus.Experimental
    public static final class Completed extends ResearchEvent {

        @ApiStatus.Internal
        public Completed(OwnerId owner, ResourceLocation technology) {
            super(owner, technology);
        }
    }

    /** HS refused the unlock; progress is kept and the technology is BLOCKED (§4.1). */
    @ApiStatus.Experimental
    public static final class Blocked extends ResearchEvent {

        @ApiStatus.Internal
        public Blocked(OwnerId owner, ResourceLocation technology) {
            super(owner, technology);
        }
    }
}
