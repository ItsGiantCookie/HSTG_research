package io.github.itsgiantcookie.hstglabs.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One research owner's queue (docs/design.md §4.3): ordered, no duplicates, at most {@code max}
 * entries ({@code queue_max}, default 7). All sites work on the head (D18).
 *
 * <p>The queue only knows ids. Whether a technology may be queued at all (REACHABLE, requirements,
 * permission) is decided by the caller before {@link #add}.
 *
 * <p>Not thread-safe; used on the server thread only.
 *
 * @param <K> technology id
 */
public final class ResearchQueue<K> {

    public static final int DEFAULT_MAX = 7;

    /** Outcome of adding an entry; duplicate is reported before full. */
    public enum AddResult {
        ADDED,
        DUPLICATE,
        FULL
    }

    private final List<K> entries = new ArrayList<>();
    private int max;

    public ResearchQueue() {
        this(DEFAULT_MAX);
    }

    public ResearchQueue(int max) {
        setMax(max);
    }

    /**
     * Rebuilds a saved queue. Order is kept and duplicates are dropped; entries beyond {@code max}
     * are kept, as after {@link #setMax} lowered the limit.
     */
    public static <K> ResearchQueue<K> restore(int max, List<K> saved) {
        var queue = new ResearchQueue<K>(max);
        for (K id : new LinkedHashSet<>(saved)) {
            queue.entries.add(Objects.requireNonNull(id, "id"));
        }
        return queue;
    }

    /** Appends {@code id} at the end. */
    public AddResult add(K id) {
        return insert(entries.size(), id);
    }

    /** Inserts {@code id} at {@code index}, clamped to the queue's ends. */
    public AddResult insert(int index, K id) {
        Objects.requireNonNull(id, "id");
        if (entries.contains(id)) {
            return AddResult.DUPLICATE;
        }
        if (isFull()) {
            return AddResult.FULL;
        }
        entries.add(clamp(index, entries.size()), id);
        return AddResult.ADDED;
    }

    public boolean remove(K id) {
        return entries.remove(id);
    }

    /** Moves {@code id} to {@code index}, clamped to the queue's ends. False if it is not queued. */
    public boolean move(K id, int index) {
        if (!entries.remove(id)) {
            return false;
        }
        entries.add(clamp(index, entries.size()), id);
        return true;
    }

    /** Removes every entry matching {@code filter} (unlocked elsewhere, lost for good) and returns them in queue order. */
    public List<K> removeIf(Predicate<? super K> filter) {
        List<K> removed = new ArrayList<>();
        for (Iterator<K> it = entries.iterator(); it.hasNext(); ) {
            K id = it.next();
            if (filter.test(id)) {
                removed.add(id);
                it.remove();
            }
        }
        return removed;
    }

    public void clear() {
        entries.clear();
    }

    /** Changes the limit. Entries already queued beyond it stay; only new adds are refused. */
    public void setMax(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("queue max must be at least 1, was " + max);
        }
        this.max = max;
    }

    public Optional<K> head() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    /** Immutable snapshot in queue order. */
    public List<K> entries() {
        return List.copyOf(entries);
    }

    public boolean contains(K id) {
        return entries.contains(id);
    }

    public int indexOf(K id) {
        return entries.indexOf(id);
    }

    public int size() {
        return entries.size();
    }

    public int max() {
        return max;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isFull() {
        return entries.size() >= max;
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size));
    }

    @Override
    public String toString() {
        return "ResearchQueue" + entries + "/" + max;
    }
}
