package io.github.itsgiantcookie.hstglabs.core;

import io.github.itsgiantcookie.hstglabs.core.ResearchQueue.AddResult;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One research owner's state (docs/design.md §4.1, §6.2): the queue, progress per technology, the
 * technologies waiting to be unlocked, and the ones History Stages refused (BLOCKED).
 *
 * <ul>
 *   <li>A technology becomes <b>pending</b> when its last unit is credited, when a cost cut makes
 *       {@code done >= U} (D7), or when a complete technology is queued again.</li>
 *   <li>Our unlock succeeded: it leaves queue and pending; its completed progress stays, so a relock
 *       with {@code on_relock = KEEP} (D5) can re-unlock it without paying again.</li>
 *   <li>Unlocked elsewhere (command, quest, script): it leaves the queue and its progress is
 *       discarded (§4.1).</li>
 *   <li>Unlock refused: BLOCKED, it leaves the queue and pending, progress kept; re-adding it retries
 *       (D32).</li>
 * </ul>
 *
 * <p>The stage itself is never stored here; whether it is unlocked is History Stages' answer. Not
 * thread-safe; server thread only.
 *
 * @param <K> technology id
 */
public final class ResearchBook<K> {

    private final ResearchQueue<K> queue;
    private final Map<K, ResearchProgress> progress = new LinkedHashMap<>();
    private final Set<K> pending = new LinkedHashSet<>();
    private final Set<K> blocked = new LinkedHashSet<>();

    public ResearchBook(int queueMax) {
        this.queue = new ResearchQueue<>(queueMax);
    }

    private ResearchBook(ResearchQueue<K> queue) {
        this.queue = queue;
    }

    /** Rebuilds saved state. */
    public static <K> ResearchBook<K> restore(int queueMax, List<K> queue, Map<K, ResearchProgress> progress, Collection<K> blocked,
          Collection<K> pending) {
        var book = new ResearchBook<K>(ResearchQueue.restore(queueMax, queue));
        book.progress.putAll(progress);
        book.blocked.addAll(blocked);
        book.pending.addAll(pending);
        return book;
    }

    public ResearchQueue<K> queue() {
        return queue;
    }

    public void setQueueMax(int max) {
        queue.setMax(max);
    }

    /** Adds to the queue (§4.3 checks are the caller's). Re-adding a BLOCKED technology retries it (D32). */
    public AddResult enqueue(K tech) {
        AddResult result = queue.add(tech);
        if (result == AddResult.ADDED) {
            blocked.remove(tech);
            if (isComplete(tech)) {
                pending.add(tech);
            }
        }
        return result;
    }

    public Optional<ResearchProgress> progress(K tech) {
        return Optional.ofNullable(progress.get(tech));
    }

    /**
     * Progress of {@code tech} for a cost of {@code units}, created if missing. A changed cost keeps
     * the done units (D7); a queued technology that is now complete becomes pending.
     */
    public ResearchProgress progressFor(K tech, int units) {
        ResearchProgress p = progress.get(tech);
        if (p == null) {
            p = new ResearchProgress(units);
            progress.put(tech, p);
        } else if (p.units() != units) {
            p.setUnits(units);
            if (p.isComplete() && queue.contains(tech)) {
                pending.add(tech);
            }
        }
        return p;
    }

    /** Credits up to {@code count} units directly (API, admin); returns how many counted. */
    public int addUnits(K tech, int units, int count) {
        int added = progressFor(tech, units).credit(count);
        unitCredited(tech);
        return added;
    }

    /** A lab finished a unit of {@code tech} (its progress already counts it). */
    public void unitCredited(K tech) {
        ResearchProgress p = progress.get(tech);
        if (p != null && p.isComplete()) {
            pending.add(tech);
        }
    }

    public boolean isComplete(K tech) {
        ResearchProgress p = progress.get(tech);
        return p != null && p.isComplete();
    }

    /** Technologies waiting for their unlock, in the order they became ready. */
    public List<K> readyToUnlock() {
        return List.copyOf(pending);
    }

    /** Same as {@link #readyToUnlock()}, for saving. */
    public List<K> pending() {
        return readyToUnlock();
    }

    /** Our unlock went through. */
    public void unlocked(K tech) {
        queue.remove(tech);
        pending.remove(tech);
        blocked.remove(tech);
    }

    /** Something else unlocked the stage: drop it and discard its progress (§4.1). */
    public void unlockedElsewhere(K tech) {
        unlocked(tech);
        progress.remove(tech);
    }

    /** History Stages refused the unlock (D32): out of the queue, progress kept, retried when re-added. */
    public void blocked(K tech) {
        queue.remove(tech);
        pending.remove(tech);
        blocked.add(tech);
    }

    public boolean isBlocked(K tech) {
        return blocked.contains(tech);
    }

    public Set<K> blockedTechnologies() {
        return Collections.unmodifiableSet(blocked);
    }

    /** The stage was relocked (D5): {@code reset} discards the progress, otherwise it is kept. */
    public void relocked(K tech, boolean reset) {
        pending.remove(tech);
        if (reset) {
            progress.remove(tech);
        }
    }

    /** The stage is lost for good: not queueable until an admin unlock; progress kept. */
    public void lost(K tech) {
        queue.remove(tech);
        pending.remove(tech);
    }

    public Map<K, ResearchProgress> progressEntries() {
        return Collections.unmodifiableMap(progress);
    }
}
