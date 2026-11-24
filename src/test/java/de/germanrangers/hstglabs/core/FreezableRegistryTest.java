package de.germanrangers.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** docs/design.md §9.2: id-keyed extension registries, frozen after their Register…Event. */
class FreezableRegistryTest {

    @Test
    void registersInOrderAndLooksUp() {
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.register("b", 2);
        registry.register("a", 1);
        assertEquals(List.of("b", "a"), List.copyOf(registry.keys()), "registration order is kept");
        assertEquals(List.of(2, 1), List.copyOf(registry.values()));
        assertEquals(Optional.of(1), registry.get("a"));
        assertEquals(Optional.empty(), registry.get("z"));
        assertTrue(registry.contains("a"));
        assertFalse(registry.contains("z"));
        assertEquals(2, registry.size());
        assertEquals("numbers", registry.name());
        assertEquals(Map.of("a", 1, "b", 2), registry.asMap());
    }

    @Test
    void refusesDuplicatesAndNulls() {
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.register("a", 1);
        var duplicate = assertThrows(IllegalArgumentException.class, () -> registry.register("a", 2));
        assertTrue(duplicate.getMessage().contains("a"));
        assertThrows(NullPointerException.class, () -> registry.register(null, 1));
        assertThrows(NullPointerException.class, () -> registry.register("b", null));
    }

    @Test
    void frozenRefusesRegistration() {
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.register("a", 1);
        assertFalse(registry.isFrozen());
        registry.freeze();
        assertTrue(registry.isFrozen());
        var frozen = assertThrows(IllegalStateException.class, () -> registry.register("b", 2));
        assertTrue(frozen.getMessage().contains("numbers"), "names the registry");
        assertEquals(Optional.of(1), registry.get("a"), "lookups still work");
    }

    @Test
    void frozenIsCheckedBeforeArguments() {
        // so a late registration always reports the real problem, even with bad arguments
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(null, null));
    }

    @Test
    void viewsAreReadOnly() {
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.register("a", 1);
        assertThrows(UnsupportedOperationException.class, () -> registry.keys().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> registry.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.asMap().put("x", 9));
    }

    @Test
    void freezingTwiceIsHarmless() {
        var registry = new FreezableRegistry<String, Integer>("numbers");
        registry.freeze();
        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(NullPointerException.class, () -> new FreezableRegistry<String, Integer>(null));
    }
}
