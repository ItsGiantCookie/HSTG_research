package io.github.itsgiantcookie.hstglabs.core;

import java.util.HashMap;
import java.util.Map;

/** In-memory consumer for core tests: a stock per ingredient key, an optional capacity per key. */
final class FakeSource implements PackSource<String> {

    private final String id;
    private final Map<String, Long> stock = new HashMap<>();
    private long capacity = Long.MAX_VALUE;

    FakeSource(String id) {
        this.id = id;
    }

    FakeSource with(String what, long amount) {
        stock.merge(what, amount, Long::sum);
        return this;
    }

    FakeSource capacity(long capacity) {
        this.capacity = capacity;
        return this;
    }

    long count(String what) {
        return stock.getOrDefault(what, 0L);
    }

    long total() {
        return stock.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public long available(String what) {
        return count(what);
    }

    @Override
    public long take(String what, long amount) {
        long taken = Math.min(amount, count(what));
        stock.merge(what, -taken, Long::sum);
        return taken;
    }

    @Override
    public long giveBack(String what, long amount) {
        long accepted = Math.max(0, Math.min(capacity - count(what), amount));
        stock.merge(what, accepted, Long::sum);
        return accepted;
    }
}
