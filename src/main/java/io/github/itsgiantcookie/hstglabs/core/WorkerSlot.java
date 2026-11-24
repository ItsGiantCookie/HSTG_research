package io.github.itsgiantcookie.hstglabs.core;

/**
 * One worker slot of a site (docs/design.md §5.3): a worker with {@code parallel} N gives N slots.
 * Holds the slot's current stats and its {@link LabCycle}. The lab layer updates the stats each tick
 * (tier, speed after modifiers, power). Not thread-safe.
 *
 * @param <K> technology id
 * @param <I> ingredient key
 */
public final class WorkerSlot<K, I> {

    private final LabCycle<K, I> cycle;
    private int tier;
    private double speed;
    private boolean powered = true;

    public WorkerSlot(int tier, double speed) {
        this(tier, speed, new LabCycle<>());
    }

    /** A slot resuming a saved unit. */
    public WorkerSlot(int tier, double speed, LabCycle<K, I> cycle) {
        setTier(tier);
        setSpeed(speed);
        this.cycle = cycle;
    }

    public LabCycle<K, I> cycle() {
        return cycle;
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1, was " + tier);
        }
        this.tier = tier;
    }

    public double speed() {
        return speed;
    }

    /** Speed after every modifier, including {@code global_speed}. */
    public void setSpeed(double speed) {
        if (!(speed > 0.0) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be positive and finite: " + speed);
        }
        this.speed = speed;
    }

    public boolean powered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }
}
