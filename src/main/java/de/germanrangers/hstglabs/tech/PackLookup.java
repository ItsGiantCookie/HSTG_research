package de.germanrangers.hstglabs.tech;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * Loaded pack definitions by id (docs/design.md §8.2). The server side holds what the repository
 * loaded; the client side holds what the server last synced. Both live in one JVM in single player,
 * so lookups go to the side of the calling thread.
 */
public final class PackLookup {

    private static volatile Map<ResourceLocation, PackDefinition> server = Map.of();
    private static volatile Map<ResourceLocation, PackDefinition> client = Map.of();

    private PackLookup() {
    }

    public static Optional<PackDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(current().get(id));
    }

    /** Every pack of the calling side, sorted by tier then id. */
    public static List<PackDefinition> all() {
        return sorted(current().values());
    }

    public static void setServer(Map<ResourceLocation, PackDefinition> packs) {
        server = Map.copyOf(packs);
    }

    public static void setClient(Map<ResourceLocation, PackDefinition> packs) {
        client = Map.copyOf(packs);
    }

    private static Map<ResourceLocation, PackDefinition> current() {
        return EffectiveSide.get().isClient() ? client : server;
    }

    private static List<PackDefinition> sorted(Collection<PackDefinition> packs) {
        return packs.stream().sorted((a, b) -> a.tier() != b.tier() ? Integer.compare(a.tier(), b.tier()) : a.id().compareTo(b.id())).toList();
    }
}
