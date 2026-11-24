package io.github.itsgiantcookie.hstglabs.api.event;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

/** Research network topology on the game bus (docs/design.md §5.3, §9.4). Server thread. */
@ApiStatus.Experimental
public abstract class GridEvent extends Event {

    private final ServerLevel level;
    private final BlockPos anchor;

    protected GridEvent(ServerLevel level, BlockPos anchor) {
        this.level = Objects.requireNonNull(level, "level");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public ServerLevel level() {
        return level;
    }

    /** A member of the network: its core if it has one, else the node that changed. */
    public BlockPos anchor() {
        return anchor;
    }

    /** A network formed. */
    @ApiStatus.Experimental
    public static final class Formed extends GridEvent {

        @ApiStatus.Internal
        public Formed(ServerLevel level, BlockPos anchor) {
            super(level, anchor);
        }
    }

    /** Members joined or left. */
    @ApiStatus.Experimental
    public static final class Changed extends GridEvent {

        @ApiStatus.Internal
        public Changed(ServerLevel level, BlockPos anchor) {
            super(level, anchor);
        }
    }

    /** A fault appeared, e.g. {@code hstg_labs:controller_conflict}. */
    @ApiStatus.Experimental
    public static final class Fault extends GridEvent {

        private final ResourceLocation fault;

        @ApiStatus.Internal
        public Fault(ServerLevel level, BlockPos anchor, ResourceLocation fault) {
            super(level, anchor);
            this.fault = Objects.requireNonNull(fault, "fault");
        }

        public ResourceLocation fault() {
            return fault;
        }
    }
}
