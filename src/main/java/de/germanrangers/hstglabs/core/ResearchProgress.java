package de.germanrangers.hstglabs.core;

/**
 * Progress of one technology (docs/design.md §4.2): {@code done} credited units and
 * {@code inFlight} units claimed by workers but not finished.
 *
 * <p>A worker may only claim while {@code done + inFlight < units}, so research never overshoots
 * and no set is consumed for a unit that cannot count. Progress is kept per technology, so a unit
 * claimed for A is credited to A even if the queue head changed meanwhile.
 *
 * <p>Not thread-safe; used on the server thread only.
 */
public final class ResearchProgress {

    private int units;
    private int done;
    private int inFlight;

    public ResearchProgress(int units) {
        this.units = requireUnits(units);
    }

    /**
     * Rebuilds progress from saved counts. Reservations above the cap are kept (they belong to
     * workers in unloaded chunks, §4.2); {@code done} may not exceed {@code units}.
     */
    public static ResearchProgress restore(int units, int done, int inFlight) {
        var progress = new ResearchProgress(units);
        if (done < 0 || done > units) {
            throw new IllegalArgumentException("done must be 0–" + units + ", was " + done);
        }
        if (inFlight < 0) {
            throw new IllegalArgumentException("inFlight must not be negative, was " + inFlight);
        }
        progress.done = done;
        progress.inFlight = inFlight;
        return progress;
    }

    /** Reserves one unit for a worker. False once {@code done + inFlight} reached U. */
    public boolean tryClaim() {
        if (claimable() == 0) {
            return false;
        }
        inFlight++;
        return true;
    }

    /**
     * A claimed unit finished. Returns whether it was credited; it is not when the cost was lowered
     * below what was already done while it ran (D7), so {@code done} never exceeds U.
     */
    public boolean finishUnit() {
        releaseClaim();
        if (done >= units) {
            return false;
        }
        done++;
        return true;
    }

    /** A claimed unit was abandoned (worker removed, admin release): nothing is credited. */
    public void cancelUnit() {
        releaseClaim();
    }

    /**
     * The cost changed on reload (D7): done units are kept, and the technology is complete at once if
     * they already reach the new U.
     */
    public void setUnits(int newUnits) {
        units = requireUnits(newUnits);
        done = Math.min(done, units);
    }

    public int units() {
        return units;
    }

    public int done() {
        return done;
    }

    public int inFlight() {
        return inFlight;
    }

    /** Units that may still be claimed. */
    public int claimable() {
        return Math.max(0, units - done - inFlight);
    }

    public boolean isComplete() {
        return done >= units;
    }

    /** done / U, 0.0 – 1.0, for progress bars. */
    public double fraction() {
        return (double) done / units;
    }

    private void releaseClaim() {
        if (inFlight == 0) {
            throw new IllegalStateException("no unit in flight");
        }
        inFlight--;
    }

    private static int requireUnits(int units) {
        if (units < 1) {
            throw new IllegalArgumentException("units must be at least 1, was " + units);
        }
        return units;
    }

    @Override
    public String toString() {
        return "ResearchProgress[" + done + "+" + inFlight + "/" + units + "]";
    }
}
