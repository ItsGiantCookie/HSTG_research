package io.github.itsgiantcookie.hstglabs.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An id-keyed extension registry (docs/design.md §9.2, §9.6): filled during its Register…Event,
 * frozen right after, read-only from then on. Keeps registration order. Not thread-safe; filled on
 * the main thread during mod loading.
 *
 * @param <K> id type
 * @param <V> registered value
 */
public final class FreezableRegistry<K, V> {

    private final String name;
    private final Map<K, V> entries = new LinkedHashMap<>();
    private boolean frozen;

    public FreezableRegistry(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Adds {@code value} under {@code key}; refused once frozen and for a key registered twice. */
    public void register(K key, V value) {
        if (frozen) {
            throw new IllegalStateException("registry '" + name + "' is frozen; register inside its Register…Event listener");
        }
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (entries.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("registry '" + name + "' already has " + key);
        }
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    /** Keys in registration order, read-only. */
    public Set<K> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** Values in registration order, read-only. */
    public Collection<V> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Map<K, V> asMap() {
        return Collections.unmodifiableMap(entries);
    }

    public int size() {
        return entries.size();
    }

    public String name() {
        return name;
    }
}
